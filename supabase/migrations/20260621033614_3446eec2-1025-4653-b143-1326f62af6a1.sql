
-- 1. profiles: XP + streak
ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS lifetime_xp INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS current_focus_streak INTEGER NOT NULL DEFAULT 0;

-- 2. breaks: severity
DO $$ BEGIN
  CREATE TYPE public.breach_severity AS ENUM ('minor', 'severe');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

ALTER TABLE public.breaks
  ADD COLUMN IF NOT EXISTS severity public.breach_severity NOT NULL DEFAULT 'severe';

-- 3. focus_history
CREATE TABLE IF NOT EXISTS public.focus_history (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  profile_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  room_id UUID REFERENCES public.rooms(id) ON DELETE SET NULL,
  score INTEGER NOT NULL CHECK (score >= 0 AND score <= 100),
  xp_earned INTEGER NOT NULL DEFAULT 0,
  duration_seconds INTEGER NOT NULL DEFAULT 0,
  breaches_count INTEGER NOT NULL DEFAULT 0,
  tier TEXT NOT NULL DEFAULT 'compromised',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
GRANT SELECT, INSERT ON public.focus_history TO authenticated;
GRANT ALL ON public.focus_history TO service_role;
ALTER TABLE public.focus_history ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users read own focus history" ON public.focus_history
  FOR SELECT TO authenticated USING (auth.uid() = profile_id);
CREATE POLICY "Users insert own focus history" ON public.focus_history
  FOR INSERT TO authenticated WITH CHECK (auth.uid() = profile_id);
CREATE INDEX IF NOT EXISTS focus_history_profile_idx ON public.focus_history(profile_id, created_at DESC);

-- 4. focus_groups
CREATE TABLE IF NOT EXISTS public.focus_groups (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL CHECK (length(name) BETWEEN 1 AND 80),
  created_by UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  total_group_xp INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
GRANT SELECT, INSERT, UPDATE, DELETE ON public.focus_groups TO authenticated;
GRANT ALL ON public.focus_groups TO service_role;
ALTER TABLE public.focus_groups ENABLE ROW LEVEL SECURITY;
CREATE INDEX IF NOT EXISTS focus_groups_creator_idx ON public.focus_groups(created_by);

-- 5. group_members
CREATE TABLE IF NOT EXISTS public.group_members (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id UUID NOT NULL REFERENCES public.focus_groups(id) ON DELETE CASCADE,
  profile_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (group_id, profile_id)
);
GRANT SELECT, INSERT, DELETE ON public.group_members TO authenticated;
GRANT ALL ON public.group_members TO service_role;
ALTER TABLE public.group_members ENABLE ROW LEVEL SECURITY;
CREATE INDEX IF NOT EXISTS group_members_group_idx ON public.group_members(group_id);
CREATE INDEX IF NOT EXISTS group_members_profile_idx ON public.group_members(profile_id);

-- Security-definer helper: is the caller a member of this group?
CREATE OR REPLACE FUNCTION public.is_group_member(_group_id UUID, _user_id UUID)
RETURNS BOOLEAN
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.group_members
    WHERE group_id = _group_id AND profile_id = _user_id
  );
$$;

-- focus_groups policies (use helper to avoid recursive RLS via group_members)
CREATE POLICY "Members read their groups" ON public.focus_groups
  FOR SELECT TO authenticated
  USING (auth.uid() = created_by OR public.is_group_member(id, auth.uid()));
CREATE POLICY "Authenticated create groups" ON public.focus_groups
  FOR INSERT TO authenticated WITH CHECK (auth.uid() = created_by);
CREATE POLICY "Creator updates group" ON public.focus_groups
  FOR UPDATE TO authenticated
  USING (auth.uid() = created_by) WITH CHECK (auth.uid() = created_by);
CREATE POLICY "Creator deletes group" ON public.focus_groups
  FOR DELETE TO authenticated USING (auth.uid() = created_by);

-- group_members policies
CREATE POLICY "Member reads own + group rows" ON public.group_members
  FOR SELECT TO authenticated
  USING (
    auth.uid() = profile_id
    OR public.is_group_member(group_id, auth.uid())
    OR EXISTS (SELECT 1 FROM public.focus_groups g WHERE g.id = group_id AND g.created_by = auth.uid())
  );
CREATE POLICY "Self join group" ON public.group_members
  FOR INSERT TO authenticated WITH CHECK (auth.uid() = profile_id);
CREATE POLICY "Self leave or creator removes" ON public.group_members
  FOR DELETE TO authenticated
  USING (
    auth.uid() = profile_id
    OR EXISTS (SELECT 1 FROM public.focus_groups g WHERE g.id = group_id AND g.created_by = auth.uid())
  );

-- updated_at trigger for focus_groups
DROP TRIGGER IF EXISTS update_focus_groups_updated_at ON public.focus_groups;
CREATE TRIGGER update_focus_groups_updated_at
  BEFORE UPDATE ON public.focus_groups
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- 6. Atomic breach helper
CREATE OR REPLACE FUNCTION public.record_breach(
  _room_id UUID,
  _participant_id UUID,
  _reason TEXT,
  _severity public.breach_severity,
  _integrity INTEGER
)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  _uid UUID := auth.uid();
  _name TEXT;
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;

  SELECT display_name INTO _name FROM public.participants
   WHERE id = _participant_id AND user_id = _uid AND room_id = _room_id;
  IF _name IS NULL THEN RAISE EXCEPTION 'not_participant'; END IF;

  INSERT INTO public.breaks (room_id, user_id, display_name, reason, severity)
  VALUES (_room_id, _uid, _name, _reason, _severity);

  -- Severe = mark them breached. Minor = log only.
  IF _severity = 'severe' THEN
    UPDATE public.participants
       SET breached = TRUE,
           breach_reason = _reason,
           breach_at = now(),
           integrity = LEAST(GREATEST(_integrity, 0), 100)
     WHERE id = _participant_id AND user_id = _uid;
  END IF;
END;
$$;
GRANT EXECUTE ON FUNCTION public.record_breach(UUID, UUID, TEXT, public.breach_severity, INTEGER) TO authenticated;

-- 7. Session finalization helper (atomic: history + XP + streak + group XP)
CREATE OR REPLACE FUNCTION public.finalize_focus_session(
  _room_id UUID,
  _score INTEGER,
  _xp INTEGER,
  _duration_seconds INTEGER,
  _breaches_count INTEGER,
  _tier TEXT
)
RETURNS UUID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  _uid UUID := auth.uid();
  _history_id UUID;
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  IF _score < 0 OR _score > 100 THEN RAISE EXCEPTION 'bad_score'; END IF;

  -- Idempotency: one history row per (profile, room).
  SELECT id INTO _history_id FROM public.focus_history
   WHERE profile_id = _uid AND room_id = _room_id;
  IF _history_id IS NOT NULL THEN RETURN _history_id; END IF;

  INSERT INTO public.focus_history (profile_id, room_id, score, xp_earned, duration_seconds, breaches_count, tier)
  VALUES (_uid, _room_id, _score, GREATEST(_xp, 0), GREATEST(_duration_seconds, 0), GREATEST(_breaches_count, 0), _tier)
  RETURNING id INTO _history_id;

  UPDATE public.profiles
     SET lifetime_xp = lifetime_xp + GREATEST(_xp, 0),
         current_focus_streak = CASE WHEN _breaches_count = 0 AND _score >= 70
                                     THEN current_focus_streak + 1
                                     ELSE 0 END,
         updated_at = now()
   WHERE id = _uid;

  -- Roll up XP to every group this user belongs to.
  UPDATE public.focus_groups g
     SET total_group_xp = total_group_xp + GREATEST(_xp, 0),
         updated_at = now()
   WHERE g.id IN (SELECT group_id FROM public.group_members WHERE profile_id = _uid);

  RETURN _history_id;
END;
$$;
GRANT EXECUTE ON FUNCTION public.finalize_focus_session(UUID, INTEGER, INTEGER, INTEGER, INTEGER, TEXT) TO authenticated;

-- 8. Realtime
ALTER PUBLICATION supabase_realtime ADD TABLE public.focus_history;
ALTER PUBLICATION supabase_realtime ADD TABLE public.group_members;
ALTER PUBLICATION supabase_realtime ADD TABLE public.focus_groups;
