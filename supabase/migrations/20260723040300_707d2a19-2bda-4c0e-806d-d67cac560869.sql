
-- ============ PROFILES: identity extensions ============
ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS banner_url TEXT,
  ADD COLUMN IF NOT EXISTS banner_gradient TEXT,
  ADD COLUMN IF NOT EXISTS title TEXT,
  ADD COLUMN IF NOT EXISTS prestige_level INT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS productivity_dna TEXT,
  ADD COLUMN IF NOT EXISTS pinned_showcase JSONB NOT NULL DEFAULT '[]'::jsonb;

-- ============ ROOMS: rich room fields ============
ALTER TABLE public.rooms
  ADD COLUMN IF NOT EXISTS banner_url TEXT,
  ADD COLUMN IF NOT EXISTS shared_goal_hours INT,
  ADD COLUMN IF NOT EXISTS collective_seconds BIGINT NOT NULL DEFAULT 0;

-- ============ room_milestones ============
CREATE TABLE IF NOT EXISTS public.room_milestones (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  room_id UUID NOT NULL REFERENCES public.rooms(id) ON DELETE CASCADE,
  kind TEXT NOT NULL,
  label TEXT NOT NULL,
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  reached_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
GRANT SELECT ON public.room_milestones TO authenticated;
GRANT ALL ON public.room_milestones TO service_role;
ALTER TABLE public.room_milestones ENABLE ROW LEVEL SECURITY;
CREATE POLICY "milestones visible to participants" ON public.room_milestones
  FOR SELECT TO authenticated
  USING (public.is_room_participant(room_id, auth.uid()) OR public.is_room_host(room_id, auth.uid()));

-- ============ room_scheduled_events ============
CREATE TABLE IF NOT EXISTS public.room_scheduled_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  room_id UUID NOT NULL REFERENCES public.rooms(id) ON DELETE CASCADE,
  created_by UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  description TEXT,
  starts_at TIMESTAMPTZ NOT NULL,
  duration_minutes INT NOT NULL DEFAULT 60,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
GRANT SELECT, INSERT, UPDATE, DELETE ON public.room_scheduled_events TO authenticated;
GRANT ALL ON public.room_scheduled_events TO service_role;
ALTER TABLE public.room_scheduled_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY "events viewable by participants" ON public.room_scheduled_events
  FOR SELECT TO authenticated
  USING (public.is_room_participant(room_id, auth.uid()) OR public.is_room_host(room_id, auth.uid()));
CREATE POLICY "events writable by moderators" ON public.room_scheduled_events
  FOR ALL TO authenticated
  USING (public.is_room_moderator(room_id, auth.uid()))
  WITH CHECK (public.is_room_moderator(room_id, auth.uid()) AND created_by = auth.uid());

-- ============ memory_vault_items ============
CREATE TABLE IF NOT EXISTS public.memory_vault_items (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  history_id UUID REFERENCES public.focus_history(id) ON DELETE SET NULL,
  title TEXT NOT NULL,
  body TEXT,
  url TEXT,
  tags TEXT[] NOT NULL DEFAULT '{}',
  ai_summary TEXT,
  attachments JSONB NOT NULL DEFAULT '[]'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
GRANT SELECT, INSERT, UPDATE, DELETE ON public.memory_vault_items TO authenticated;
GRANT ALL ON public.memory_vault_items TO service_role;
ALTER TABLE public.memory_vault_items ENABLE ROW LEVEL SECURITY;
CREATE POLICY "vault owner only" ON public.memory_vault_items
  FOR ALL TO authenticated
  USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE INDEX IF NOT EXISTS memory_vault_user_idx ON public.memory_vault_items(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS memory_vault_tags_idx ON public.memory_vault_items USING GIN(tags);

CREATE TRIGGER memory_vault_updated_at
  BEFORE UPDATE ON public.memory_vault_items
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- ============ titles ============
CREATE TABLE IF NOT EXISTS public.titles (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  description TEXT NOT NULL,
  icon TEXT,
  criteria JSONB NOT NULL DEFAULT '{}'::jsonb
);
GRANT SELECT ON public.titles TO authenticated, anon;
GRANT ALL ON public.titles TO service_role;
ALTER TABLE public.titles ENABLE ROW LEVEL SECURITY;
CREATE POLICY "titles public read" ON public.titles FOR SELECT TO authenticated, anon USING (true);

INSERT INTO public.titles(id, name, description) VALUES
  ('night_owl', 'Night Owl', 'Sessions past midnight'),
  ('deep_thinker', 'Deep Thinker', '10+ flow sessions'),
  ('legend', 'Legend', '100 total hours'),
  ('focused', 'Focused', '7-day streak'),
  ('mentor', 'Mentor', 'Guided a new user'),
  ('sprinter', 'Sprinter', 'Fastest sprint finisher'),
  ('explorer', 'Explorer', 'Joined 5+ rooms'),
  ('planner', 'Planner', 'Scheduled 3+ events')
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS public.user_titles (
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  title_id TEXT NOT NULL REFERENCES public.titles(id) ON DELETE CASCADE,
  earned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, title_id)
);
GRANT SELECT, INSERT, DELETE ON public.user_titles TO authenticated;
GRANT ALL ON public.user_titles TO service_role;
ALTER TABLE public.user_titles ENABLE ROW LEVEL SECURITY;
CREATE POLICY "user_titles public read" ON public.user_titles FOR SELECT TO authenticated USING (true);
CREATE POLICY "user_titles owner write" ON public.user_titles
  FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY "user_titles owner delete" ON public.user_titles
  FOR DELETE TO authenticated USING (auth.uid() = user_id);

-- ============ seasons ============
CREATE TABLE IF NOT EXISTS public.seasons (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  description TEXT,
  starts_at TIMESTAMPTZ NOT NULL,
  ends_at TIMESTAMPTZ NOT NULL,
  reward_title_id TEXT REFERENCES public.titles(id) ON DELETE SET NULL,
  xp_multiplier NUMERIC NOT NULL DEFAULT 1.0
);
GRANT SELECT ON public.seasons TO authenticated, anon;
GRANT ALL ON public.seasons TO service_role;
ALTER TABLE public.seasons ENABLE ROW LEVEL SECURITY;
CREATE POLICY "seasons public read" ON public.seasons FOR SELECT TO authenticated, anon USING (true);

CREATE TABLE IF NOT EXISTS public.season_participants (
  season_id UUID NOT NULL REFERENCES public.seasons(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  xp INT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (season_id, user_id)
);
GRANT SELECT, INSERT, UPDATE ON public.season_participants TO authenticated;
GRANT ALL ON public.season_participants TO service_role;
ALTER TABLE public.season_participants ENABLE ROW LEVEL SECURITY;
CREATE POLICY "season_participants public read" ON public.season_participants FOR SELECT TO authenticated USING (true);
CREATE POLICY "season_participants owner write" ON public.season_participants
  FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY "season_participants owner update" ON public.season_participants
  FOR UPDATE TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- ============ mentor_relationships ============
CREATE TABLE IF NOT EXISTS public.mentor_relationships (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  mentor_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  mentee_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  status TEXT NOT NULL DEFAULT 'active',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (mentor_id, mentee_id)
);
GRANT SELECT, INSERT, UPDATE, DELETE ON public.mentor_relationships TO authenticated;
GRANT ALL ON public.mentor_relationships TO service_role;
ALTER TABLE public.mentor_relationships ENABLE ROW LEVEL SECURITY;
CREATE POLICY "mentorship visible to both parties" ON public.mentor_relationships
  FOR SELECT TO authenticated USING (auth.uid() IN (mentor_id, mentee_id));
CREATE POLICY "mentorship writable by either party" ON public.mentor_relationships
  FOR ALL TO authenticated
  USING (auth.uid() IN (mentor_id, mentee_id))
  WITH CHECK (auth.uid() IN (mentor_id, mentee_id));

-- ============ webhooks ============
CREATE TABLE IF NOT EXISTS public.webhooks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  url TEXT NOT NULL,
  events TEXT[] NOT NULL DEFAULT '{}',
  secret TEXT,
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
GRANT SELECT, INSERT, UPDATE, DELETE ON public.webhooks TO authenticated;
GRANT ALL ON public.webhooks TO service_role;
ALTER TABLE public.webhooks ENABLE ROW LEVEL SECURITY;
CREATE POLICY "webhooks owner only" ON public.webhooks
  FOR ALL TO authenticated
  USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- ============ helper: increment collective_seconds on session completion ============
CREATE OR REPLACE FUNCTION public.rooms_add_collective_seconds()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NEW.room_id IS NOT NULL THEN
    UPDATE public.rooms
       SET collective_seconds = collective_seconds + COALESCE(NEW.duration_seconds, 0)
     WHERE id = NEW.room_id;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS focus_history_room_totals ON public.focus_history;
CREATE TRIGGER focus_history_room_totals
  AFTER INSERT ON public.focus_history
  FOR EACH ROW EXECUTE FUNCTION public.rooms_add_collective_seconds();
