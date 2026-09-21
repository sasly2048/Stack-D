-- ============================================================
-- Stack'd — consolidated schema for a fresh Supabase project
-- Generated from supabase/migrations/*.sql (50 files, in order)
-- Paste whole file into Supabase Dashboard > SQL Editor > Run.
-- Extensions used (all free-tier): pg_net, pg_cron, supabase_vault, pgmq.
-- ============================================================


-- ============================================================
-- 20260620092909_3dfd6429-5081-476b-b888-ff62df77c40e.sql
-- ============================================================

-- =========================================================
-- PROFILES
-- =========================================================
CREATE TABLE public.profiles (
  id UUID NOT NULL PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  display_name TEXT NOT NULL DEFAULT 'Anonymous',
  avatar_url TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

GRANT SELECT ON public.profiles TO anon;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.profiles TO authenticated;
GRANT ALL ON public.profiles TO service_role;

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Profiles are viewable by everyone"
  ON public.profiles FOR SELECT
  USING (true);

CREATE POLICY "Users can insert their own profile"
  ON public.profiles FOR INSERT
  TO authenticated
  WITH CHECK (auth.uid() = id);

CREATE POLICY "Users can update their own profile"
  ON public.profiles FOR UPDATE
  TO authenticated
  USING (auth.uid() = id)
  WITH CHECK (auth.uid() = id);

-- =========================================================
-- ROOMS
-- =========================================================
CREATE TYPE public.room_status AS ENUM ('lobby', 'active', 'complete', 'aborted');

CREATE TABLE public.rooms (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  code TEXT NOT NULL UNIQUE,
  host_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  target_duration_seconds INTEGER NOT NULL DEFAULT 1800 CHECK (target_duration_seconds BETWEEN 60 AND 28800),
  status public.room_status NOT NULL DEFAULT 'lobby',
  started_at TIMESTAMPTZ,
  ended_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_rooms_code ON public.rooms(code);
CREATE INDEX idx_rooms_host ON public.rooms(host_id);
CREATE INDEX idx_rooms_status ON public.rooms(status);

GRANT SELECT, INSERT, UPDATE, DELETE ON public.rooms TO authenticated;
GRANT ALL ON public.rooms TO service_role;

ALTER TABLE public.rooms ENABLE ROW LEVEL SECURITY;

-- Allow any authenticated user to read rooms (needed to look up by code).
-- Sensitive ops gated by other policies.
CREATE POLICY "Authenticated users can read rooms"
  ON public.rooms FOR SELECT
  TO authenticated
  USING (true);

CREATE POLICY "Authenticated users can create rooms as host"
  ON public.rooms FOR INSERT
  TO authenticated
  WITH CHECK (auth.uid() = host_id);

CREATE POLICY "Only host can update room"
  ON public.rooms FOR UPDATE
  TO authenticated
  USING (auth.uid() = host_id)
  WITH CHECK (auth.uid() = host_id);

CREATE POLICY "Only host can delete room"
  ON public.rooms FOR DELETE
  TO authenticated
  USING (auth.uid() = host_id);

-- =========================================================
-- PARTICIPANTS
-- =========================================================
CREATE TABLE public.participants (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  room_id UUID NOT NULL REFERENCES public.rooms(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  display_name TEXT NOT NULL,
  integrity INTEGER NOT NULL DEFAULT 100 CHECK (integrity BETWEEN 0 AND 100),
  breached BOOLEAN NOT NULL DEFAULT false,
  breach_reason TEXT,
  breach_at TIMESTAMPTZ,
  last_heartbeat TIMESTAMPTZ NOT NULL DEFAULT now(),
  joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  left_at TIMESTAMPTZ,
  UNIQUE (room_id, user_id)
);

CREATE INDEX idx_participants_room ON public.participants(room_id);
CREATE INDEX idx_participants_user ON public.participants(user_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON public.participants TO authenticated;
GRANT ALL ON public.participants TO service_role;

ALTER TABLE public.participants ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Authenticated users can read participants"
  ON public.participants FOR SELECT
  TO authenticated
  USING (true);

CREATE POLICY "Users can join as themselves"
  ON public.participants FOR INSERT
  TO authenticated
  WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own participant or host can update any"
  ON public.participants FOR UPDATE
  TO authenticated
  USING (
    auth.uid() = user_id
    OR EXISTS (SELECT 1 FROM public.rooms r WHERE r.id = room_id AND r.host_id = auth.uid())
  )
  WITH CHECK (
    auth.uid() = user_id
    OR EXISTS (SELECT 1 FROM public.rooms r WHERE r.id = room_id AND r.host_id = auth.uid())
  );

CREATE POLICY "Users can remove own participant or host can remove any"
  ON public.participants FOR DELETE
  TO authenticated
  USING (
    auth.uid() = user_id
    OR EXISTS (SELECT 1 FROM public.rooms r WHERE r.id = room_id AND r.host_id = auth.uid())
  );

-- =========================================================
-- BREAKS (event log)
-- =========================================================
CREATE TABLE public.breaks (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  room_id UUID NOT NULL REFERENCES public.rooms(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  display_name TEXT NOT NULL,
  reason TEXT NOT NULL,
  at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_breaks_room ON public.breaks(room_id);
CREATE INDEX idx_breaks_user ON public.breaks(user_id);

GRANT SELECT, INSERT ON public.breaks TO authenticated;
GRANT ALL ON public.breaks TO service_role;

ALTER TABLE public.breaks ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Authenticated users can read breaks"
  ON public.breaks FOR SELECT
  TO authenticated
  USING (true);

CREATE POLICY "Users can log own break"
  ON public.breaks FOR INSERT
  TO authenticated
  WITH CHECK (auth.uid() = user_id);

-- =========================================================
-- updated_at trigger
-- =========================================================
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_profiles_updated_at
  BEFORE UPDATE ON public.profiles
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER trg_rooms_updated_at
  BEFORE UPDATE ON public.rooms
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- =========================================================
-- Auto-create profile on signup
-- =========================================================
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  INSERT INTO public.profiles (id, display_name, avatar_url)
  VALUES (
    NEW.id,
    COALESCE(
      NEW.raw_user_meta_data->>'display_name',
      NEW.raw_user_meta_data->>'full_name',
      NEW.raw_user_meta_data->>'name',
      split_part(NEW.email, '@', 1),
      'Anonymous'
    ),
    NEW.raw_user_meta_data->>'avatar_url'
  )
  ON CONFLICT (id) DO NOTHING;
  RETURN NEW;
END;
$$;

CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- =========================================================
-- Realtime
-- =========================================================
ALTER PUBLICATION supabase_realtime ADD TABLE public.rooms;
ALTER PUBLICATION supabase_realtime ADD TABLE public.participants;
ALTER PUBLICATION supabase_realtime ADD TABLE public.breaks;


-- ============================================================
-- 20260620092931_77756636-904f-401b-a255-6ebcda135a05.sql
-- ============================================================

REVOKE EXECUTE ON FUNCTION public.update_updated_at_column() FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public.handle_new_user() FROM PUBLIC, anon, authenticated;


-- ============================================================
-- 20260620102838_cea976e8-f23d-43cd-82ce-89dbf2996e69.sql
-- ============================================================
-- =========================================================
-- RATE LIMITS (persistent, sliding-window)
-- =========================================================
CREATE TABLE public.rate_limits (
  key TEXT NOT NULL PRIMARY KEY,
  hits TIMESTAMPTZ[] NOT NULL DEFAULT ARRAY[]::TIMESTAMPTZ[],
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

GRANT ALL ON public.rate_limits TO service_role;

ALTER TABLE public.rate_limits ENABLE ROW LEVEL SECURITY;
-- No policies: only service_role (which bypasses RLS) accesses this table.

-- Atomic record + check. Returns TRUE when the caller exceeded the limit.
CREATE OR REPLACE FUNCTION public.check_and_record_hit(
  _key TEXT,
  _window_seconds INT,
  _max_hits INT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  _now TIMESTAMPTZ := now();
  _cutoff TIMESTAMPTZ := _now - make_interval(secs => _window_seconds);
  _new_hits TIMESTAMPTZ[];
BEGIN
  INSERT INTO public.rate_limits(key, hits)
  VALUES (_key, ARRAY[_now])
  ON CONFLICT (key) DO UPDATE
    SET hits = (
      SELECT COALESCE(array_agg(t ORDER BY t), ARRAY[]::TIMESTAMPTZ[])
      FROM unnest(public.rate_limits.hits) AS t
      WHERE t > _cutoff
    ) || _now,
    updated_at = _now
  RETURNING hits INTO _new_hits;
  RETURN COALESCE(array_length(_new_hits, 1), 0) > _max_hits;
END;
$$;

REVOKE ALL ON FUNCTION public.check_and_record_hit(TEXT, INT, INT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.check_and_record_hit(TEXT, INT, INT) FROM anon, authenticated;

-- =========================================================
-- AUTH ATTEMPTS (audit log)
-- =========================================================
CREATE TABLE public.auth_attempts (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  provider TEXT NOT NULL,
  email TEXT,
  success BOOLEAN NOT NULL DEFAULT false,
  reason TEXT,
  ip TEXT,
  user_agent TEXT,
  at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_auth_attempts_email_at ON public.auth_attempts(email, at DESC);
CREATE INDEX idx_auth_attempts_ip_at ON public.auth_attempts(ip, at DESC);
CREATE INDEX idx_auth_attempts_at ON public.auth_attempts(at DESC);

GRANT ALL ON public.auth_attempts TO service_role;

ALTER TABLE public.auth_attempts ENABLE ROW LEVEL SECURITY;
-- No policies: only service_role writes/reads this log.

-- Helper: count recent failures for a (provider, email) pair within a window.
CREATE OR REPLACE FUNCTION public.recent_auth_failures(
  _provider TEXT,
  _email TEXT,
  _window_seconds INT
)
RETURNS INT
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT COUNT(*)::INT
  FROM public.auth_attempts
  WHERE provider = _provider
    AND email = _email
    AND success = false
    AND at > now() - make_interval(secs => _window_seconds);
$$;

REVOKE ALL ON FUNCTION public.recent_auth_failures(TEXT, TEXT, INT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.recent_auth_failures(TEXT, TEXT, INT) FROM anon, authenticated;


-- ============================================================
-- 20260621033614_3446eec2-1025-4653-b143-1326f62af6a1.sql
-- ============================================================

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


-- ============================================================
-- 20260621033641_4549263e-bec2-4f95-bab1-2fff82609d50.sql
-- ============================================================

REVOKE ALL ON FUNCTION public.is_group_member(UUID, UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.record_breach(UUID, UUID, TEXT, public.breach_severity, INTEGER) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.finalize_focus_session(UUID, INTEGER, INTEGER, INTEGER, INTEGER, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.is_group_member(UUID, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.record_breach(UUID, UUID, TEXT, public.breach_severity, INTEGER) TO authenticated;
GRANT EXECUTE ON FUNCTION public.finalize_focus_session(UUID, INTEGER, INTEGER, INTEGER, INTEGER, TEXT) TO authenticated;


-- ============================================================
-- 20260621035659_94412c52-2d20-4832-985a-5e9733eb212e.sql
-- ============================================================
ALTER TABLE public.focus_groups
  ADD COLUMN IF NOT EXISTS active_session_id UUID REFERENCES public.rooms(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS active_session_code TEXT,
  ADD COLUMN IF NOT EXISTS active_session_started_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS active_session_expires_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS focus_groups_active_session_expires_idx
  ON public.focus_groups (active_session_expires_at)
  WHERE active_session_id IS NOT NULL;

-- Allow group members (not just creator) to update the active_session_* fields
-- when launching a sprint. Existing "Creator updates group" policy stays.
CREATE POLICY "Members update active session"
  ON public.focus_groups FOR UPDATE
  TO authenticated
  USING (public.is_group_member(id, auth.uid()))
  WITH CHECK (public.is_group_member(id, auth.uid()));

-- Ensure focus_groups changes stream over Realtime (postgres_changes).
ALTER TABLE public.focus_groups REPLICA IDENTITY FULL;
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables
    WHERE pubname = 'supabase_realtime' AND tablename = 'focus_groups'
  ) THEN
    EXECUTE 'ALTER PUBLICATION supabase_realtime ADD TABLE public.focus_groups';
  END IF;
END$$;

-- ============================================================
-- 20260621052404_498b9566-d40d-486e-bd0e-57506302033d.sql
-- ============================================================

-- ============================================================
-- 1. Helper: is_room_participant (SECURITY DEFINER, avoids RLS recursion)
-- ============================================================
CREATE OR REPLACE FUNCTION public.is_room_participant(_room_id uuid, _user_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.participants
    WHERE room_id = _room_id AND user_id = _user_id
  );
$$;
GRANT EXECUTE ON FUNCTION public.is_room_participant(uuid, uuid) TO authenticated;

-- ============================================================
-- 2. rooms — restrict SELECT to host + participants
-- ============================================================
DROP POLICY IF EXISTS "Authenticated users can read rooms" ON public.rooms;

CREATE POLICY "Host or participant can read room"
ON public.rooms FOR SELECT TO authenticated
USING (
  auth.uid() = host_id
  OR public.is_room_participant(id, auth.uid())
);

-- ============================================================
-- 3. participants — restrict SELECT to same-room members + host
-- ============================================================
DROP POLICY IF EXISTS "Authenticated users can read participants" ON public.participants;

CREATE POLICY "Same-room members can read participants"
ON public.participants FOR SELECT TO authenticated
USING (
  auth.uid() = user_id
  OR public.is_room_participant(room_id, auth.uid())
  OR EXISTS (
    SELECT 1 FROM public.rooms r
     WHERE r.id = participants.room_id AND r.host_id = auth.uid()
  )
);

-- ============================================================
-- 4. breaks — restrict SELECT to same-room members + host
-- ============================================================
DROP POLICY IF EXISTS "Authenticated users can read breaks" ON public.breaks;

CREATE POLICY "Same-room members can read breaks"
ON public.breaks FOR SELECT TO authenticated
USING (
  auth.uid() = user_id
  OR public.is_room_participant(room_id, auth.uid())
  OR EXISTS (
    SELECT 1 FROM public.rooms r
     WHERE r.id = breaks.room_id AND r.host_id = auth.uid()
  )
);

-- ============================================================
-- 5. auth_attempts — service-role only (deny authenticated + anon explicitly)
-- ============================================================
REVOKE ALL ON public.auth_attempts FROM authenticated, anon, public;
GRANT ALL ON public.auth_attempts TO service_role;

-- Explicit deny policies so even if a permissive grant is later added,
-- regular signed-in users still cannot read or write the audit log.
DROP POLICY IF EXISTS "Deny all to authenticated" ON public.auth_attempts;
CREATE POLICY "Deny all to authenticated"
ON public.auth_attempts AS RESTRICTIVE
FOR ALL TO authenticated, anon
USING (false) WITH CHECK (false);

-- ============================================================
-- 6. rate_limits — service-role only
-- ============================================================
REVOKE ALL ON public.rate_limits FROM authenticated, anon, public;
GRANT ALL ON public.rate_limits TO service_role;

DROP POLICY IF EXISTS "Deny all to authenticated" ON public.rate_limits;
CREATE POLICY "Deny all to authenticated"
ON public.rate_limits AS RESTRICTIVE
FOR ALL TO authenticated, anon
USING (false) WITH CHECK (false);

-- ============================================================
-- 7. RPC: claim_room_seat — single trusted entry point for joining
--    Returns the room row so the client can hydrate without needing
--    a permissive SELECT on rooms.
-- ============================================================
CREATE OR REPLACE FUNCTION public.claim_room_seat(_code text)
RETURNS public.rooms
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  _uid UUID := auth.uid();
  _room public.rooms%ROWTYPE;
  _name TEXT;
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  IF _code IS NULL OR length(_code) <> 6 THEN RAISE EXCEPTION 'bad_code'; END IF;

  SELECT * INTO _room FROM public.rooms WHERE code = upper(_code);
  IF _room.id IS NULL THEN RAISE EXCEPTION 'not_found'; END IF;
  IF _room.status = 'aborted' OR _room.status = 'complete' THEN
    -- Allow rejoin to read state for completed/aborted, but don't create new participant
    RETURN _room;
  END IF;

  SELECT COALESCE(display_name, 'Anon') INTO _name
    FROM public.profiles WHERE id = _uid;

  INSERT INTO public.participants (room_id, user_id, display_name)
  VALUES (_room.id, _uid, COALESCE(_name, 'Anon'))
  ON CONFLICT (room_id, user_id) DO NOTHING;

  RETURN _room;
END;
$$;
GRANT EXECUTE ON FUNCTION public.claim_room_seat(text) TO authenticated;

-- ============================================================
-- 8. RPC: room_code_exists — used by host to avoid collisions
-- ============================================================
CREATE OR REPLACE FUNCTION public.room_code_exists(_code text)
RETURNS boolean
LANGUAGE sql
STABLE SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (SELECT 1 FROM public.rooms WHERE code = upper(_code));
$$;
GRANT EXECUTE ON FUNCTION public.room_code_exists(text) TO authenticated;

-- ============================================================
-- 9. Lock down internal SECURITY DEFINER helpers (linter SUPA_0029)
--    These should only run from backend code paths, not via PostgREST RPC.
-- ============================================================
REVOKE EXECUTE ON FUNCTION public.check_and_record_hit(text, integer, integer) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public.recent_auth_failures(text, text, integer) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public.handle_new_user() FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public.update_updated_at_column() FROM PUBLIC, anon, authenticated;

-- ============================================================
-- 10. Realtime: topic-scoped subscription policies on realtime.messages
--     Authenticated users may only receive/send on:
--       - room:<room_id>      → must be a participant of that room
--       - group-sprints:<uid> → must be that user
-- ============================================================
ALTER TABLE IF EXISTS realtime.messages ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Topic-scoped realtime select" ON realtime.messages;
CREATE POLICY "Topic-scoped realtime select"
ON realtime.messages FOR SELECT TO authenticated
USING (
  (
    realtime.topic() ~ '^room:[0-9a-fA-F-]{36}$'
    AND public.is_room_participant(
          substring(realtime.topic() FROM 6)::uuid,
          auth.uid()
        )
  )
  OR realtime.topic() = 'group-sprints:' || auth.uid()::text
);

DROP POLICY IF EXISTS "Topic-scoped realtime insert" ON realtime.messages;
CREATE POLICY "Topic-scoped realtime insert"
ON realtime.messages FOR INSERT TO authenticated
WITH CHECK (
  realtime.topic() ~ '^room:[0-9a-fA-F-]{36}$'
  AND public.is_room_participant(
        substring(realtime.topic() FROM 6)::uuid,
        auth.uid()
      )
);


-- ============================================================
-- 20260621053337_16a9b5c0-34c8-4106-a43b-daa47d69355c.sql
-- ============================================================
CREATE OR REPLACE FUNCTION public.recent_auth_failures(_provider text, _email text, _window_seconds integer, _ip text DEFAULT NULL)
 RETURNS integer
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
  SELECT COUNT(*)::INT
  FROM public.auth_attempts
  WHERE provider = _provider
    AND email = _email
    AND success = false
    AND at > now() - make_interval(secs => _window_seconds)
    AND (_ip IS NULL OR ip::text = _ip);
$function$;

REVOKE ALL ON FUNCTION public.recent_auth_failures(text, text, integer, text) FROM PUBLIC, anon, authenticated;

-- ============================================================
-- 20260624074934_85bfdab0-4a06-469c-84b8-655eb4f46162.sql
-- ============================================================

-- Alert dedupe ledger
CREATE TABLE public.auth_alerts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  kind TEXT NOT NULL,
  subject TEXT NOT NULL,         -- e.g. normalized email, or "ip:1.2.3.4"
  failure_count INT NOT NULL DEFAULT 0,
  details JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX auth_alerts_kind_subject_created_at_idx
  ON public.auth_alerts (kind, subject, created_at DESC);

-- Service role only; no anon/authenticated grants. Reads happen via server.
GRANT ALL ON public.auth_alerts TO service_role;
ALTER TABLE public.auth_alerts ENABLE ROW LEVEL SECURITY;
-- (no policies — locked to service_role)

-- Atomic dedupe: returns the inserted row id if no recent alert of same
-- (kind, subject) exists within _cooldown_seconds; otherwise NULL.
CREATE OR REPLACE FUNCTION public.record_auth_alert_if_new(
  _kind TEXT, _subject TEXT, _cooldown_seconds INT,
  _failure_count INT, _details JSONB
) RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  _id UUID;
  _cutoff TIMESTAMPTZ := now() - make_interval(secs => _cooldown_seconds);
BEGIN
  PERFORM 1 FROM public.auth_alerts
   WHERE kind = _kind AND subject = _subject AND created_at > _cutoff
   LIMIT 1;
  IF FOUND THEN RETURN NULL; END IF;
  INSERT INTO public.auth_alerts (kind, subject, failure_count, details)
  VALUES (_kind, _subject, COALESCE(_failure_count,0), COALESCE(_details,'{}'::jsonb))
  RETURNING id INTO _id;
  RETURN _id;
END;
$$;
REVOKE EXECUTE ON FUNCTION public.record_auth_alert_if_new(TEXT,TEXT,INT,INT,JSONB) FROM PUBLIC, anon, authenticated;


-- ============================================================
-- 20260717064403_7ca81fe1-7c11-4726-b0bd-bc4bebd7af90.sql
-- ============================================================

-- Revoke public EXECUTE on all SECURITY DEFINER functions, then grant narrowly.

-- Trigger functions & internal helpers: server/trigger only
REVOKE ALL ON FUNCTION public.update_updated_at_column() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.handle_new_user() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.check_and_record_hit(text, integer, integer) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.recent_auth_failures(text, text, integer) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.recent_auth_failures(text, text, integer, text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.record_auth_alert_if_new(text, text, integer, integer, jsonb) FROM PUBLIC, anon, authenticated;

-- RLS helper functions: used inside policies (run as definer via RLS); no direct client execute needed
REVOKE ALL ON FUNCTION public.is_group_member(uuid, uuid) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.is_room_participant(uuid, uuid) FROM PUBLIC, anon, authenticated;

-- Client-callable RPCs: signed-in users only
REVOKE ALL ON FUNCTION public.record_breach(uuid, uuid, text, breach_severity, integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.record_breach(uuid, uuid, text, breach_severity, integer) TO authenticated;

REVOKE ALL ON FUNCTION public.room_code_exists(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.room_code_exists(text) TO authenticated;

REVOKE ALL ON FUNCTION public.claim_room_seat(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.claim_room_seat(text) TO authenticated;

REVOKE ALL ON FUNCTION public.finalize_focus_session(uuid, integer, integer, integer, integer, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.finalize_focus_session(uuid, integer, integer, integer, integer, text) TO authenticated;


-- ============================================================
-- 20260722085945_cb6361c1-a395-4095-b26a-7c288ff120e0.sql
-- ============================================================
GRANT EXECUTE ON FUNCTION public.is_room_participant(uuid, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.is_group_member(uuid, uuid) TO authenticated;

-- ============================================================
-- 20260722093104_email_infra.sql
-- ============================================================
-- Email infrastructure
-- Creates the queue system, send log, send state, suppression, and unsubscribe
-- tables used by both auth and transactional emails.

-- Extensions required for queue processing
CREATE EXTENSION IF NOT EXISTS pg_net SCHEMA extensions;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_cron') THEN
    CREATE EXTENSION pg_cron;
  END IF;
END $$;
CREATE EXTENSION IF NOT EXISTS supabase_vault;
CREATE EXTENSION IF NOT EXISTS pgmq;

-- Create email queues (auth = high priority, transactional = normal)
-- Wrapped in DO blocks to handle "queue already exists" errors idempotently.
DO $$ BEGIN PERFORM pgmq.create('auth_emails'); EXCEPTION WHEN OTHERS THEN NULL; END $$;
DO $$ BEGIN PERFORM pgmq.create('transactional_emails'); EXCEPTION WHEN OTHERS THEN NULL; END $$;

-- Dead-letter queues for messages that exceed max retries
DO $$ BEGIN PERFORM pgmq.create('auth_emails_dlq'); EXCEPTION WHEN OTHERS THEN NULL; END $$;
DO $$ BEGIN PERFORM pgmq.create('transactional_emails_dlq'); EXCEPTION WHEN OTHERS THEN NULL; END $$;

-- Email send log table (audit trail for all send attempts)
-- UPDATE is allowed for the service role so the suppression edge function
-- can update a log record's status when a bounce/complaint/unsubscribe occurs.
CREATE TABLE IF NOT EXISTS public.email_send_log (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  message_id TEXT,
  template_name TEXT NOT NULL,
  recipient_email TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('pending', 'sent', 'suppressed', 'failed', 'bounced', 'complained', 'dlq')),
  error_message TEXT,
  metadata JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Supabase no longer grants public-schema access to service_role by default;
-- emit the grant explicitly so edge functions can reach the table via PostgREST.
GRANT ALL ON public.email_send_log TO service_role;

ALTER TABLE public.email_send_log ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
  CREATE POLICY "Service role can read send log"
    ON public.email_send_log FOR SELECT
    USING (auth.role() = 'service_role');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  CREATE POLICY "Service role can insert send log"
    ON public.email_send_log FOR INSERT
    WITH CHECK (auth.role() = 'service_role');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  CREATE POLICY "Service role can update send log"
    ON public.email_send_log FOR UPDATE
    USING (auth.role() = 'service_role')
    WITH CHECK (auth.role() = 'service_role');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_email_send_log_created ON public.email_send_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_email_send_log_recipient ON public.email_send_log(recipient_email);

-- Backfill: add message_id column to existing tables that predate this migration
DO $$ BEGIN
  ALTER TABLE public.email_send_log ADD COLUMN message_id TEXT;
EXCEPTION WHEN duplicate_column THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_email_send_log_message ON public.email_send_log(message_id);

-- Prevent duplicate sends: only one 'sent' row per message_id.
-- If VT expires and another worker picks up the same message, the pre-send
-- check catches it. This index is a DB-level safety net for race conditions.
CREATE UNIQUE INDEX IF NOT EXISTS idx_email_send_log_message_sent_unique
  ON public.email_send_log(message_id) WHERE status = 'sent';

-- Backfill: update status CHECK constraint for existing tables that predate new statuses
DO $$ BEGIN
  ALTER TABLE public.email_send_log DROP CONSTRAINT IF EXISTS email_send_log_status_check;
  ALTER TABLE public.email_send_log ADD CONSTRAINT email_send_log_status_check
    CHECK (status IN ('pending', 'sent', 'suppressed', 'failed', 'bounced', 'complained', 'dlq'));
END $$;

-- Rate-limit state and queue config (single row, tracks Retry-After cooldown + throughput settings)
CREATE TABLE IF NOT EXISTS public.email_send_state (
  id INT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  retry_after_until TIMESTAMPTZ,
  batch_size INTEGER NOT NULL DEFAULT 10,
  send_delay_ms INTEGER NOT NULL DEFAULT 200,
  auth_email_ttl_minutes INTEGER NOT NULL DEFAULT 15,
  transactional_email_ttl_minutes INTEGER NOT NULL DEFAULT 60,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO public.email_send_state (id) VALUES (1) ON CONFLICT DO NOTHING;

-- Backfill: add config columns to existing tables that predate this migration
DO $$ BEGIN
  ALTER TABLE public.email_send_state ADD COLUMN batch_size INTEGER NOT NULL DEFAULT 10;
EXCEPTION WHEN duplicate_column THEN NULL;
END $$;
DO $$ BEGIN
  ALTER TABLE public.email_send_state ADD COLUMN send_delay_ms INTEGER NOT NULL DEFAULT 200;
EXCEPTION WHEN duplicate_column THEN NULL;
END $$;
DO $$ BEGIN
  ALTER TABLE public.email_send_state ADD COLUMN auth_email_ttl_minutes INTEGER NOT NULL DEFAULT 15;
EXCEPTION WHEN duplicate_column THEN NULL;
END $$;
DO $$ BEGIN
  ALTER TABLE public.email_send_state ADD COLUMN transactional_email_ttl_minutes INTEGER NOT NULL DEFAULT 60;
EXCEPTION WHEN duplicate_column THEN NULL;
END $$;

GRANT ALL ON public.email_send_state TO service_role;

ALTER TABLE public.email_send_state ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
  CREATE POLICY "Service role can manage send state"
    ON public.email_send_state FOR ALL
    USING (auth.role() = 'service_role')
    WITH CHECK (auth.role() = 'service_role');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- RPC wrappers so Edge Functions can interact with pgmq via supabase.rpc()
-- (PostgREST only exposes functions in the public schema; pgmq functions are in the pgmq schema)
-- All wrappers auto-create the queue on undefined_table (42P01) so emails
-- are never lost if the queue was dropped (extension upgrade, restore, etc.).
CREATE OR REPLACE FUNCTION public.enqueue_email(queue_name TEXT, payload JSONB)
RETURNS BIGINT
LANGUAGE plpgsql SECURITY DEFINER
AS $$
BEGIN
  RETURN pgmq.send(queue_name, payload);
EXCEPTION WHEN undefined_table THEN
  PERFORM pgmq.create(queue_name);
  RETURN pgmq.send(queue_name, payload);
END;
$$;

CREATE OR REPLACE FUNCTION public.read_email_batch(queue_name TEXT, batch_size INT, vt INT)
RETURNS TABLE(msg_id BIGINT, read_ct INT, message JSONB)
LANGUAGE plpgsql SECURITY DEFINER
AS $$
BEGIN
  RETURN QUERY SELECT r.msg_id, r.read_ct, r.message FROM pgmq.read(queue_name, vt, batch_size) r;
EXCEPTION WHEN undefined_table THEN
  PERFORM pgmq.create(queue_name);
  RETURN;
END;
$$;

CREATE OR REPLACE FUNCTION public.delete_email(queue_name TEXT, message_id BIGINT)
RETURNS BOOLEAN
LANGUAGE plpgsql SECURITY DEFINER
AS $$
BEGIN
  RETURN pgmq.delete(queue_name, message_id);
EXCEPTION WHEN undefined_table THEN
  RETURN FALSE;
END;
$$;

CREATE OR REPLACE FUNCTION public.move_to_dlq(
  source_queue TEXT, dlq_name TEXT, message_id BIGINT, payload JSONB
)
RETURNS BIGINT
LANGUAGE plpgsql SECURITY DEFINER
AS $$
DECLARE new_id BIGINT;
BEGIN
  SELECT pgmq.send(dlq_name, payload) INTO new_id;
  PERFORM pgmq.delete(source_queue, message_id);
  RETURN new_id;
EXCEPTION WHEN undefined_table THEN
  BEGIN
    PERFORM pgmq.create(dlq_name);
  EXCEPTION WHEN OTHERS THEN
    NULL;
  END;
  SELECT pgmq.send(dlq_name, payload) INTO new_id;
  BEGIN
    PERFORM pgmq.delete(source_queue, message_id);
  EXCEPTION WHEN undefined_table THEN
    NULL;
  END;
  RETURN new_id;
END;
$$;

-- Restrict queue RPC wrappers to service_role only (SECURITY DEFINER runs as owner,
-- so without this any authenticated user could manipulate the email queues)
REVOKE EXECUTE ON FUNCTION public.enqueue_email(TEXT, JSONB) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.enqueue_email(TEXT, JSONB) TO service_role;

REVOKE EXECUTE ON FUNCTION public.read_email_batch(TEXT, INT, INT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.read_email_batch(TEXT, INT, INT) TO service_role;

REVOKE EXECUTE ON FUNCTION public.delete_email(TEXT, BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.delete_email(TEXT, BIGINT) TO service_role;

REVOKE EXECUTE ON FUNCTION public.move_to_dlq(TEXT, TEXT, BIGINT, JSONB) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.move_to_dlq(TEXT, TEXT, BIGINT, JSONB) TO service_role;

-- Suppressed emails table (tracks unsubscribes, bounces, complaints)
-- Append-only: no DELETE or UPDATE policies to prevent bypassing suppression.
CREATE TABLE IF NOT EXISTS public.suppressed_emails (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email TEXT NOT NULL,
  reason TEXT NOT NULL CHECK (reason IN ('unsubscribe', 'bounce', 'complaint')),
  metadata JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(email)
);

GRANT ALL ON public.suppressed_emails TO service_role;

ALTER TABLE public.suppressed_emails ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
  CREATE POLICY "Service role can read suppressed emails"
    ON public.suppressed_emails FOR SELECT
    USING (auth.role() = 'service_role');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  CREATE POLICY "Service role can insert suppressed emails"
    ON public.suppressed_emails FOR INSERT
    WITH CHECK (auth.role() = 'service_role');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_suppressed_emails_email ON public.suppressed_emails(email);

-- Email unsubscribe tokens table (one token per email address for unsubscribe links)
-- No DELETE policy to prevent removing tokens. UPDATE allowed only to mark tokens as used.
CREATE TABLE IF NOT EXISTS public.email_unsubscribe_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  token TEXT NOT NULL UNIQUE,
  email TEXT NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  used_at TIMESTAMPTZ
);

GRANT ALL ON public.email_unsubscribe_tokens TO service_role;

ALTER TABLE public.email_unsubscribe_tokens ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
  CREATE POLICY "Service role can read tokens"
    ON public.email_unsubscribe_tokens FOR SELECT
    USING (auth.role() = 'service_role');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  CREATE POLICY "Service role can insert tokens"
    ON public.email_unsubscribe_tokens FOR INSERT
    WITH CHECK (auth.role() = 'service_role');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  CREATE POLICY "Service role can mark tokens as used"
    ON public.email_unsubscribe_tokens FOR UPDATE
    USING (auth.role() = 'service_role')
    WITH CHECK (auth.role() = 'service_role');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_unsubscribe_tokens_token ON public.email_unsubscribe_tokens(token);

-- ============================================================
-- POST-MIGRATION STEPS (applied dynamically by setup_email_infra)
-- These steps contain project-specific secrets and URLs and
-- cannot be expressed as static SQL. They are applied via the
-- Supabase Management API (ExecuteSQL) each time the tool runs.
-- ============================================================
--
-- 1. VAULT SECRET
--    Stores (or updates) the Supabase service_role key in
--    vault as 'email_queue_service_role_key'.
--    Uses vault.create_secret / vault.update_secret (upsert).
--    To revert: DELETE FROM vault.secrets WHERE name = 'email_queue_service_role_key';
--
-- 2. CRON JOB (pg_cron)
--    Creates job 'process-email-queue' with a 5-second interval.
--    The job checks:
--      a) rate-limit cooldown (email_send_state.retry_after_until)
--      b) whether auth_emails or transactional_emails queues have messages
--    If conditions are met, it calls the process-email-queue Edge Function
--    via net.http_post using the vault-stored service_role key.
--    To revert: SELECT cron.unschedule('process-email-queue');


-- ============================================================
-- 20260722100505_695c8a68-0349-4e28-9ff1-9d6039ef6068.sql
-- ============================================================

-- =========================================================
-- 1. Tighten SELECT policies on rooms / participants / breaks
--    to strictly same-room ACTIVE members or the host.
-- =========================================================

DROP POLICY IF EXISTS "Host or participant can read room" ON public.rooms;
CREATE POLICY "Host or active participant can read room"
  ON public.rooms
  FOR SELECT
  TO authenticated
  USING (
    auth.uid() = host_id
    OR EXISTS (
      SELECT 1 FROM public.participants p
      WHERE p.room_id = rooms.id
        AND p.user_id = auth.uid()
        AND p.left_at IS NULL
    )
  );

DROP POLICY IF EXISTS "Same-room members can read participants" ON public.participants;
CREATE POLICY "Same-room active members can read participants"
  ON public.participants
  FOR SELECT
  TO authenticated
  USING (
    auth.uid() = user_id
    OR EXISTS (
      SELECT 1 FROM public.rooms r
      WHERE r.id = participants.room_id AND r.host_id = auth.uid()
    )
    OR EXISTS (
      SELECT 1 FROM public.participants me
      WHERE me.room_id = participants.room_id
        AND me.user_id = auth.uid()
        AND me.left_at IS NULL
    )
  );

DROP POLICY IF EXISTS "Same-room members can read breaks" ON public.breaks;
CREATE POLICY "Same-room active members can read breaks"
  ON public.breaks
  FOR SELECT
  TO authenticated
  USING (
    auth.uid() = user_id
    OR EXISTS (
      SELECT 1 FROM public.rooms r
      WHERE r.id = breaks.room_id AND r.host_id = auth.uid()
    )
    OR EXISTS (
      SELECT 1 FROM public.participants me
      WHERE me.room_id = breaks.room_id
        AND me.user_id = auth.uid()
        AND me.left_at IS NULL
    )
  );

-- =========================================================
-- 2. Remove members' broad UPDATE on focus_groups; route
--    session dispatch through a scoped SECURITY DEFINER RPC.
-- =========================================================

DROP POLICY IF EXISTS "Members update active session" ON public.focus_groups;

CREATE OR REPLACE FUNCTION public.dispatch_group_sprint(
  _group_id uuid,
  _active_session_id uuid,
  _active_session_code text,
  _started_at timestamptz,
  _expires_at timestamptz
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  _uid uuid := auth.uid();
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  IF NOT public.is_group_member(_group_id, _uid) THEN
    RAISE EXCEPTION 'not_group_member';
  END IF;
  IF _active_session_id IS NULL OR _active_session_code IS NULL
     OR _started_at IS NULL OR _expires_at IS NULL THEN
    RAISE EXCEPTION 'bad_input';
  END IF;
  IF _expires_at <= _started_at THEN
    RAISE EXCEPTION 'bad_window';
  END IF;

  UPDATE public.focus_groups
     SET active_session_id = _active_session_id,
         active_session_code = upper(_active_session_code),
         active_session_started_at = _started_at,
         active_session_expires_at = _expires_at,
         updated_at = now()
   WHERE id = _group_id;
END;
$$;

REVOKE ALL ON FUNCTION public.dispatch_group_sprint(uuid, uuid, text, timestamptz, timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.dispatch_group_sprint(uuid, uuid, text, timestamptz, timestamptz) TO authenticated;

-- =========================================================
-- 3. finalize_focus_session: reject NULL room_id, verify
--    the caller was a participant, compute XP server-side.
-- =========================================================

CREATE OR REPLACE FUNCTION public.finalize_focus_session(
  _room_id uuid,
  _score integer,
  _xp integer,
  _duration_seconds integer,
  _breaches_count integer,
  _tier text
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  _uid UUID := auth.uid();
  _history_id UUID;
  _room public.rooms%ROWTYPE;
  _target INTEGER;
  _duration INTEGER;
  _breaches INTEGER;
  _score_clamped INTEGER;
  _multiplier NUMERIC;
  _tier_key TEXT;
  _server_xp INTEGER;
  _accept_xp INTEGER;
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  IF _room_id IS NULL THEN RAISE EXCEPTION 'room_required'; END IF;

  -- Must have actually participated in this room.
  IF NOT EXISTS (
    SELECT 1 FROM public.participants
    WHERE room_id = _room_id AND user_id = _uid
  ) THEN
    RAISE EXCEPTION 'not_participant';
  END IF;

  -- Idempotency: one history row per (profile, room).
  SELECT id INTO _history_id FROM public.focus_history
   WHERE profile_id = _uid AND room_id = _room_id;
  IF _history_id IS NOT NULL THEN RETURN _history_id; END IF;

  SELECT * INTO _room FROM public.rooms WHERE id = _room_id;
  _target := GREATEST(COALESCE(_room.target_duration_seconds, 1), 1);

  -- Server-authoritative inputs. Client values are only used as caps,
  -- never trusted upward. Breaches come from the audit log, not the caller.
  _duration := LEAST(GREATEST(COALESCE(_duration_seconds, 0), 0), _target);
  SELECT COUNT(*)::INT INTO _breaches
    FROM public.breaks
   WHERE room_id = _room_id AND user_id = _uid;

  _score_clamped := LEAST(GREATEST(COALESCE(_score, 0), 0), 100);

  -- Derive tier + multiplier server-side from clamped score.
  IF _score_clamped >= 95 THEN _tier_key := 'flow';         _multiplier := 1.5;
  ELSIF _score_clamped >= 85 THEN _tier_key := 'pristine';   _multiplier := 1.0;
  ELSIF _score_clamped >= 70 THEN _tier_key := 'steady';     _multiplier := 0.5;
  ELSIF _score_clamped >= 40 THEN _tier_key := 'fragmented'; _multiplier := 0.0;
  ELSE                            _tier_key := 'compromised'; _multiplier := 0.0;
  END IF;

  _server_xp := FLOOR(_score_clamped * (_duration::NUMERIC / 60) * _multiplier)::INT;
  -- Accept the smaller of client-claimed and server-authoritative XP,
  -- floored at 0. Client can never inflate.
  _accept_xp := GREATEST(LEAST(COALESCE(_xp, _server_xp), _server_xp), 0);

  INSERT INTO public.focus_history (profile_id, room_id, score, xp_earned, duration_seconds, breaches_count, tier)
  VALUES (_uid, _room_id, _score_clamped, _accept_xp, _duration, _breaches, _tier_key)
  RETURNING id INTO _history_id;

  UPDATE public.profiles
     SET lifetime_xp = lifetime_xp + _accept_xp,
         current_focus_streak = CASE WHEN _breaches = 0 AND _score_clamped >= 70
                                     THEN current_focus_streak + 1
                                     ELSE 0 END,
         updated_at = now()
   WHERE id = _uid;

  UPDATE public.focus_groups g
     SET total_group_xp = total_group_xp + _accept_xp,
         updated_at = now()
   WHERE g.id IN (SELECT group_id FROM public.group_members WHERE profile_id = _uid);

  RETURN _history_id;
END;
$$;

-- =========================================================
-- 4. search_path + tighter EXECUTE grants on internal helpers.
-- =========================================================

CREATE OR REPLACE FUNCTION public.delete_email(queue_name text, message_id bigint)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pgmq
AS $$
BEGIN
  RETURN pgmq.delete(queue_name, message_id);
EXCEPTION WHEN undefined_table THEN
  RETURN FALSE;
END;
$$;

CREATE OR REPLACE FUNCTION public.enqueue_email(queue_name text, payload jsonb)
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pgmq
AS $$
BEGIN
  RETURN pgmq.send(queue_name, payload);
EXCEPTION WHEN undefined_table THEN
  PERFORM pgmq.create(queue_name);
  RETURN pgmq.send(queue_name, payload);
END;
$$;

CREATE OR REPLACE FUNCTION public.move_to_dlq(source_queue text, dlq_name text, message_id bigint, payload jsonb)
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pgmq
AS $$
DECLARE new_id BIGINT;
BEGIN
  SELECT pgmq.send(dlq_name, payload) INTO new_id;
  PERFORM pgmq.delete(source_queue, message_id);
  RETURN new_id;
EXCEPTION WHEN undefined_table THEN
  BEGIN
    PERFORM pgmq.create(dlq_name);
  EXCEPTION WHEN OTHERS THEN NULL;
  END;
  SELECT pgmq.send(dlq_name, payload) INTO new_id;
  BEGIN
    PERFORM pgmq.delete(source_queue, message_id);
  EXCEPTION WHEN undefined_table THEN NULL;
  END;
  RETURN new_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.read_email_batch(queue_name text, batch_size integer, vt integer)
RETURNS TABLE(msg_id bigint, read_ct integer, message jsonb)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pgmq
AS $$
BEGIN
  RETURN QUERY SELECT r.msg_id, r.read_ct, r.message FROM pgmq.read(queue_name, vt, batch_size) r;
EXCEPTION WHEN undefined_table THEN
  PERFORM pgmq.create(queue_name);
  RETURN;
END;
$$;

-- Lock queue helpers to service_role only; they were previously executable by anon/authenticated.
REVOKE ALL ON FUNCTION public.delete_email(text, bigint)              FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.enqueue_email(text, jsonb)              FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.move_to_dlq(text, text, bigint, jsonb)  FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.read_email_batch(text, integer, integer) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.email_queue_dispatch()                  FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.email_queue_wake()                      FROM PUBLIC, anon, authenticated;

GRANT EXECUTE ON FUNCTION public.delete_email(text, bigint)              TO service_role;
GRANT EXECUTE ON FUNCTION public.enqueue_email(text, jsonb)              TO service_role;
GRANT EXECUTE ON FUNCTION public.move_to_dlq(text, text, bigint, jsonb)  TO service_role;
GRANT EXECUTE ON FUNCTION public.read_email_batch(text, integer, integer) TO service_role;
GRANT EXECUTE ON FUNCTION public.email_queue_dispatch()                  TO service_role;
GRANT EXECUTE ON FUNCTION public.email_queue_wake()                      TO service_role;


-- ============================================================
-- 20260722100708_487a877b-9a54-49ce-82cf-130ad7453ffe.sql
-- ============================================================
CREATE OR REPLACE FUNCTION public.dispatch_group_sprint(_group_id uuid, _active_session_id uuid, _active_session_code text, _started_at timestamp with time zone, _expires_at timestamp with time zone)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
DECLARE
  _uid uuid := auth.uid();
  _over_user boolean;
  _over_group boolean;
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  IF NOT public.is_group_member(_group_id, _uid) THEN
    RAISE EXCEPTION 'not_group_member';
  END IF;
  IF _active_session_id IS NULL OR _active_session_code IS NULL
     OR _started_at IS NULL OR _expires_at IS NULL THEN
    RAISE EXCEPTION 'bad_input';
  END IF;
  IF _expires_at <= _started_at THEN
    RAISE EXCEPTION 'bad_window';
  END IF;

  -- Rate limit: max 3 dispatches per user per 60s and 5 per group per 60s.
  _over_user  := public.check_and_record_hit('grp_sprint_user:' || _uid::text,   60, 3);
  _over_group := public.check_and_record_hit('grp_sprint_group:' || _group_id::text, 60, 5);
  IF _over_user OR _over_group THEN
    RAISE EXCEPTION 'rate_limited';
  END IF;

  UPDATE public.focus_groups
     SET active_session_id = _active_session_id,
         active_session_code = upper(_active_session_code),
         active_session_started_at = _started_at,
         active_session_expires_at = _expires_at,
         updated_at = now()
   WHERE id = _group_id;
END;
$function$;

-- ============================================================
-- 20260723025731_906120ff-1275-4e7b-b24d-d993dee65d86.sql
-- ============================================================

-- Extend profiles
ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS bio TEXT,
  ADD COLUMN IF NOT EXISTS best_streak INT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS total_focus_seconds BIGINT NOT NULL DEFAULT 0;

-- Public read of a limited profile projection is needed for friend search / public profiles.
-- The existing profiles policies restrict to owner; add a narrow read.
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='profiles' AND policyname='Authenticated can view basic profile fields') THEN
    CREATE POLICY "Authenticated can view basic profile fields"
      ON public.profiles FOR SELECT TO authenticated USING (true);
  END IF;
END $$;

-- ============================================================
-- FRIENDSHIPS
-- ============================================================
CREATE TABLE IF NOT EXISTS public.friendships (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  requester_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  addressee_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','accepted','blocked')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (requester_id <> addressee_id),
  UNIQUE (requester_id, addressee_id)
);

CREATE INDEX IF NOT EXISTS friendships_addressee_idx ON public.friendships(addressee_id, status);
CREATE INDEX IF NOT EXISTS friendships_requester_idx ON public.friendships(requester_id, status);

GRANT SELECT, INSERT, UPDATE, DELETE ON public.friendships TO authenticated;
GRANT ALL ON public.friendships TO service_role;
ALTER TABLE public.friendships ENABLE ROW LEVEL SECURITY;

CREATE POLICY "See own friendship rows" ON public.friendships FOR SELECT TO authenticated
  USING (auth.uid() = requester_id OR auth.uid() = addressee_id);

CREATE POLICY "Send friend request" ON public.friendships FOR INSERT TO authenticated
  WITH CHECK (auth.uid() = requester_id AND status = 'pending');

CREATE POLICY "Respond to own incoming request" ON public.friendships FOR UPDATE TO authenticated
  USING (auth.uid() = addressee_id OR auth.uid() = requester_id)
  WITH CHECK (auth.uid() = addressee_id OR auth.uid() = requester_id);

CREATE POLICY "Remove own friendship" ON public.friendships FOR DELETE TO authenticated
  USING (auth.uid() = requester_id OR auth.uid() = addressee_id);

CREATE TRIGGER friendships_updated_at BEFORE UPDATE ON public.friendships
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- Helper: are two users accepted friends?
CREATE OR REPLACE FUNCTION public.are_friends(_a UUID, _b UUID)
RETURNS BOOLEAN LANGUAGE sql STABLE SECURITY DEFINER SET search_path=public AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.friendships
    WHERE status='accepted' AND (
      (requester_id=_a AND addressee_id=_b) OR
      (requester_id=_b AND addressee_id=_a)
    )
  );
$$;
REVOKE ALL ON FUNCTION public.are_friends(UUID,UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.are_friends(UUID,UUID) TO authenticated;

-- ============================================================
-- ACHIEVEMENTS
-- ============================================================
CREATE TABLE IF NOT EXISTS public.achievements (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  description TEXT NOT NULL,
  icon TEXT NOT NULL DEFAULT 'trophy',
  xp_reward INT NOT NULL DEFAULT 0,
  tier TEXT NOT NULL DEFAULT 'bronze' CHECK (tier IN ('bronze','silver','gold','obsidian')),
  sort_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

GRANT SELECT ON public.achievements TO authenticated, anon;
GRANT ALL ON public.achievements TO service_role;
ALTER TABLE public.achievements ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Anyone can read achievement catalog" ON public.achievements FOR SELECT USING (true);

CREATE TABLE IF NOT EXISTS public.user_achievements (
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  achievement_id TEXT NOT NULL REFERENCES public.achievements(id) ON DELETE CASCADE,
  unlocked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, achievement_id)
);

CREATE INDEX IF NOT EXISTS user_achievements_user_idx ON public.user_achievements(user_id);

GRANT SELECT ON public.user_achievements TO authenticated;
GRANT ALL ON public.user_achievements TO service_role;
ALTER TABLE public.user_achievements ENABLE ROW LEVEL SECURITY;

CREATE POLICY "See any user's unlocks" ON public.user_achievements FOR SELECT TO authenticated USING (true);

-- Seed catalog
INSERT INTO public.achievements (id, name, description, icon, xp_reward, tier, sort_order) VALUES
  ('first_stack', 'First Stack', 'Complete your first focus session.', 'sparkles', 50, 'bronze', 10),
  ('streak_7', 'Seven-Day Rite', 'Maintain a 7-session focus streak.', 'flame', 200, 'silver', 20),
  ('streak_30', 'Thirty-Day Ascension', 'Maintain a 30-session focus streak.', 'flame', 800, 'gold', 30),
  ('hours_10', 'Ten Hours Held', 'Accumulate 10 hours of tracked focus.', 'clock', 150, 'bronze', 40),
  ('hours_100', 'Century of Silence', 'Accumulate 100 hours of tracked focus.', 'clock', 1000, 'gold', 50),
  ('no_breach', 'Unbroken', 'Complete a session with zero breaches.', 'shield', 100, 'silver', 60),
  ('flow_state', 'Flow State', 'Earn a Flow-tier score (95+).', 'zap', 250, 'gold', 70),
  ('team_player', 'Team Player', 'Complete a session inside a Focus Circle.', 'users', 150, 'silver', 80),
  ('night_owl', 'Night Owl', 'Complete a session between 22:00 and 04:00.', 'moon', 75, 'bronze', 90),
  ('early_bird', 'Early Bird', 'Complete a session between 04:00 and 08:00.', 'sunrise', 75, 'bronze', 100)
ON CONFLICT (id) DO UPDATE SET
  name = EXCLUDED.name, description = EXCLUDED.description, icon = EXCLUDED.icon,
  xp_reward = EXCLUDED.xp_reward, tier = EXCLUDED.tier, sort_order = EXCLUDED.sort_order;

-- Check + unlock achievements after a session (called from finalize_focus_session)
CREATE OR REPLACE FUNCTION public.evaluate_achievements(_user_id UUID, _history_id UUID)
RETURNS SETOF TEXT LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE
  _h public.focus_history%ROWTYPE;
  _profile public.profiles%ROWTYPE;
  _total_hours NUMERIC;
  _hour INT;
  _in_group BOOLEAN;
  _new TEXT;
BEGIN
  SELECT * INTO _h FROM public.focus_history WHERE id = _history_id AND profile_id = _user_id;
  IF _h.id IS NULL THEN RETURN; END IF;
  SELECT * INTO _profile FROM public.profiles WHERE id = _user_id;
  _total_hours := COALESCE(_profile.total_focus_seconds,0)::NUMERIC / 3600.0;
  _hour := EXTRACT(HOUR FROM _h.completed_at AT TIME ZONE 'UTC')::INT;
  SELECT EXISTS(SELECT 1 FROM public.group_members WHERE profile_id = _user_id) INTO _in_group;

  -- Helper inline: try_unlock via CTE
  FOR _new IN
    WITH candidates(aid, ok) AS (
      VALUES
        ('first_stack', TRUE),
        ('streak_7',  _profile.current_focus_streak >= 7),
        ('streak_30', _profile.current_focus_streak >= 30),
        ('hours_10',  _total_hours >= 10),
        ('hours_100', _total_hours >= 100),
        ('no_breach', _h.breaches_count = 0 AND _h.duration_seconds >= 300),
        ('flow_state', _h.score >= 95),
        ('team_player', _in_group),
        ('night_owl', _hour >= 22 OR _hour < 4),
        ('early_bird', _hour >= 4 AND _hour < 8)
    ), ins AS (
      INSERT INTO public.user_achievements (user_id, achievement_id)
      SELECT _user_id, aid FROM candidates WHERE ok
      ON CONFLICT DO NOTHING
      RETURNING achievement_id
    )
    SELECT achievement_id FROM ins
  LOOP
    -- Award XP for newly unlocked achievements
    UPDATE public.profiles p
       SET lifetime_xp = lifetime_xp + a.xp_reward,
           updated_at = now()
      FROM public.achievements a
     WHERE a.id = _new AND p.id = _user_id;
    RETURN NEXT _new;
  END LOOP;
END;
$$;
REVOKE ALL ON FUNCTION public.evaluate_achievements(UUID,UUID) FROM PUBLIC, anon, authenticated;

-- Rewrite finalize_focus_session to update best_streak, total_focus_seconds, and unlock achievements.
CREATE OR REPLACE FUNCTION public.finalize_focus_session(_room_id uuid, _score integer, _xp integer, _duration_seconds integer, _breaches_count integer, _tier text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
DECLARE
  _uid UUID := auth.uid();
  _history_id UUID;
  _room public.rooms%ROWTYPE;
  _target INTEGER;
  _duration INTEGER;
  _breaches INTEGER;
  _score_clamped INTEGER;
  _multiplier NUMERIC;
  _tier_key TEXT;
  _server_xp INTEGER;
  _accept_xp INTEGER;
  _new_streak INTEGER;
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  IF _room_id IS NULL THEN RAISE EXCEPTION 'room_required'; END IF;

  IF NOT EXISTS (SELECT 1 FROM public.participants WHERE room_id = _room_id AND user_id = _uid) THEN
    RAISE EXCEPTION 'not_participant';
  END IF;

  SELECT id INTO _history_id FROM public.focus_history WHERE profile_id = _uid AND room_id = _room_id;
  IF _history_id IS NOT NULL THEN RETURN _history_id; END IF;

  SELECT * INTO _room FROM public.rooms WHERE id = _room_id;
  _target := GREATEST(COALESCE(_room.target_duration_seconds, 1), 1);

  _duration := LEAST(GREATEST(COALESCE(_duration_seconds, 0), 0), _target);
  SELECT COUNT(*)::INT INTO _breaches FROM public.breaks WHERE room_id = _room_id AND user_id = _uid;
  _score_clamped := LEAST(GREATEST(COALESCE(_score, 0), 0), 100);

  IF _score_clamped >= 95 THEN _tier_key := 'flow';         _multiplier := 1.5;
  ELSIF _score_clamped >= 85 THEN _tier_key := 'pristine';   _multiplier := 1.0;
  ELSIF _score_clamped >= 70 THEN _tier_key := 'steady';     _multiplier := 0.5;
  ELSIF _score_clamped >= 40 THEN _tier_key := 'fragmented'; _multiplier := 0.0;
  ELSE                            _tier_key := 'compromised'; _multiplier := 0.0;
  END IF;

  _server_xp := FLOOR(_score_clamped * (_duration::NUMERIC / 60) * _multiplier)::INT;
  _accept_xp := GREATEST(LEAST(COALESCE(_xp, _server_xp), _server_xp), 0);

  INSERT INTO public.focus_history (profile_id, room_id, score, xp_earned, duration_seconds, breaches_count, tier)
  VALUES (_uid, _room_id, _score_clamped, _accept_xp, _duration, _breaches, _tier_key)
  RETURNING id INTO _history_id;

  _new_streak := CASE WHEN _breaches = 0 AND _score_clamped >= 70
                      THEN (SELECT current_focus_streak FROM public.profiles WHERE id = _uid) + 1
                      ELSE 0 END;

  UPDATE public.profiles
     SET lifetime_xp = lifetime_xp + _accept_xp,
         current_focus_streak = _new_streak,
         best_streak = GREATEST(best_streak, _new_streak),
         total_focus_seconds = total_focus_seconds + _duration,
         updated_at = now()
   WHERE id = _uid;

  UPDATE public.focus_groups g
     SET total_group_xp = total_group_xp + _accept_xp,
         updated_at = now()
   WHERE g.id IN (SELECT group_id FROM public.group_members WHERE profile_id = _uid);

  PERFORM public.evaluate_achievements(_uid, _history_id);

  RETURN _history_id;
END;
$function$;


-- ============================================================
-- 20260723030044_d62b6902-28eb-4d9c-9b63-6ab874e1d59c.sql
-- ============================================================
GRANT EXECUTE ON FUNCTION public.is_group_member(uuid, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.is_room_participant(uuid, uuid) TO authenticated;

-- ============================================================
-- 20260723030308_d509f7f0-02ec-4e74-ad76-9935e904c89c.sql
-- ============================================================

-- ============================================================
-- ENGAGEMENT LOOP: session tags/notes, daily challenges
-- ============================================================
ALTER TABLE public.focus_history
  ADD COLUMN IF NOT EXISTS notes TEXT,
  ADD COLUMN IF NOT EXISTS tags TEXT[] NOT NULL DEFAULT '{}';

ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS last_active_at TIMESTAMPTZ;

-- Daily challenge catalog (seeded rules; date-scoped assignments derived)
CREATE TABLE IF NOT EXISTS public.challenges (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  description TEXT NOT NULL,
  cadence TEXT NOT NULL CHECK (cadence IN ('daily','weekly')),
  metric TEXT NOT NULL CHECK (metric IN ('sessions','focus_minutes','perfect_sessions','flow_sessions')),
  target INT NOT NULL,
  xp_reward INT NOT NULL DEFAULT 0,
  sort_order INT NOT NULL DEFAULT 0
);
GRANT SELECT ON public.challenges TO anon, authenticated;
GRANT ALL ON public.challenges TO service_role;
ALTER TABLE public.challenges ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Anyone reads challenge catalog" ON public.challenges FOR SELECT USING (true);

INSERT INTO public.challenges (id, name, description, cadence, metric, target, xp_reward, sort_order) VALUES
  ('d_two_sessions',  'Two Sessions',   'Complete 2 focus sessions today.',       'daily',  'sessions',         2, 40, 10),
  ('d_ninety_min',    'Ninety Minutes', 'Log 90 focused minutes today.',          'daily',  'focus_minutes',   90, 60, 20),
  ('d_no_breach',     'Unbroken Day',   'Finish a session today with 0 breaches.','daily',  'perfect_sessions', 1, 80, 30),
  ('w_ten_sessions',  'Ten Sessions',   'Complete 10 sessions this week.',        'weekly', 'sessions',        10, 200, 40),
  ('w_twelve_hours',  'Twelve Hours',   'Log 12 hours of focus this week.',       'weekly', 'focus_minutes',  720, 300, 50),
  ('w_five_flow',     'Five in Flow',   'Reach Flow tier (95+) five times this week.','weekly','flow_sessions', 5, 400, 60)
ON CONFLICT (id) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, cadence=EXCLUDED.cadence,
  metric=EXCLUDED.metric, target=EXCLUDED.target, xp_reward=EXCLUDED.xp_reward,
  sort_order=EXCLUDED.sort_order;

-- Track completion per period (period_start truncated to day for daily, ISO week for weekly).
CREATE TABLE IF NOT EXISTS public.challenge_progress (
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  challenge_id TEXT NOT NULL REFERENCES public.challenges(id) ON DELETE CASCADE,
  period_start DATE NOT NULL,
  progress INT NOT NULL DEFAULT 0,
  completed_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, challenge_id, period_start)
);
CREATE INDEX IF NOT EXISTS challenge_progress_user_period_idx ON public.challenge_progress(user_id, period_start DESC);
GRANT SELECT ON public.challenge_progress TO authenticated;
GRANT ALL ON public.challenge_progress TO service_role;
ALTER TABLE public.challenge_progress ENABLE ROW LEVEL SECURITY;
CREATE POLICY "See own challenge progress" ON public.challenge_progress FOR SELECT TO authenticated USING (auth.uid() = user_id);

-- Activity feed (append-only, friend/self visible)
CREATE TABLE IF NOT EXISTS public.activity_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  kind TEXT NOT NULL CHECK (kind IN ('session_complete','achievement_unlock','challenge_complete','friend_add')),
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS activity_events_user_idx ON public.activity_events(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS activity_events_created_idx ON public.activity_events(created_at DESC);
GRANT SELECT ON public.activity_events TO authenticated;
GRANT ALL ON public.activity_events TO service_role;
ALTER TABLE public.activity_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY "See own or friends' activity"
  ON public.activity_events FOR SELECT TO authenticated
  USING (
    user_id = auth.uid()
    OR public.are_friends(auth.uid(), user_id)
  );

-- Evaluate challenges + write activity events. Called from finalize_focus_session.
CREATE OR REPLACE FUNCTION public.evaluate_challenges(_user_id UUID, _history_id UUID)
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE
  _h public.focus_history%ROWTYPE;
  _today DATE := (now() AT TIME ZONE 'UTC')::DATE;
  _week_start DATE := date_trunc('week', now() AT TIME ZONE 'UTC')::DATE;
  _c public.challenges%ROWTYPE;
  _period_start DATE;
  _increment INT;
  _row public.challenge_progress%ROWTYPE;
BEGIN
  SELECT * INTO _h FROM public.focus_history WHERE id = _history_id AND profile_id = _user_id;
  IF _h.id IS NULL THEN RETURN; END IF;

  FOR _c IN SELECT * FROM public.challenges LOOP
    _period_start := CASE WHEN _c.cadence = 'daily' THEN _today ELSE _week_start END;
    _increment := CASE _c.metric
      WHEN 'sessions'          THEN 1
      WHEN 'focus_minutes'     THEN GREATEST((_h.duration_seconds / 60), 0)
      WHEN 'perfect_sessions'  THEN CASE WHEN _h.breaches_count = 0 AND _h.duration_seconds >= 300 THEN 1 ELSE 0 END
      WHEN 'flow_sessions'     THEN CASE WHEN _h.score >= 95 THEN 1 ELSE 0 END
      ELSE 0
    END;
    IF _increment <= 0 THEN CONTINUE; END IF;

    INSERT INTO public.challenge_progress (user_id, challenge_id, period_start, progress)
    VALUES (_user_id, _c.id, _period_start, _increment)
    ON CONFLICT (user_id, challenge_id, period_start) DO UPDATE
      SET progress = public.challenge_progress.progress + EXCLUDED.progress,
          updated_at = now()
    RETURNING * INTO _row;

    IF _row.completed_at IS NULL AND _row.progress >= _c.target THEN
      UPDATE public.challenge_progress
         SET completed_at = now()
       WHERE user_id = _user_id AND challenge_id = _c.id AND period_start = _period_start;
      UPDATE public.profiles SET lifetime_xp = lifetime_xp + _c.xp_reward, updated_at = now()
       WHERE id = _user_id;
      INSERT INTO public.activity_events (user_id, kind, payload)
      VALUES (_user_id, 'challenge_complete', jsonb_build_object('id', _c.id, 'name', _c.name, 'xp', _c.xp_reward));
    END IF;
  END LOOP;
END;
$$;
REVOKE ALL ON FUNCTION public.evaluate_challenges(UUID,UUID) FROM PUBLIC, anon, authenticated;

-- Extend finalize_focus_session to emit an activity event + evaluate challenges.
CREATE OR REPLACE FUNCTION public.finalize_focus_session(_room_id uuid, _score integer, _xp integer, _duration_seconds integer, _breaches_count integer, _tier text)
 RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public' AS $function$
DECLARE
  _uid UUID := auth.uid();
  _history_id UUID;
  _room public.rooms%ROWTYPE;
  _target INTEGER;
  _duration INTEGER;
  _breaches INTEGER;
  _score_clamped INTEGER;
  _multiplier NUMERIC;
  _tier_key TEXT;
  _server_xp INTEGER;
  _accept_xp INTEGER;
  _new_streak INTEGER;
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  IF _room_id IS NULL THEN RAISE EXCEPTION 'room_required'; END IF;
  IF NOT EXISTS (SELECT 1 FROM public.participants WHERE room_id = _room_id AND user_id = _uid) THEN
    RAISE EXCEPTION 'not_participant';
  END IF;

  SELECT id INTO _history_id FROM public.focus_history WHERE profile_id = _uid AND room_id = _room_id;
  IF _history_id IS NOT NULL THEN RETURN _history_id; END IF;

  SELECT * INTO _room FROM public.rooms WHERE id = _room_id;
  _target := GREATEST(COALESCE(_room.target_duration_seconds, 1), 1);
  _duration := LEAST(GREATEST(COALESCE(_duration_seconds, 0), 0), _target);
  SELECT COUNT(*)::INT INTO _breaches FROM public.breaks WHERE room_id = _room_id AND user_id = _uid;
  _score_clamped := LEAST(GREATEST(COALESCE(_score, 0), 0), 100);

  IF _score_clamped >= 95 THEN _tier_key := 'flow';         _multiplier := 1.5;
  ELSIF _score_clamped >= 85 THEN _tier_key := 'pristine';   _multiplier := 1.0;
  ELSIF _score_clamped >= 70 THEN _tier_key := 'steady';     _multiplier := 0.5;
  ELSIF _score_clamped >= 40 THEN _tier_key := 'fragmented'; _multiplier := 0.0;
  ELSE                            _tier_key := 'compromised'; _multiplier := 0.0;
  END IF;

  _server_xp := FLOOR(_score_clamped * (_duration::NUMERIC / 60) * _multiplier)::INT;
  _accept_xp := GREATEST(LEAST(COALESCE(_xp, _server_xp), _server_xp), 0);

  INSERT INTO public.focus_history (profile_id, room_id, score, xp_earned, duration_seconds, breaches_count, tier)
  VALUES (_uid, _room_id, _score_clamped, _accept_xp, _duration, _breaches, _tier_key)
  RETURNING id INTO _history_id;

  _new_streak := CASE WHEN _breaches = 0 AND _score_clamped >= 70
                      THEN (SELECT current_focus_streak FROM public.profiles WHERE id = _uid) + 1
                      ELSE 0 END;

  UPDATE public.profiles
     SET lifetime_xp = lifetime_xp + _accept_xp,
         current_focus_streak = _new_streak,
         best_streak = GREATEST(best_streak, _new_streak),
         total_focus_seconds = total_focus_seconds + _duration,
         last_active_at = now(),
         updated_at = now()
   WHERE id = _uid;

  UPDATE public.focus_groups g SET total_group_xp = total_group_xp + _accept_xp, updated_at = now()
   WHERE g.id IN (SELECT group_id FROM public.group_members WHERE profile_id = _uid);

  INSERT INTO public.activity_events (user_id, kind, payload)
  VALUES (_uid, 'session_complete', jsonb_build_object(
    'score', _score_clamped, 'tier', _tier_key,
    'duration_seconds', _duration, 'xp', _accept_xp, 'breaches', _breaches
  ));

  PERFORM public.evaluate_achievements(_uid, _history_id);
  PERFORM public.evaluate_challenges(_uid, _history_id);

  RETURN _history_id;
END;
$function$;

-- User-supplied session meta (notes + tags) written after finalize
CREATE OR REPLACE FUNCTION public.update_session_meta(_history_id UUID, _notes TEXT, _tags TEXT[])
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE _uid UUID := auth.uid();
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  UPDATE public.focus_history
     SET notes = LEFT(COALESCE(_notes, ''), 2000),
         tags = COALESCE((SELECT array_agg(DISTINCT LOWER(TRIM(t))) FROM unnest(_tags) t WHERE LENGTH(TRIM(t)) BETWEEN 1 AND 24), '{}'::TEXT[])
   WHERE id = _history_id AND profile_id = _uid;
END;
$$;
REVOKE ALL ON FUNCTION public.update_session_meta(UUID, TEXT, TEXT[]) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.update_session_meta(UUID, TEXT, TEXT[]) TO authenticated;

-- Friend-add activity trigger (fires when a friendship transitions to accepted)
CREATE OR REPLACE FUNCTION public.friendship_accepted_activity()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
BEGIN
  IF NEW.status = 'accepted' AND (OLD.status IS DISTINCT FROM 'accepted') THEN
    INSERT INTO public.activity_events (user_id, kind, payload)
    VALUES (NEW.requester_id, 'friend_add', jsonb_build_object('friend_id', NEW.addressee_id)),
           (NEW.addressee_id, 'friend_add', jsonb_build_object('friend_id', NEW.requester_id));
  END IF;
  RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS friendship_accepted_activity_trg ON public.friendships;
CREATE TRIGGER friendship_accepted_activity_trg
  AFTER UPDATE ON public.friendships
  FOR EACH ROW EXECUTE FUNCTION public.friendship_accepted_activity();

-- Heartbeat function for presence (self-updates last_active_at)
CREATE OR REPLACE FUNCTION public.presence_heartbeat()
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE _uid UUID := auth.uid();
BEGIN
  IF _uid IS NULL THEN RETURN; END IF;
  UPDATE public.profiles SET last_active_at = now() WHERE id = _uid;
END;
$$;
REVOKE ALL ON FUNCTION public.presence_heartbeat() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.presence_heartbeat() TO authenticated;


-- ============================================================
-- 20260723030501_d247642e-44de-4481-ab8a-f457cad45f44.sql
-- ============================================================

CREATE OR REPLACE FUNCTION public.evaluate_achievements(_user_id UUID, _history_id UUID)
RETURNS SETOF TEXT LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE
  _h public.focus_history%ROWTYPE;
  _profile public.profiles%ROWTYPE;
  _total_hours NUMERIC;
  _hour INT;
  _in_group BOOLEAN;
  _new TEXT;
BEGIN
  SELECT * INTO _h FROM public.focus_history WHERE id = _history_id AND profile_id = _user_id;
  IF _h.id IS NULL THEN RETURN; END IF;
  SELECT * INTO _profile FROM public.profiles WHERE id = _user_id;
  _total_hours := COALESCE(_profile.total_focus_seconds,0)::NUMERIC / 3600.0;
  _hour := EXTRACT(HOUR FROM _h.created_at AT TIME ZONE 'UTC')::INT;
  SELECT EXISTS(SELECT 1 FROM public.group_members WHERE profile_id = _user_id) INTO _in_group;

  FOR _new IN
    WITH candidates(aid, ok) AS (
      VALUES
        ('first_stack', TRUE),
        ('streak_7',  _profile.current_focus_streak >= 7),
        ('streak_30', _profile.current_focus_streak >= 30),
        ('hours_10',  _total_hours >= 10),
        ('hours_100', _total_hours >= 100),
        ('no_breach', _h.breaches_count = 0 AND _h.duration_seconds >= 300),
        ('flow_state', _h.score >= 95),
        ('team_player', _in_group),
        ('night_owl', _hour >= 22 OR _hour < 4),
        ('early_bird', _hour >= 4 AND _hour < 8)
    ), ins AS (
      INSERT INTO public.user_achievements (user_id, achievement_id)
      SELECT _user_id, aid FROM candidates WHERE ok
      ON CONFLICT DO NOTHING
      RETURNING achievement_id
    )
    SELECT achievement_id FROM ins
  LOOP
    UPDATE public.profiles p
       SET lifetime_xp = lifetime_xp + a.xp_reward, updated_at = now()
      FROM public.achievements a
     WHERE a.id = _new AND p.id = _user_id;
    INSERT INTO public.activity_events (user_id, kind, payload)
    VALUES (_user_id, 'achievement_unlock', jsonb_build_object('id', _new));
    RETURN NEXT _new;
  END LOOP;
END;
$$;
REVOKE ALL ON FUNCTION public.evaluate_achievements(UUID,UUID) FROM PUBLIC, anon, authenticated;


-- ============================================================
-- 20260723031300_bf478f0a-9b43-41b0-900a-30b11dd4c58f.sql
-- ============================================================

-- Fix infinite recursion between rooms and participants SELECT policies
-- by routing cross-table checks through SECURITY DEFINER helpers.

CREATE OR REPLACE FUNCTION public.is_room_host(_room_id uuid, _user_id uuid)
RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
  SELECT EXISTS (SELECT 1 FROM public.rooms WHERE id = _room_id AND host_id = _user_id);
$$;

REVOKE ALL ON FUNCTION public.is_room_host(uuid, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.is_room_host(uuid, uuid) TO authenticated, service_role;

-- Rooms: allow read if host OR a participant (via helper, no self-referential subquery)
DROP POLICY IF EXISTS "Host or active participant can read room" ON public.rooms;
CREATE POLICY "Host or active participant can read room"
ON public.rooms FOR SELECT
USING (
  auth.uid() = host_id
  OR public.is_room_participant(id, auth.uid())
);

-- Participants: replace the rooms subquery with the host helper
DROP POLICY IF EXISTS "Same-room active members can read participants" ON public.participants;
CREATE POLICY "Same-room active members can read participants"
ON public.participants FOR SELECT
USING (
  auth.uid() = user_id
  OR public.is_room_host(room_id, auth.uid())
  OR public.is_room_participant(room_id, auth.uid())
);

DROP POLICY IF EXISTS "Users can update own participant or host can update any" ON public.participants;
CREATE POLICY "Users can update own participant or host can update any"
ON public.participants FOR UPDATE
USING (auth.uid() = user_id OR public.is_room_host(room_id, auth.uid()))
WITH CHECK (auth.uid() = user_id OR public.is_room_host(room_id, auth.uid()));

DROP POLICY IF EXISTS "Users can remove own participant or host can remove any" ON public.participants;
CREATE POLICY "Users can remove own participant or host can remove any"
ON public.participants FOR DELETE
USING (auth.uid() = user_id OR public.is_room_host(room_id, auth.uid()));


-- ============================================================
-- 20260723032039_0d52c53d-55f0-463c-a753-8d28f685d53e.sql
-- ============================================================

-- ============================================================
-- Rooms 2.0 — metadata, moderators, join requests, templates, live events
-- ============================================================

-- 1) Extend rooms with metadata columns
ALTER TABLE public.rooms
  ADD COLUMN IF NOT EXISTS title TEXT,
  ADD COLUMN IF NOT EXISTS description TEXT,
  ADD COLUMN IF NOT EXISTS banner_url TEXT,
  ADD COLUMN IF NOT EXISTS pinned_message TEXT,
  ADD COLUMN IF NOT EXISTS collective_goal_seconds INTEGER,
  ADD COLUMN IF NOT EXISTS visibility TEXT NOT NULL DEFAULT 'invite'
    CHECK (visibility IN ('open','request','invite')),
  ADD COLUMN IF NOT EXISTS template_key TEXT;

-- 2) Room templates seed table
CREATE TABLE IF NOT EXISTS public.room_templates (
  key TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  description TEXT NOT NULL,
  target_duration_seconds INTEGER NOT NULL,
  banner_tone TEXT NOT NULL DEFAULT 'ember',
  visibility TEXT NOT NULL DEFAULT 'invite',
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
GRANT SELECT ON public.room_templates TO authenticated;
GRANT ALL ON public.room_templates TO service_role;
ALTER TABLE public.room_templates ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Templates readable by authenticated"
  ON public.room_templates FOR SELECT TO authenticated USING (true);

INSERT INTO public.room_templates (key, title, description, target_duration_seconds, banner_tone, visibility, sort_order) VALUES
  ('deep_work',  'Deep Work',         '90 minutes of uninterrupted focus. For deep code, writing, or study.', 5400, 'ember',  'invite', 10),
  ('study_hall', 'Study Hall',        'A long study block with room for many. Quiet and steady.',            7200, 'silver', 'open',   20),
  ('sprint',     'Sprint',            '25-minute pomodoro sprint. Fast, high-intent.',                        1500, 'ember',  'open',   30),
  ('silent_co',  'Silent Coworking',  'Two hours of parallel work. Camera off, sound off.',                   7200, 'silver', 'request',40),
  ('exam_prep',  'Exam Prep',         'Three hours of focused revision. Absolute mode recommended.',         10800, 'ember',  'request',50)
ON CONFLICT (key) DO UPDATE SET
  title = EXCLUDED.title,
  description = EXCLUDED.description,
  target_duration_seconds = EXCLUDED.target_duration_seconds,
  banner_tone = EXCLUDED.banner_tone,
  visibility = EXCLUDED.visibility,
  sort_order = EXCLUDED.sort_order;

-- 3) Moderators
CREATE TABLE IF NOT EXISTS public.room_moderators (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  room_id UUID NOT NULL REFERENCES public.rooms(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (room_id, user_id)
);
CREATE INDEX IF NOT EXISTS room_moderators_room_idx ON public.room_moderators(room_id);
GRANT SELECT, INSERT, DELETE ON public.room_moderators TO authenticated;
GRANT ALL ON public.room_moderators TO service_role;
ALTER TABLE public.room_moderators ENABLE ROW LEVEL SECURITY;

-- 4) Join requests
CREATE TABLE IF NOT EXISTS public.room_join_requests (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  room_id UUID NOT NULL REFERENCES public.rooms(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  display_name TEXT NOT NULL,
  message TEXT,
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','approved','denied','cancelled')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  responded_at TIMESTAMPTZ,
  UNIQUE (room_id, user_id)
);
CREATE INDEX IF NOT EXISTS room_join_requests_room_status_idx
  ON public.room_join_requests(room_id, status);
GRANT SELECT, INSERT, UPDATE ON public.room_join_requests TO authenticated;
GRANT ALL ON public.room_join_requests TO service_role;
ALTER TABLE public.room_join_requests ENABLE ROW LEVEL SECURITY;

-- 5) Live activity events (append-only)
CREATE TABLE IF NOT EXISTS public.room_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  room_id UUID NOT NULL REFERENCES public.rooms(id) ON DELETE CASCADE,
  actor_id UUID,
  actor_name TEXT,
  kind TEXT NOT NULL CHECK (kind IN (
    'joined','left','started','paused','resumed','breach','completed','pinned','goal_hit','moderator_added','moderator_removed','join_requested','join_approved','join_denied'
  )),
  payload JSONB NOT NULL DEFAULT '{}'::JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS room_events_room_created_idx
  ON public.room_events(room_id, created_at DESC);
GRANT SELECT ON public.room_events TO authenticated;
GRANT ALL ON public.room_events TO service_role;
ALTER TABLE public.room_events ENABLE ROW LEVEL SECURITY;

-- 6) Helper: moderator check (routes cross-table through security definer)
CREATE OR REPLACE FUNCTION public.is_room_moderator(_room_id UUID, _user_id UUID)
RETURNS BOOLEAN
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
  SELECT public.is_room_host(_room_id, _user_id)
      OR EXISTS (SELECT 1 FROM public.room_moderators WHERE room_id = _room_id AND user_id = _user_id);
$$;
REVOKE ALL ON FUNCTION public.is_room_moderator(uuid, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.is_room_moderator(uuid, uuid) TO authenticated, service_role;

-- 7) Policies for the new tables

-- Moderators: readable by anyone who can see the room; write only via host RPCs (host_id via helper)
CREATE POLICY "Room-visible users read moderators"
  ON public.room_moderators FOR SELECT TO authenticated
  USING (public.is_room_host(room_id, auth.uid()) OR public.is_room_participant(room_id, auth.uid()));

CREATE POLICY "Host can add moderators"
  ON public.room_moderators FOR INSERT TO authenticated
  WITH CHECK (public.is_room_host(room_id, auth.uid()));

CREATE POLICY "Host can remove moderators"
  ON public.room_moderators FOR DELETE TO authenticated
  USING (public.is_room_host(room_id, auth.uid()));

-- Join requests: user reads own; moderators read all for their room
CREATE POLICY "User reads own join requests"
  ON public.room_join_requests FOR SELECT TO authenticated
  USING (auth.uid() = user_id OR public.is_room_moderator(room_id, auth.uid()));

CREATE POLICY "User creates own join requests"
  ON public.room_join_requests FOR INSERT TO authenticated
  WITH CHECK (auth.uid() = user_id AND status = 'pending');

CREATE POLICY "User cancels own, moderator responds"
  ON public.room_join_requests FOR UPDATE TO authenticated
  USING (auth.uid() = user_id OR public.is_room_moderator(room_id, auth.uid()))
  WITH CHECK (auth.uid() = user_id OR public.is_room_moderator(room_id, auth.uid()));

-- Room events: readable by host/participants; writes only through record_room_event helper
CREATE POLICY "Room-visible users read events"
  ON public.room_events FOR SELECT TO authenticated
  USING (public.is_room_host(room_id, auth.uid()) OR public.is_room_participant(room_id, auth.uid()));

-- 8) Extend rooms SELECT policy so open rooms are publicly discoverable
DROP POLICY IF EXISTS "Host or active participant can read room" ON public.rooms;
CREATE POLICY "Host, participant, or open visibility can read room"
  ON public.rooms FOR SELECT
  USING (
    auth.uid() = host_id
    OR public.is_room_participant(id, auth.uid())
    OR visibility = 'open'
  );

-- 9) Event recorder (SECURITY DEFINER — callers must be room-visible)
CREATE OR REPLACE FUNCTION public.record_room_event(
  _room_id UUID, _kind TEXT, _payload JSONB DEFAULT '{}'::JSONB
) RETURNS UUID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE
  _uid UUID := auth.uid();
  _name TEXT;
  _id UUID;
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  IF NOT (public.is_room_host(_room_id, _uid) OR public.is_room_participant(_room_id, _uid)) THEN
    RAISE EXCEPTION 'not_room_member';
  END IF;
  SELECT display_name INTO _name FROM public.profiles WHERE id = _uid;
  INSERT INTO public.room_events (room_id, actor_id, actor_name, kind, payload)
  VALUES (_room_id, _uid, COALESCE(_name, 'Anon'), _kind, COALESCE(_payload, '{}'::JSONB))
  RETURNING id INTO _id;
  RETURN _id;
END;
$$;
REVOKE ALL ON FUNCTION public.record_room_event(uuid, text, jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.record_room_event(uuid, text, jsonb) TO authenticated, service_role;

-- 10) Hook existing state transitions into the event feed
CREATE OR REPLACE FUNCTION public.claim_room_seat(_code text)
 RETURNS rooms
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
DECLARE
  _uid UUID := auth.uid();
  _room public.rooms%ROWTYPE;
  _name TEXT;
  _inserted BOOLEAN := FALSE;
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  IF _code IS NULL OR length(_code) <> 6 THEN RAISE EXCEPTION 'bad_code'; END IF;

  SELECT * INTO _room FROM public.rooms WHERE code = upper(_code);
  IF _room.id IS NULL THEN RAISE EXCEPTION 'not_found'; END IF;
  IF _room.status = 'aborted' OR _room.status = 'complete' THEN
    RETURN _room;
  END IF;

  -- Gate: request-only rooms require an approved request
  IF _room.visibility = 'request'
     AND _uid <> _room.host_id
     AND NOT EXISTS (
       SELECT 1 FROM public.participants WHERE room_id = _room.id AND user_id = _uid
     )
     AND NOT EXISTS (
       SELECT 1 FROM public.room_join_requests
        WHERE room_id = _room.id AND user_id = _uid AND status = 'approved'
     ) THEN
    RAISE EXCEPTION 'needs_approval';
  END IF;

  SELECT COALESCE(display_name, 'Anon') INTO _name
    FROM public.profiles WHERE id = _uid;

  INSERT INTO public.participants (room_id, user_id, display_name)
  VALUES (_room.id, _uid, COALESCE(_name, 'Anon'))
  ON CONFLICT (room_id, user_id) DO NOTHING;
  GET DIAGNOSTICS _inserted = ROW_COUNT;

  IF _inserted THEN
    INSERT INTO public.room_events (room_id, actor_id, actor_name, kind, payload)
    VALUES (_room.id, _uid, COALESCE(_name, 'Anon'), 'joined', '{}'::JSONB);
  END IF;

  RETURN _room;
END;
$function$;

-- Record breach events into the live feed as well
CREATE OR REPLACE FUNCTION public.record_breach(_room_id uuid, _participant_id uuid, _reason text, _severity breach_severity, _integrity integer)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
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

  IF _severity = 'severe' THEN
    UPDATE public.participants
       SET breached = TRUE,
           breach_reason = _reason,
           breach_at = now(),
           integrity = LEAST(GREATEST(_integrity, 0), 100)
     WHERE id = _participant_id AND user_id = _uid;
  END IF;

  INSERT INTO public.room_events (room_id, actor_id, actor_name, kind, payload)
  VALUES (_room_id, _uid, _name, 'breach',
          jsonb_build_object('reason', _reason, 'severity', _severity));
END;
$function$;

-- 11) Real-time publication
ALTER PUBLICATION supabase_realtime ADD TABLE public.room_events;


-- ============================================================
-- 20260723033539_3a3da65a-6e34-4a18-86f9-19b9da46612e.sql
-- ============================================================

-- Session reactions
CREATE TABLE public.session_reactions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES public.focus_history(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  emoji TEXT NOT NULL CHECK (char_length(emoji) BETWEEN 1 AND 8),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (session_id, user_id, emoji)
);
CREATE INDEX idx_session_reactions_session ON public.session_reactions(session_id);

GRANT SELECT, INSERT, DELETE ON public.session_reactions TO authenticated;
GRANT ALL ON public.session_reactions TO service_role;
ALTER TABLE public.session_reactions ENABLE ROW LEVEL SECURITY;

CREATE POLICY "reactions_select_friends_or_owner" ON public.session_reactions FOR SELECT
TO authenticated USING (
  EXISTS (
    SELECT 1 FROM public.focus_history fh
    WHERE fh.id = session_reactions.session_id
      AND (fh.profile_id = auth.uid() OR public.are_friends(fh.profile_id, auth.uid()))
  )
);
CREATE POLICY "reactions_insert_self_on_friend_or_own" ON public.session_reactions FOR INSERT
TO authenticated WITH CHECK (
  user_id = auth.uid() AND EXISTS (
    SELECT 1 FROM public.focus_history fh
    WHERE fh.id = session_reactions.session_id
      AND (fh.profile_id = auth.uid() OR public.are_friends(fh.profile_id, auth.uid()))
  )
);
CREATE POLICY "reactions_delete_self" ON public.session_reactions FOR DELETE
TO authenticated USING (user_id = auth.uid());

-- Session workspace items
CREATE TABLE public.session_workspace_items (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID REFERENCES public.focus_history(id) ON DELETE CASCADE,
  room_id UUID REFERENCES public.rooms(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  kind TEXT NOT NULL CHECK (kind IN ('note','todo','link')),
  content TEXT NOT NULL CHECK (char_length(content) BETWEEN 1 AND 4000),
  url TEXT,
  done BOOLEAN NOT NULL DEFAULT FALSE,
  position INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_workspace_user ON public.session_workspace_items(user_id, created_at DESC);
CREATE INDEX idx_workspace_session ON public.session_workspace_items(session_id);
CREATE INDEX idx_workspace_room ON public.session_workspace_items(room_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON public.session_workspace_items TO authenticated;
GRANT ALL ON public.session_workspace_items TO service_role;
ALTER TABLE public.session_workspace_items ENABLE ROW LEVEL SECURITY;

CREATE POLICY "workspace_own_all" ON public.session_workspace_items FOR ALL
TO authenticated USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid());

CREATE TRIGGER trg_workspace_updated_at
BEFORE UPDATE ON public.session_workspace_items
FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- Realtime for reactions
ALTER PUBLICATION supabase_realtime ADD TABLE public.session_reactions;


-- ============================================================
-- 20260723040300_707d2a19-2bda-4c0e-806d-d67cac560869.sql
-- ============================================================

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


-- ============================================================
-- 20260723041852_f0a2871f-b50a-4570-93bf-1d86fa70934f.sql
-- ============================================================

-- Daily rewards: track claim streak per user
CREATE TABLE public.login_streaks (
  user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  streak INT NOT NULL DEFAULT 0,
  last_claim_date DATE,
  total_claims INT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
GRANT SELECT, INSERT, UPDATE ON public.login_streaks TO authenticated;
GRANT ALL ON public.login_streaks TO service_role;
ALTER TABLE public.login_streaks ENABLE ROW LEVEL SECURITY;
CREATE POLICY "own login streak" ON public.login_streaks
  FOR SELECT TO authenticated USING (auth.uid() = user_id);

-- Time capsules
CREATE TABLE public.time_capsules (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  message TEXT NOT NULL,
  open_at TIMESTAMPTZ NOT NULL,
  opened_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
GRANT SELECT, INSERT, UPDATE, DELETE ON public.time_capsules TO authenticated;
GRANT ALL ON public.time_capsules TO service_role;
ALTER TABLE public.time_capsules ENABLE ROW LEVEL SECURITY;
CREATE POLICY "own capsules read" ON public.time_capsules
  FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "own capsules write" ON public.time_capsules
  FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY "own capsules update" ON public.time_capsules
  FOR UPDATE TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "own capsules delete" ON public.time_capsules
  FOR DELETE TO authenticated USING (auth.uid() = user_id);
CREATE INDEX time_capsules_user_open_idx ON public.time_capsules(user_id, open_at);

-- Trust & Safety: reports
CREATE TABLE public.user_reports (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  reporter_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  target_user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
  target_room_id UUID REFERENCES public.rooms(id) ON DELETE CASCADE,
  kind TEXT NOT NULL,
  reason TEXT,
  status TEXT NOT NULL DEFAULT 'open',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
GRANT SELECT, INSERT ON public.user_reports TO authenticated;
GRANT ALL ON public.user_reports TO service_role;
ALTER TABLE public.user_reports ENABLE ROW LEVEL SECURITY;
CREATE POLICY "reporters see their reports" ON public.user_reports
  FOR SELECT TO authenticated USING (auth.uid() = reporter_id);
CREATE POLICY "authenticated can file reports" ON public.user_reports
  FOR INSERT TO authenticated WITH CHECK (auth.uid() = reporter_id);

-- Trust & Safety: blocks
CREATE TABLE public.user_blocks (
  blocker_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  blocked_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (blocker_id, blocked_id)
);
GRANT SELECT, INSERT, DELETE ON public.user_blocks TO authenticated;
GRANT ALL ON public.user_blocks TO service_role;
ALTER TABLE public.user_blocks ENABLE ROW LEVEL SECURITY;
CREATE POLICY "own blocks read" ON public.user_blocks
  FOR SELECT TO authenticated USING (auth.uid() = blocker_id);
CREATE POLICY "own blocks write" ON public.user_blocks
  FOR INSERT TO authenticated WITH CHECK (auth.uid() = blocker_id);
CREATE POLICY "own blocks delete" ON public.user_blocks
  FOR DELETE TO authenticated USING (auth.uid() = blocker_id);

-- Daily reward claim RPC
CREATE OR REPLACE FUNCTION public.claim_daily_reward()
RETURNS TABLE(reward_xp INT, new_streak INT, day_of_streak INT)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE
  _uid UUID := auth.uid();
  _today DATE := (now() AT TIME ZONE 'UTC')::DATE;
  _row public.login_streaks%ROWTYPE;
  _new_streak INT;
  _reward INT;
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  SELECT * INTO _row FROM public.login_streaks WHERE user_id = _uid FOR UPDATE;
  IF _row.user_id IS NULL THEN
    _new_streak := 1;
    INSERT INTO public.login_streaks(user_id, streak, last_claim_date, total_claims, updated_at)
      VALUES (_uid, 1, _today, 1, now());
  ELSE
    IF _row.last_claim_date = _today THEN RAISE EXCEPTION 'already_claimed'; END IF;
    IF _row.last_claim_date = _today - 1 THEN _new_streak := _row.streak + 1;
    ELSE _new_streak := 1; END IF;
    UPDATE public.login_streaks
      SET streak = _new_streak, last_claim_date = _today,
          total_claims = _row.total_claims + 1, updated_at = now()
      WHERE user_id = _uid;
  END IF;
  -- reward ramp: day1=10, day2=20, day3=40, day4=60, day5=80, day6=100, day7=200 (loop)
  _reward := CASE ((_new_streak - 1) % 7)
    WHEN 0 THEN 10 WHEN 1 THEN 20 WHEN 2 THEN 40
    WHEN 3 THEN 60 WHEN 4 THEN 80 WHEN 5 THEN 100
    ELSE 200 END;
  UPDATE public.profiles SET lifetime_xp = lifetime_xp + _reward, updated_at = now()
    WHERE id = _uid;
  INSERT INTO public.activity_events(user_id, kind, payload)
    VALUES (_uid, 'daily_reward', jsonb_build_object('xp', _reward, 'streak', _new_streak));
  reward_xp := _reward; new_streak := _new_streak; day_of_streak := ((_new_streak - 1) % 7) + 1;
  RETURN NEXT;
END;
$$;
REVOKE ALL ON FUNCTION public.claim_daily_reward() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.claim_daily_reward() TO authenticated;

-- Prestige RPC: requires 100k lifetime XP per level, resets streak but keeps XP total
CREATE OR REPLACE FUNCTION public.prestige_up()
RETURNS TABLE(new_prestige INT)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE
  _uid UUID := auth.uid();
  _xp BIGINT;
  _level INT;
  _needed BIGINT;
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  SELECT lifetime_xp, COALESCE(prestige_level,0) INTO _xp, _level
    FROM public.profiles WHERE id = _uid FOR UPDATE;
  _needed := 100000::BIGINT * (_level + 1);
  IF _xp < _needed THEN RAISE EXCEPTION 'not_enough_xp'; END IF;
  UPDATE public.profiles
    SET prestige_level = _level + 1,
        current_focus_streak = 0,
        updated_at = now()
    WHERE id = _uid;
  INSERT INTO public.activity_events(user_id, kind, payload)
    VALUES (_uid, 'prestige', jsonb_build_object('level', _level + 1));
  new_prestige := _level + 1;
  RETURN NEXT;
END;
$$;
REVOKE ALL ON FUNCTION public.prestige_up() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.prestige_up() TO authenticated;

-- Enable Realtime for milestones so the room lobby streams new entries
ALTER PUBLICATION supabase_realtime ADD TABLE public.room_milestones;


-- ============================================================
-- 20260723043229_e71ca700-5116-445c-bf42-30790d444c4d.sql
-- ============================================================

CREATE TABLE public.webhook_deliveries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  webhook_id UUID NOT NULL REFERENCES public.webhooks(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  event TEXT NOT NULL,
  status_code INT,
  ok BOOLEAN NOT NULL DEFAULT false,
  response_snippet TEXT,
  attempt INT NOT NULL DEFAULT 1,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX webhook_deliveries_wh_idx ON public.webhook_deliveries(webhook_id, created_at DESC);
CREATE INDEX webhook_deliveries_user_idx ON public.webhook_deliveries(user_id, created_at DESC);

GRANT SELECT ON public.webhook_deliveries TO authenticated;
GRANT ALL ON public.webhook_deliveries TO service_role;

ALTER TABLE public.webhook_deliveries ENABLE ROW LEVEL SECURITY;

CREATE POLICY "owner reads deliveries"
  ON public.webhook_deliveries FOR SELECT
  TO authenticated
  USING (user_id = auth.uid());


-- ============================================================
-- 20260723043319_84144652-9f28-407d-ace2-9131156f323b.sql
-- ============================================================

CREATE POLICY "host reads room reports"
  ON public.user_reports FOR SELECT
  TO authenticated
  USING (
    target_room_id IS NOT NULL
    AND EXISTS (
      SELECT 1 FROM public.rooms r
      WHERE r.id = user_reports.target_room_id
        AND r.host_id = auth.uid()
    )
  );

CREATE POLICY "host resolves room reports"
  ON public.user_reports FOR UPDATE
  TO authenticated
  USING (
    target_room_id IS NOT NULL
    AND EXISTS (
      SELECT 1 FROM public.rooms r
      WHERE r.id = user_reports.target_room_id
        AND r.host_id = auth.uid()
    )
  )
  WITH CHECK (
    target_room_id IS NOT NULL
    AND EXISTS (
      SELECT 1 FROM public.rooms r
      WHERE r.id = user_reports.target_room_id
        AND r.host_id = auth.uid()
    )
  );


-- ============================================================
-- 20260723112101_f3d42d26-fd42-4f59-88c1-68bffdb2fc8c.sql
-- ============================================================

-- Fix 1: Revoke anon EXECUTE on user-only SECURITY DEFINER functions
REVOKE EXECUTE ON FUNCTION public.claim_daily_reward() FROM anon, PUBLIC;
REVOKE EXECUTE ON FUNCTION public.prestige_up() FROM anon, PUBLIC;
REVOKE EXECUTE ON FUNCTION public.dispatch_group_sprint(uuid, uuid, text, timestamptz, timestamptz) FROM anon, PUBLIC;

-- Fix 2: Restrict group_members INSERT so only the group creator can add members.
-- Previously any authenticated user could self-insert into any group, bypassing visibility.
DROP POLICY IF EXISTS "Self join group" ON public.group_members;

CREATE POLICY "Creator adds members"
  ON public.group_members
  FOR INSERT
  TO authenticated
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM public.focus_groups g
      WHERE g.id = group_members.group_id
        AND g.created_by = auth.uid()
    )
  );


-- ============================================================
-- 20260723123314_1c4a41cd-b11b-4813-8b28-3ea96e6697aa.sql
-- ============================================================
REVOKE EXECUTE ON FUNCTION public.friendship_accepted_activity() FROM anon, PUBLIC; REVOKE EXECUTE ON FUNCTION public.rooms_add_collective_seconds() FROM anon, PUBLIC;

-- ============================================================
-- 20260724062452_34346586-d047-4d6c-98a4-271a3f2a813f.sql
-- ============================================================
-- Scope participants RLS policies to authenticated role only.
-- Previously these policies applied to PUBLIC/anon and evaluated is_room_host()
-- which is not executable by anon, causing "permission denied for function" on
-- any anonymous read (e.g. when landing on an open room page while signed out).

DROP POLICY IF EXISTS "Same-room active members can read participants" ON public.participants;
DROP POLICY IF EXISTS "Users can remove own participant or host can remove any" ON public.participants;
DROP POLICY IF EXISTS "Users can update own participant or host can update any" ON public.participants;

CREATE POLICY "Same-room active members can read participants"
  ON public.participants FOR SELECT
  TO authenticated
  USING (
    auth.uid() = user_id
    OR public.is_room_host(room_id, auth.uid())
    OR public.is_room_participant(room_id, auth.uid())
  );

CREATE POLICY "Users can remove own participant or host can remove any"
  ON public.participants FOR DELETE
  TO authenticated
  USING (
    auth.uid() = user_id
    OR public.is_room_host(room_id, auth.uid())
  );

CREATE POLICY "Users can update own participant or host can update any"
  ON public.participants FOR UPDATE
  TO authenticated
  USING (
    auth.uid() = user_id
    OR public.is_room_host(room_id, auth.uid())
  )
  WITH CHECK (
    auth.uid() = user_id
    OR public.is_room_host(room_id, auth.uid())
  );


-- ============================================================
-- 20260724115513_95bc5f6e-e5a8-40c4-8052-56a609122f88.sql
-- ============================================================

-- 1) focus_history: remove direct client INSERT policy; SECURITY DEFINER RPC finalize_focus_session bypasses RLS
DROP POLICY IF EXISTS "Users insert own focus history" ON public.focus_history;

-- 2) focus_groups: restrict UPDATE to non-sensitive columns via column-level grants.
-- SECURITY DEFINER RPCs (finalize_focus_session, dispatch_group_sprint) run as owner and bypass column grants.
REVOKE UPDATE ON public.focus_groups FROM authenticated;
GRANT UPDATE (name, updated_at) ON public.focus_groups TO authenticated;


-- ============================================================
-- 20260727035130_012c04c0-47c3-4eaa-b8de-a5bf6831741d.sql
-- ============================================================
ALTER TABLE public.activity_events DROP CONSTRAINT IF EXISTS activity_events_kind_check;

ALTER TABLE public.activity_events
  ADD CONSTRAINT activity_events_kind_check
  CHECK (kind = ANY (ARRAY[
    'session_complete',
    'achievement_unlock',
    'challenge_complete',
    'friend_add',
    'daily_reward',
    'prestige',
    'session.started',
    'session.completed',
    'session.breached',
    'room.created',
    'room.joined',
    'atlas.recommendation_shown',
    'atlas.recommendation_dismissed',
    'low_power.toggled',
    'integration.viewed'
  ]::text[]));

-- ============================================================
-- 20260730022718_cafd0554-0355-42e8-9d43-917a77af4e12.sql
-- ============================================================
-- 1. Allowlists ------------------------------------------------------------
ALTER TABLE public.room_events DROP CONSTRAINT IF EXISTS room_events_kind_check;
ALTER TABLE public.room_events ADD CONSTRAINT room_events_kind_check CHECK (kind = ANY (ARRAY[
  'joined','left','started','paused','resumed','breach','completed','pinned','goal_hit',
  'moderator_added','moderator_removed','join_requested','join_approved','join_denied',
  'ready','unready','all_ready','disconnected','reconnected'
]));

ALTER TABLE public.activity_events DROP CONSTRAINT IF EXISTS activity_events_kind_check;
ALTER TABLE public.activity_events ADD CONSTRAINT activity_events_kind_check CHECK (kind = ANY (ARRAY[
  'session_complete','achievement_unlock','challenge_complete','friend_add','daily_reward','prestige',
  'session.started','session.completed','session.breached','room.created','room.joined',
  'atlas.recommendation_shown','atlas.recommendation_dismissed','low_power.toggled','integration.viewed',
  'milestone_unlock','personality_shift','season_join','wrapped.viewed'
]));

ALTER TABLE public.achievements DROP CONSTRAINT IF EXISTS achievements_tier_check;
ALTER TABLE public.achievements ADD CONSTRAINT achievements_tier_check CHECK (tier = ANY (ARRAY[
  'bronze','silver','gold','obsidian','milestone'
]));

-- 2. Weekly named seasons ---------------------------------------------------
CREATE OR REPLACE FUNCTION public.ensure_current_season()
RETURNS public.seasons
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  _start TIMESTAMPTZ := date_trunc('week', now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC';
  _end   TIMESTAMPTZ := _start + INTERVAL '7 days' - INTERVAL '1 second';
  _s public.seasons%ROWTYPE;
  _names TEXT[] := ARRAY[
    'Season of Quiet Hands','The Long Dark','Ember Week','Still Water','Iron Silence',
    'The Slow Burn','Obsidian Tide','Held Breath','The Deep Field','Ash and Focus',
    'Northern Quiet','The Steady Hour','Lantern Week','Cold Forge','The Unbroken Line',
    'Glass Morning'
  ];
  _idx INT;
  _ord INT;
BEGIN
  SELECT * INTO _s FROM public.seasons WHERE starts_at = _start LIMIT 1;
  IF _s.id IS NOT NULL THEN RETURN _s; END IF;

  _ord := (EXTRACT(EPOCH FROM _start)::BIGINT / 604800)::INT;
  _idx := (_ord % array_length(_names, 1)) + 1;

  INSERT INTO public.seasons (name, description, starts_at, ends_at, xp_multiplier)
  VALUES (_names[_idx], 'Week ' || _ord::TEXT, _start, _end, 1.0)
  ON CONFLICT DO NOTHING
  RETURNING * INTO _s;

  IF _s.id IS NULL THEN
    SELECT * INTO _s FROM public.seasons WHERE starts_at = _start LIMIT 1;
  END IF;
  RETURN _s;
END;
$$;

REVOKE ALL ON FUNCTION public.ensure_current_season() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.ensure_current_season() TO authenticated, service_role;

CREATE UNIQUE INDEX IF NOT EXISTS seasons_starts_at_key ON public.seasons (starts_at);

-- 3. Lifetime milestones ----------------------------------------------------
CREATE OR REPLACE FUNCTION public.evaluate_milestones(_user_id uuid)
RETURNS SETOF text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  _hours NUMERIC;
  _sessions INT;
  _best INT;
  _new TEXT;
BEGIN
  SELECT COALESCE(total_focus_seconds,0)/3600.0, COALESCE(best_streak,0)
    INTO _hours, _best FROM public.profiles WHERE id = _user_id;
  SELECT COUNT(*)::INT INTO _sessions FROM public.focus_history WHERE profile_id = _user_id;

  FOR _new IN
    WITH candidates(aid, ok) AS (
      VALUES
        ('ms_hours_100',  _hours >= 100),
        ('ms_hours_200',  _hours >= 200),
        ('ms_hours_300',  _hours >= 300),
        ('ms_hours_500',  _hours >= 500),
        ('ms_hours_1000', _hours >= 1000),
        ('ms_hours_2000', _hours >= 2000),
        ('ms_sessions_100',  _sessions >= 100),
        ('ms_sessions_500',  _sessions >= 500),
        ('ms_sessions_1000', _sessions >= 1000),
        ('ms_streak_50',  _best >= 50),
        ('ms_streak_100', _best >= 100)
    ), ins AS (
      INSERT INTO public.user_achievements (user_id, achievement_id)
      SELECT _user_id, aid FROM candidates
       WHERE ok AND EXISTS (SELECT 1 FROM public.achievements a WHERE a.id = aid)
      ON CONFLICT DO NOTHING
      RETURNING achievement_id
    )
    SELECT achievement_id FROM ins
  LOOP
    UPDATE public.profiles p
       SET lifetime_xp = lifetime_xp + a.xp_reward, updated_at = now()
      FROM public.achievements a
     WHERE a.id = _new AND p.id = _user_id;
    INSERT INTO public.activity_events (user_id, kind, payload)
    VALUES (_user_id, 'milestone_unlock', jsonb_build_object('id', _new));
    RETURN NEXT _new;
  END LOOP;
END;
$$;

REVOKE ALL ON FUNCTION public.evaluate_milestones(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.evaluate_milestones(uuid) TO authenticated, service_role;

-- 4. Composite focus personality -------------------------------------------
CREATE OR REPLACE FUNCTION public.refresh_personality(_user_id uuid)
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  _total INT;
  _avg_dur NUMERIC;
  _night INT; _early INT; _weekend INT;
  _days INT; _flow INT; _perfect INT;
  _streak INT;
  _traits TEXT[] := '{}';
  _label TEXT;
  _prev TEXT;
BEGIN
  SELECT COUNT(*)::INT, COALESCE(AVG(duration_seconds),0),
         COUNT(*) FILTER (WHERE EXTRACT(HOUR FROM created_at AT TIME ZONE 'UTC') >= 22
                            OR EXTRACT(HOUR FROM created_at AT TIME ZONE 'UTC') < 4)::INT,
         COUNT(*) FILTER (WHERE EXTRACT(HOUR FROM created_at AT TIME ZONE 'UTC') >= 4
                           AND EXTRACT(HOUR FROM created_at AT TIME ZONE 'UTC') < 9)::INT,
         COUNT(*) FILTER (WHERE EXTRACT(ISODOW FROM created_at AT TIME ZONE 'UTC') >= 6)::INT,
         COUNT(DISTINCT (created_at AT TIME ZONE 'UTC')::date)::INT,
         COUNT(*) FILTER (WHERE score >= 95)::INT,
         COUNT(*) FILTER (WHERE breaches_count = 0 AND duration_seconds >= 300)::INT
    INTO _total, _avg_dur, _night, _early, _weekend, _days, _flow, _perfect
    FROM public.focus_history
   WHERE profile_id = _user_id AND created_at > now() - INTERVAL '60 days';

  IF _total = 0 THEN RETURN NULL; END IF;
  SELECT COALESCE(current_focus_streak,0) INTO _streak FROM public.profiles WHERE id = _user_id;

  IF _avg_dur >= 3600 THEN _traits := _traits || 'Marathoner';
  ELSIF _avg_dur >= 1800 THEN _traits := _traits || 'Deep Worker';
  ELSIF _avg_dur > 0 AND _avg_dur < 900 THEN _traits := _traits || 'Sprint Specialist';
  END IF;

  IF _night::NUMERIC / _total >= 0.35 THEN _traits := _traits || 'Night Owl'; END IF;
  IF _early::NUMERIC / _total >= 0.35 THEN _traits := _traits || 'Early Bird'; END IF;
  IF _weekend::NUMERIC / _total >= 0.45 THEN _traits := _traits || 'Weekend Warrior'; END IF;
  IF _days >= 30 THEN _traits := _traits || 'Consistency Master'; END IF;
  IF _streak >= 7 THEN _traits := _traits || 'Streak Keeper'; END IF;
  IF _flow::NUMERIC / _total >= 0.3 THEN _traits := _traits || 'Flow Chaser'; END IF;
  IF _perfect::NUMERIC / _total >= 0.6 THEN _traits := _traits || 'Unbroken'; END IF;

  IF array_length(_traits,1) IS NULL THEN _traits := ARRAY['Getting Started']; END IF;
  _label := array_to_string(_traits[1:3], ' • ');

  SELECT productivity_dna INTO _prev FROM public.profiles WHERE id = _user_id;
  IF _prev IS DISTINCT FROM _label THEN
    UPDATE public.profiles SET productivity_dna = _label, updated_at = now() WHERE id = _user_id;
    INSERT INTO public.activity_events (user_id, kind, payload)
    VALUES (_user_id, 'personality_shift', jsonb_build_object('from', _prev, 'to', _label));
  END IF;
  RETURN _label;
END;
$$;

REVOKE ALL ON FUNCTION public.refresh_personality(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.refresh_personality(uuid) TO authenticated, service_role;

-- 5. finalize_focus_session: season XP + milestones + personality -----------
CREATE OR REPLACE FUNCTION public.finalize_focus_session(_room_id uuid, _score integer, _xp integer, _duration_seconds integer, _breaches_count integer, _tier text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
DECLARE
  _uid UUID := auth.uid();
  _history_id UUID;
  _room public.rooms%ROWTYPE;
  _target INTEGER;
  _duration INTEGER;
  _breaches INTEGER;
  _score_clamped INTEGER;
  _multiplier NUMERIC;
  _tier_key TEXT;
  _server_xp INTEGER;
  _accept_xp INTEGER;
  _new_streak INTEGER;
  _season public.seasons%ROWTYPE;
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  IF _room_id IS NULL THEN RAISE EXCEPTION 'room_required'; END IF;
  IF NOT EXISTS (SELECT 1 FROM public.participants WHERE room_id = _room_id AND user_id = _uid) THEN
    RAISE EXCEPTION 'not_participant';
  END IF;

  SELECT id INTO _history_id FROM public.focus_history WHERE profile_id = _uid AND room_id = _room_id;
  IF _history_id IS NOT NULL THEN RETURN _history_id; END IF;

  SELECT * INTO _room FROM public.rooms WHERE id = _room_id;
  _target := GREATEST(COALESCE(_room.target_duration_seconds, 1), 1);
  _duration := LEAST(GREATEST(COALESCE(_duration_seconds, 0), 0), _target);
  SELECT COUNT(*)::INT INTO _breaches FROM public.breaks WHERE room_id = _room_id AND user_id = _uid;
  _score_clamped := LEAST(GREATEST(COALESCE(_score, 0), 0), 100);

  IF _score_clamped >= 95 THEN _tier_key := 'flow';         _multiplier := 1.5;
  ELSIF _score_clamped >= 85 THEN _tier_key := 'pristine';   _multiplier := 1.0;
  ELSIF _score_clamped >= 70 THEN _tier_key := 'steady';     _multiplier := 0.5;
  ELSIF _score_clamped >= 40 THEN _tier_key := 'fragmented'; _multiplier := 0.0;
  ELSE                            _tier_key := 'compromised'; _multiplier := 0.0;
  END IF;

  _server_xp := FLOOR(_score_clamped * (_duration::NUMERIC / 60) * _multiplier)::INT;
  _accept_xp := GREATEST(LEAST(COALESCE(_xp, _server_xp), _server_xp), 0);

  INSERT INTO public.focus_history (profile_id, room_id, score, xp_earned, duration_seconds, breaches_count, tier)
  VALUES (_uid, _room_id, _score_clamped, _accept_xp, _duration, _breaches, _tier_key)
  RETURNING id INTO _history_id;

  _new_streak := CASE WHEN _breaches = 0 AND _score_clamped >= 70
                      THEN (SELECT current_focus_streak FROM public.profiles WHERE id = _uid) + 1
                      ELSE 0 END;

  UPDATE public.profiles
     SET lifetime_xp = lifetime_xp + _accept_xp,
         current_focus_streak = _new_streak,
         best_streak = GREATEST(best_streak, _new_streak),
         total_focus_seconds = total_focus_seconds + _duration,
         last_active_at = now(),
         updated_at = now()
   WHERE id = _uid;

  UPDATE public.focus_groups g SET total_group_xp = total_group_xp + _accept_xp, updated_at = now()
   WHERE g.id IN (SELECT group_id FROM public.group_members WHERE profile_id = _uid);

  -- Weekly season standings (permanent XP is untouched above).
  _season := public.ensure_current_season();
  IF _season.id IS NOT NULL AND _accept_xp > 0 THEN
    INSERT INTO public.season_participants (season_id, user_id, xp, updated_at)
    VALUES (_season.id, _uid, _accept_xp, now())
    ON CONFLICT (season_id, user_id) DO UPDATE
      SET xp = public.season_participants.xp + EXCLUDED.xp, updated_at = now();
  END IF;

  INSERT INTO public.activity_events (user_id, kind, payload)
  VALUES (_uid, 'session_complete', jsonb_build_object(
    'score', _score_clamped, 'tier', _tier_key,
    'duration_seconds', _duration, 'xp', _accept_xp, 'breaches', _breaches
  ));

  PERFORM public.evaluate_achievements(_uid, _history_id);
  PERFORM public.evaluate_challenges(_uid, _history_id);
  PERFORM public.evaluate_milestones(_uid);
  PERFORM public.refresh_personality(_uid);

  RETURN _history_id;
END;
$function$;

REVOKE ALL ON FUNCTION public.finalize_focus_session(uuid, integer, integer, integer, integer, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.finalize_focus_session(uuid, integer, integer, integer, integer, text) TO authenticated, service_role;

-- 6. Weekly rollover job ----------------------------------------------------
CREATE EXTENSION IF NOT EXISTS pg_cron;
DO $$
BEGIN
  PERFORM cron.unschedule('stackd-weekly-season');
EXCEPTION WHEN OTHERS THEN NULL;
END $$;
SELECT cron.schedule('stackd-weekly-season', '0 0 * * 1', $$ SELECT public.ensure_current_season(); $$);

-- ============================================================
-- 20260730024248_be0b06be-34cc-44b3-93fc-754459b3755b.sql
-- ============================================================
ALTER TABLE public.room_templates
  ADD COLUMN IF NOT EXISTS access TEXT NOT NULL DEFAULT 'public';

ALTER TABLE public.room_templates
  DROP CONSTRAINT IF EXISTS room_templates_access_check;
ALTER TABLE public.room_templates
  ADD CONSTRAINT room_templates_access_check CHECK (access IN ('public', 'restricted'));

DROP POLICY IF EXISTS "Templates readable by authenticated" ON public.room_templates;

CREATE POLICY "Public templates readable by authenticated"
  ON public.room_templates
  FOR SELECT
  TO authenticated
  USING (access = 'public');

-- ============================================================
-- 20260730030253_9d2ccea3-836a-49c9-b265-8c85cbd6ad4a.sql
-- ============================================================
DROP POLICY IF EXISTS "See any user's unlocks" ON public.user_achievements;
CREATE POLICY "Owner or friends can view unlocks" ON public.user_achievements
FOR SELECT TO authenticated
USING (auth.uid() = user_id OR public.are_friends(auth.uid(), user_id));

DROP POLICY IF EXISTS "user_titles public read" ON public.user_titles;
CREATE POLICY "Owner or friends can view titles" ON public.user_titles
FOR SELECT TO authenticated
USING (auth.uid() = user_id OR public.are_friends(auth.uid(), user_id));

-- ============================================================
-- 20260804013441_5848c869-b919-4ba9-9390-37fd0d1988c5.sql
-- ============================================================
DROP POLICY IF EXISTS "Anyone can view season participants" ON public.season_participants;
DROP POLICY IF EXISTS "Season standings are viewable by authenticated users" ON public.season_participants;
DROP POLICY IF EXISTS "season_participants_select" ON public.season_participants;
DROP POLICY IF EXISTS "Authenticated can view standings" ON public.season_participants;

CREATE POLICY "Users can view their own season standing"
ON public.season_participants
FOR SELECT
TO authenticated
USING (auth.uid() = user_id);

CREATE OR REPLACE FUNCTION public.season_standings(_season_id uuid, _limit integer DEFAULT 50)
RETURNS TABLE(user_id uuid, display_name text, avatar_url text, xp integer, rank integer)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO 'public'
AS $$
  SELECT sp.user_id,
         p.display_name,
         p.avatar_url,
         sp.xp,
         (ROW_NUMBER() OVER (ORDER BY sp.xp DESC, sp.user_id))::int AS rank
  FROM public.season_participants sp
  LEFT JOIN public.profiles p ON p.id = sp.user_id
  WHERE sp.season_id = _season_id
    AND auth.uid() IS NOT NULL
  ORDER BY sp.xp DESC, sp.user_id
  LIMIT LEAST(GREATEST(COALESCE(_limit, 50), 1), 100);
$$;

REVOKE ALL ON FUNCTION public.season_standings(uuid, integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.season_standings(uuid, integer) TO authenticated;

CREATE OR REPLACE FUNCTION public.my_season_rank(_season_id uuid)
RETURNS integer
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO 'public'
AS $$
  SELECT r.rank FROM (
    SELECT sp.user_id, (ROW_NUMBER() OVER (ORDER BY sp.xp DESC, sp.user_id))::int AS rank
    FROM public.season_participants sp
    WHERE sp.season_id = _season_id
  ) r
  WHERE r.user_id = auth.uid();
$$;

REVOKE ALL ON FUNCTION public.my_season_rank(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.my_season_rank(uuid) TO authenticated;

-- ============================================================
-- 20260804014620_a5d4d35f-ba57-4eb6-8ff2-d45e7ecbf2d1.sql
-- ============================================================
DROP POLICY IF EXISTS "season_participants public read" ON public.season_participants;

-- ============================================================
-- 20260804074750_fix_is_room_participant_left_at.sql
-- ============================================================
-- is_room_participant() never excluded departed members (left_at IS NOT NULL),
-- unlike the breaks/rooms/participants SELECT policies which filter left_at IS NULL
-- directly. room_events, room_milestones, room_moderators, and room_scheduled_events
-- all gate SELECT through this helper, so a user who left a room retained read
-- access to those tables for that room indefinitely. Require left_at IS NULL here too.
CREATE OR REPLACE FUNCTION public.is_room_participant(_room_id uuid, _user_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.participants
    WHERE room_id = _room_id AND user_id = _user_id AND left_at IS NULL
  );
$$;


-- ============================================================
-- 20260805145433_f9b733f8-0165-46ea-947c-944337d65039.sql
-- ============================================================
CREATE OR REPLACE FUNCTION public.is_room_participant(_room_id uuid, _user_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE SECURITY DEFINER
SET search_path TO 'public'
AS $function$
  SELECT EXISTS (
    SELECT 1 FROM public.participants
    WHERE room_id = _room_id
      AND user_id = _user_id
      AND left_at IS NULL
  );
$function$;

REVOKE ALL ON FUNCTION public.is_room_participant(uuid, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.is_room_participant(uuid, uuid) TO authenticated, service_role;

-- ============================================================
-- 20260805155534_f5711499-d3cd-4e3b-9094-5f02ab7614e6.sql
-- ============================================================
-- Internal-only SECURITY DEFINER helpers: revoke direct API execution.
REVOKE ALL ON FUNCTION public.check_and_record_hit(text, integer, integer) FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.recent_auth_failures(text, text, integer) FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.recent_auth_failures(text, text, integer, text) FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.record_auth_alert_if_new(text, text, integer, integer, jsonb) FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.enqueue_email(text, jsonb) FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.delete_email(text, bigint) FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.read_email_batch(text, integer, integer) FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.move_to_dlq(text, text, bigint, jsonb) FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.email_queue_dispatch() FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.email_queue_wake() FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.evaluate_achievements(uuid, uuid) FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.evaluate_challenges(uuid, uuid) FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.evaluate_milestones(uuid) FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.refresh_personality(uuid) FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.ensure_current_season() FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.handle_new_user() FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.friendship_accepted_activity() FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.rooms_add_collective_seconds() FROM anon, authenticated;

-- Trusted server-side callers keep access.
GRANT EXECUTE ON FUNCTION public.check_and_record_hit(text, integer, integer) TO service_role;
GRANT EXECUTE ON FUNCTION public.recent_auth_failures(text, text, integer) TO service_role;
GRANT EXECUTE ON FUNCTION public.recent_auth_failures(text, text, integer, text) TO service_role;
GRANT EXECUTE ON FUNCTION public.record_auth_alert_if_new(text, text, integer, integer, jsonb) TO service_role;
GRANT EXECUTE ON FUNCTION public.enqueue_email(text, jsonb) TO service_role;
GRANT EXECUTE ON FUNCTION public.delete_email(text, bigint) TO service_role;
GRANT EXECUTE ON FUNCTION public.read_email_batch(text, integer, integer) TO service_role;
GRANT EXECUTE ON FUNCTION public.move_to_dlq(text, text, bigint, jsonb) TO service_role;
GRANT EXECUTE ON FUNCTION public.email_queue_dispatch() TO service_role, postgres;
GRANT EXECUTE ON FUNCTION public.evaluate_achievements(uuid, uuid) TO service_role;
GRANT EXECUTE ON FUNCTION public.evaluate_challenges(uuid, uuid) TO service_role;
GRANT EXECUTE ON FUNCTION public.evaluate_milestones(uuid) TO service_role;
GRANT EXECUTE ON FUNCTION public.refresh_personality(uuid) TO service_role;
GRANT EXECUTE ON FUNCTION public.ensure_current_season() TO service_role;

-- ============================================================
-- 20260806075521_776b38ef-a733-4e65-bb0a-dc3feee92c1f.sql
-- ============================================================
CREATE OR REPLACE FUNCTION public.are_friends(_a uuid, _b uuid)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO 'public'
AS $function$
  SELECT CASE
    WHEN auth.uid() IS NOT NULL AND auth.uid() <> _a AND auth.uid() <> _b THEN FALSE
    ELSE EXISTS (
      SELECT 1 FROM public.friendships
      WHERE status='accepted' AND (
        (requester_id=_a AND addressee_id=_b) OR
        (requester_id=_b AND addressee_id=_a)
      )
    )
  END;
$function$;

CREATE OR REPLACE FUNCTION public.is_group_member(_group_id uuid, _user_id uuid)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO 'public'
AS $function$
  SELECT CASE
    WHEN auth.uid() IS NOT NULL AND auth.uid() <> _user_id THEN FALSE
    ELSE EXISTS (
      SELECT 1 FROM public.group_members
      WHERE group_id = _group_id AND profile_id = _user_id
    )
  END;
$function$;

CREATE OR REPLACE FUNCTION public.is_room_host(_room_id uuid, _user_id uuid)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO 'public'
AS $function$
  SELECT CASE
    WHEN auth.uid() IS NOT NULL AND auth.uid() <> _user_id THEN FALSE
    ELSE EXISTS (SELECT 1 FROM public.rooms WHERE id = _room_id AND host_id = _user_id)
  END;
$function$;

CREATE OR REPLACE FUNCTION public.is_room_moderator(_room_id uuid, _user_id uuid)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO 'public'
AS $function$
  SELECT CASE
    WHEN auth.uid() IS NOT NULL AND auth.uid() <> _user_id THEN FALSE
    ELSE (
      public.is_room_host(_room_id, _user_id)
      OR EXISTS (SELECT 1 FROM public.room_moderators WHERE room_id = _room_id AND user_id = _user_id)
    )
  END;
$function$;

CREATE OR REPLACE FUNCTION public.is_room_participant(_room_id uuid, _user_id uuid)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO 'public'
AS $function$
  SELECT CASE
    WHEN auth.uid() IS NOT NULL AND auth.uid() <> _user_id THEN FALSE
    ELSE EXISTS (
      SELECT 1 FROM public.participants
      WHERE room_id = _room_id AND user_id = _user_id AND left_at IS NULL
    )
  END;
$function$;

REVOKE ALL ON FUNCTION public.are_friends(uuid, uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.is_group_member(uuid, uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.is_room_host(uuid, uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.is_room_moderator(uuid, uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.is_room_participant(uuid, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.are_friends(uuid, uuid) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.is_group_member(uuid, uuid) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.is_room_host(uuid, uuid) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.is_room_moderator(uuid, uuid) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.is_room_participant(uuid, uuid) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.room_code_exists(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.room_code_exists(text) TO authenticated, service_role;

-- ============================================================
-- 20260806085153_7ecf93e6-dec4-4aed-ba77-105e8ea416ae.sql
-- ============================================================
REVOKE ALL ON FUNCTION public.room_code_exists(text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.room_code_exists(text) TO service_role;

-- ============================================================
-- 20260809180000_scoring_version.sql
-- ============================================================
-- Stamp every completed session with the scoring ruleset that produced it.
--
-- Without this, changing the formula silently reinterprets history: a
-- leaderboard compares scores computed under different rules as though they
-- were equivalent, and there is no way afterwards to tell which rows came from
-- which version. Recording it is trivial now and impossible retroactively.
--
-- Existing rows default to 1 (the ruleset in force before this migration).
-- Version 2 derives tier and XP from the unrounded score; version 1 rounded
-- first, so a raw 84.5 crossed the "pristine" boundary at 85 and doubled the
-- XP multiplier.

ALTER TABLE public.focus_history
  ADD COLUMN IF NOT EXISTS scoring_version SMALLINT NOT NULL DEFAULT 1;

COMMENT ON COLUMN public.focus_history.scoring_version IS
  'Focus-score ruleset that produced this row. See SCORING_VERSION in src/lib/focus-score.ts.';

-- Analytics and leaderboards will want to segment or exclude by version once
-- more than one exists in the table.
CREATE INDEX IF NOT EXISTS idx_focus_history_scoring_version
  ON public.focus_history (scoring_version);

-- finalize_focus_session already recomputes score, duration and breaches
-- server-side; it now records which ruleset it used. The parameter is
-- defaulted so existing callers keep working without a client change.
CREATE OR REPLACE FUNCTION public.finalize_focus_session(
  _room_id uuid,
  _score integer,
  _xp integer,
  _duration_seconds integer,
  _breaches_count integer,
  _tier text,
  _scoring_version smallint DEFAULT 2
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
  _uid UUID := auth.uid();
  _history_id UUID;
  _room public.rooms%ROWTYPE;
  _target INTEGER;
  _duration INTEGER;
  _breaches INTEGER;
  _score_clamped INTEGER;
  _multiplier NUMERIC;
  _tier_key TEXT;
  _server_xp INTEGER;
  _accept_xp INTEGER;
  _new_streak INTEGER;
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  IF _room_id IS NULL THEN RAISE EXCEPTION 'room_required'; END IF;

  IF NOT EXISTS (SELECT 1 FROM public.participants WHERE room_id = _room_id AND user_id = _uid) THEN
    RAISE EXCEPTION 'not_participant';
  END IF;

  SELECT id INTO _history_id FROM public.focus_history WHERE profile_id = _uid AND room_id = _room_id;
  IF _history_id IS NOT NULL THEN RETURN _history_id; END IF;

  SELECT * INTO _room FROM public.rooms WHERE id = _room_id;
  _target := GREATEST(COALESCE(_room.target_duration_seconds, 1), 1);

  _duration := LEAST(GREATEST(COALESCE(_duration_seconds, 0), 0), _target);
  SELECT COUNT(*)::INT INTO _breaches FROM public.breaks WHERE room_id = _room_id AND user_id = _uid;
  _score_clamped := LEAST(GREATEST(COALESCE(_score, 0), 0), 100);

  IF _score_clamped >= 95 THEN _tier_key := 'flow';         _multiplier := 1.5;
  ELSIF _score_clamped >= 85 THEN _tier_key := 'pristine';   _multiplier := 1.0;
  ELSIF _score_clamped >= 70 THEN _tier_key := 'steady';     _multiplier := 0.5;
  ELSIF _score_clamped >= 40 THEN _tier_key := 'fragmented'; _multiplier := 0.0;
  ELSE                            _tier_key := 'compromised'; _multiplier := 0.0;
  END IF;

  _server_xp := FLOOR(_score_clamped * (_duration::NUMERIC / 60) * _multiplier)::INT;
  _accept_xp := GREATEST(LEAST(COALESCE(_xp, _server_xp), _server_xp), 0);

  INSERT INTO public.focus_history (
    profile_id, room_id, score, xp_earned, duration_seconds, breaches_count, tier, scoring_version
  )
  VALUES (
    _uid, _room_id, _score_clamped, _accept_xp, _duration, _breaches, _tier_key,
    COALESCE(_scoring_version, 2)
  )
  RETURNING id INTO _history_id;

  _new_streak := CASE WHEN _breaches = 0 AND _score_clamped >= 70
                      THEN (SELECT current_focus_streak FROM public.profiles WHERE id = _uid) + 1
                      ELSE 0 END;

  UPDATE public.profiles
     SET lifetime_xp = lifetime_xp + _accept_xp,
         current_focus_streak = _new_streak,
         best_streak = GREATEST(best_streak, _new_streak),
         total_focus_seconds = total_focus_seconds + _duration,
         updated_at = now()
   WHERE id = _uid;

  UPDATE public.focus_groups g
     SET total_group_xp = total_group_xp + _accept_xp,
         updated_at = now()
   WHERE g.id IN (SELECT group_id FROM public.group_members WHERE profile_id = _uid);

  PERFORM public.evaluate_achievements(_uid, _history_id);

  RETURN _history_id;
END;
$function$;

-- Same privilege boundary as every other SECURITY DEFINER routine here:
-- reachable by a signed-in user, never by anon.
REVOKE ALL ON FUNCTION public.finalize_focus_session(uuid, integer, integer, integer, integer, text, smallint) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.finalize_focus_session(uuid, integer, integer, integer, integer, text, smallint) TO authenticated;


-- ============================================================
-- 20260809181000_server_owned_session_start.sql
-- ============================================================
-- Make the server the clock for session start.
--
-- Previously the host's browser wrote `started_at: new Date().toISOString()`
-- directly. Every score derives from that timestamp, so the single most
-- important input to the reward system came from an unverified client clock —
-- skewed, wrong, or deliberately backdated. Backdating the start inflates
-- elapsed focus time; post-dating it shortens the session while the timer
-- still reads full.
--
-- This also closes the "who may start a room" question: the RPC checks host
-- identity server-side rather than trusting the client to have checked.

CREATE OR REPLACE FUNCTION public.start_focus_session(_room_id uuid)
RETURNS TIMESTAMPTZ
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
  _uid UUID := auth.uid();
  _room public.rooms%ROWTYPE;
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;

  SELECT * INTO _room FROM public.rooms WHERE id = _room_id;
  IF _room.id IS NULL THEN RAISE EXCEPTION 'room_not_found'; END IF;

  -- Only the host starts the room. The client checked this too, but a check
  -- the client performs is a suggestion, not a rule.
  IF _room.host_id <> _uid THEN RAISE EXCEPTION 'not_host'; END IF;

  -- Idempotent: a double-tap, a retry after a dropped response, or two tabs
  -- must not restart a running session and reset everyone's elapsed time.
  IF _room.status <> 'lobby' THEN
    RETURN _room.started_at;
  END IF;

  UPDATE public.rooms
     SET status = 'active',
         -- now() is the database's clock, identical for every participant.
         started_at = now()
   WHERE id = _room_id;

  SELECT started_at INTO _room.started_at FROM public.rooms WHERE id = _room_id;
  RETURN _room.started_at;
END;
$function$;

REVOKE ALL ON FUNCTION public.start_focus_session(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.start_focus_session(uuid) TO authenticated;

COMMENT ON FUNCTION public.start_focus_session(uuid) IS
  'Host-only, idempotent session start. Sets started_at from the server clock so scoring cannot be manipulated by a client clock.';


-- ============================================================
-- 20260810000000_server_side_room_completion.sql
-- ============================================================
-- Finish rooms server-side so a closed browser cannot strand a session.
--
-- The room lifecycle depended entirely on a live client: whoever's tab was
-- open drove the transition to 'complete'. If the host closed the tab, lost
-- battery, or drove into a tunnel, the room stayed 'active' forever — it kept
-- appearing in dashboards as a live session, participants could not cleanly
-- rejoin, and nothing ever wrote the finalisation.
--
-- This adds a sweep that completes rooms whose target duration has elapsed,
-- independent of any browser being open. It does NOT score participants:
-- finalize_focus_session stays the single place a score is written, remains
-- idempotent per (profile, room), and still runs when a client reconnects.
-- The sweep only closes the room so the state is truthful.

CREATE OR REPLACE FUNCTION public.reap_stale_rooms()
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
  _closed INTEGER;
BEGIN
  -- A room is finished when its target duration has elapsed since started_at,
  -- plus a grace margin. The grace exists so a client that is merely slow to
  -- report — a backgrounded tab catching up, a reconnect in progress — gets to
  -- finish the job itself before the server steps in.
  WITH done AS (
    UPDATE public.rooms
       SET status = 'complete',
           ended_at = COALESCE(ended_at, now()),
           updated_at = now()
     WHERE status = 'active'
       AND started_at IS NOT NULL
       AND now() > started_at
                 + make_interval(secs => target_duration_seconds)
                 + INTERVAL '2 minutes'
    RETURNING id
  )
  SELECT COUNT(*)::INT INTO _closed FROM done;

  -- Rooms that were opened and never started are abandoned lobbies. Left
  -- alone they accumulate forever and clutter every room list.
  UPDATE public.rooms
     SET status = 'aborted',
         ended_at = COALESCE(ended_at, now()),
         updated_at = now()
   WHERE status = 'lobby'
     AND created_at < now() - INTERVAL '6 hours';

  RETURN _closed;
END;
$function$;

-- Called by cron only. No client has any reason to invoke this.
REVOKE ALL ON FUNCTION public.reap_stale_rooms() FROM PUBLIC, anon, authenticated;

COMMENT ON FUNCTION public.reap_stale_rooms() IS
  'Completes active rooms past their duration and aborts stale lobbies, so room state does not depend on a browser staying open. Does not score — finalize_focus_session remains the only writer of focus_history.';

CREATE EXTENSION IF NOT EXISTS pg_cron;

DO $$
BEGIN
  PERFORM cron.unschedule('stackd-reap-rooms');
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

-- Every 5 minutes: frequent enough that a stranded room resolves quickly,
-- infrequent enough to be invisible load.
SELECT cron.schedule('stackd-reap-rooms', '*/5 * * * *', $$ SELECT public.reap_stale_rooms(); $$);


-- ============================================================
-- 20260810001000_pin_search_path_email_fns.sql
-- ============================================================
-- Pin search_path on the four email-queue SECURITY DEFINER functions.
--
-- A SECURITY DEFINER function runs with its owner's privileges. Without a
-- fixed search_path, the schemas it resolves against are chosen by the
-- *caller*, so a caller who can create objects in a schema earlier on the path
-- can shadow `pgmq.send`, `pgmq.read` or `pgmq.delete` and have their own code
-- executed as the function's owner. That is the textbook privilege-escalation
-- route against SECURITY DEFINER routines.
--
-- Every other privileged routine in this schema already does this
-- (`SET search_path TO 'public'`); these four predate the convention. Bodies
-- are unchanged — only the search_path option is added.
--
-- EXECUTE remains revoked from anon/authenticated as set in the original
-- migration; these are service_role-only queue wrappers.

CREATE OR REPLACE FUNCTION public.enqueue_email(queue_name TEXT, payload JSONB)
RETURNS BIGINT
LANGUAGE plpgsql SECURITY DEFINER
SET search_path TO 'public', 'pgmq'
AS $$
BEGIN
  RETURN pgmq.send(queue_name, payload);
EXCEPTION WHEN undefined_table THEN
  PERFORM pgmq.create(queue_name);
  RETURN pgmq.send(queue_name, payload);
END;
$$;

CREATE OR REPLACE FUNCTION public.read_email_batch(queue_name TEXT, batch_size INT, vt INT)
RETURNS TABLE(msg_id BIGINT, read_ct INT, message JSONB)
LANGUAGE plpgsql SECURITY DEFINER
SET search_path TO 'public', 'pgmq'
AS $$
BEGIN
  RETURN QUERY SELECT r.msg_id, r.read_ct, r.message FROM pgmq.read(queue_name, vt, batch_size) r;
EXCEPTION WHEN undefined_table THEN
  PERFORM pgmq.create(queue_name);
  RETURN;
END;
$$;

CREATE OR REPLACE FUNCTION public.delete_email(queue_name TEXT, message_id BIGINT)
RETURNS BOOLEAN
LANGUAGE plpgsql SECURITY DEFINER
SET search_path TO 'public', 'pgmq'
AS $$
BEGIN
  RETURN pgmq.delete(queue_name, message_id);
EXCEPTION WHEN undefined_table THEN
  RETURN FALSE;
END;
$$;

CREATE OR REPLACE FUNCTION public.move_to_dlq(
  source_queue TEXT, dlq_name TEXT, message_id BIGINT, payload JSONB
)
RETURNS BIGINT
LANGUAGE plpgsql SECURITY DEFINER
SET search_path TO 'public', 'pgmq'
AS $$
DECLARE new_id BIGINT;
BEGIN
  SELECT pgmq.send(dlq_name, payload) INTO new_id;
  PERFORM pgmq.delete(source_queue, message_id);
  RETURN new_id;
EXCEPTION WHEN undefined_table THEN
  BEGIN
    PERFORM pgmq.create(dlq_name);
  EXCEPTION WHEN OTHERS THEN
    NULL;
  END;
  SELECT pgmq.send(dlq_name, payload) INTO new_id;
  BEGIN
    PERFORM pgmq.delete(source_queue, message_id);
  EXCEPTION WHEN undefined_table THEN
    NULL;
  END;
  RETURN new_id;
END;
$$;


-- ============================================================
-- 20260810040000_restrict_anon_reads.sql
-- ============================================================
-- Stop anonymous reads of profiles and rooms.
--
-- Both tables carried a SELECT policy targeting the `public` role. In Postgres,
-- `public` means every role — including `anon`, the role every unauthenticated
-- visitor gets via the publishable key that ships in the browser bundle. So
-- these were not "public" in the sense of "any signed-in user"; they were
-- readable by the open internet.
--
-- profiles: VERIFIED EXPLOITABLE before this migration. An unauthenticated
-- GET /rest/v1/profiles returned 200 with real display names, XP and streaks.
-- A duplicate `authenticated`-scoped policy already existed granting exactly
-- the intended access, so dropping the public one loses nothing.
--
-- rooms: the policy was equally misconfigured but happened not to be
-- exploitable, because `anon` lacks EXECUTE on is_room_participant() and the
-- query errored out before returning rows. That is an accident, not a control
-- — granting anon execute on that helper for any unrelated reason would
-- silently open every open-visibility room. The policy is rescoped so the
-- protection is intentional rather than incidental.

-- ---------------------------------------------------------------------------
-- profiles
-- ---------------------------------------------------------------------------

-- "Profiles are viewable by everyone" — USING (true) TO public.
DROP POLICY IF EXISTS "Profiles are viewable by everyone" ON public.profiles;

-- The surviving policy ("Authenticated can view basic profile fields",
-- USING true TO authenticated) already covers every legitimate reader: the
-- app only renders profiles for signed-in users.

-- ---------------------------------------------------------------------------
-- rooms
-- ---------------------------------------------------------------------------

DROP POLICY IF EXISTS "Host, participant, or open visibility can read room" ON public.rooms;

CREATE POLICY "Host, participant, or open visibility can read room"
  ON public.rooms
  FOR SELECT
  -- TO authenticated, not the implicit `public`. Same predicate as before;
  -- only the audience changes.
  TO authenticated
  USING (
    auth.uid() = host_id
    OR public.is_room_participant(id, auth.uid())
    OR visibility = 'open'
  );


-- ============================================================
-- 20260811010428_8d748380-8121-4e4c-9026-b02bd71f8909.sql
-- ============================================================
ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS username TEXT,
  ADD COLUMN IF NOT EXISTS username_canonical TEXT,
  ADD COLUMN IF NOT EXISTS username_changed_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS profiles_username_canonical_key
  ON public.profiles (username_canonical);

ALTER TABLE public.profiles
  DROP CONSTRAINT IF EXISTS profiles_username_format_chk;
ALTER TABLE public.profiles
  ADD CONSTRAINT profiles_username_format_chk
  CHECK (username IS NULL OR username ~ '^[A-Za-z][A-Za-z0-9_-]{2,19}$');

ALTER TABLE public.profiles
  DROP CONSTRAINT IF EXISTS profiles_username_canonical_chk;
ALTER TABLE public.profiles
  ADD CONSTRAINT profiles_username_canonical_chk
  CHECK (username_canonical IS NULL OR username_canonical ~ '^[a-z0-9]{3,20}$');

-- ============================================================
-- 20260812002416_b0916b22-6660-4a16-bfcb-cd729ba2a7ca.sql
-- ============================================================
CREATE TABLE public.moderation_list_versions (
  category TEXT PRIMARY KEY,
  version INTEGER NOT NULL DEFAULT 1,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
GRANT ALL ON public.moderation_list_versions TO service_role;
ALTER TABLE public.moderation_list_versions ENABLE ROW LEVEL SECURITY;

CREATE TABLE public.moderation_terms (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  category TEXT NOT NULL CHECK (category IN ('profanity','nsfw','slurs','reserved','impersonation')),
  term TEXT NOT NULL CHECK (term ~ '^[a-z0-9]{2,40}$'),
  match_mode TEXT NOT NULL DEFAULT 'word' CHECK (match_mode IN ('exact','word','substring')),
  list_version INTEGER NOT NULL DEFAULT 1,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (category, term)
);
CREATE INDEX moderation_terms_active_idx ON public.moderation_terms (active, category);
GRANT ALL ON public.moderation_terms TO service_role;
ALTER TABLE public.moderation_terms ENABLE ROW LEVEL SECURITY;
CREATE TRIGGER moderation_terms_updated_at BEFORE UPDATE ON public.moderation_terms
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TABLE public.moderation_allowlist (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  canonical TEXT NOT NULL UNIQUE CHECK (canonical ~ '^[a-z0-9]{2,40}$'),
  reason TEXT,
  list_version INTEGER NOT NULL DEFAULT 1,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
GRANT ALL ON public.moderation_allowlist TO service_role;
ALTER TABLE public.moderation_allowlist ENABLE ROW LEVEL SECURITY;
CREATE TRIGGER moderation_allowlist_updated_at BEFORE UPDATE ON public.moderation_allowlist
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TABLE public.username_moderation_events (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id UUID,
  attempted TEXT NOT NULL,
  canonical TEXT,
  decision TEXT NOT NULL CHECK (decision IN ('allowed','rejected','error')),
  reason TEXT,
  category TEXT,
  matched_term TEXT,
  match_mode TEXT,
  matched_form TEXT,
  confidence NUMERIC,
  list_version INTEGER,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX username_moderation_events_created_idx ON public.username_moderation_events (created_at DESC);
GRANT ALL ON public.username_moderation_events TO service_role;
ALTER TABLE public.username_moderation_events ENABLE ROW LEVEL SECURITY;

-- ============================================================
-- 20260813002936_6515c151-8568-4e44-882e-1fb5c8d93b0f.sql
-- ============================================================

-- 1. season_participants: block direct client XP writes
DROP POLICY IF EXISTS "season_participants owner write" ON public.season_participants;
DROP POLICY IF EXISTS "season_participants owner update" ON public.season_participants;
REVOKE INSERT, UPDATE ON public.season_participants FROM authenticated;
GRANT ALL ON public.season_participants TO service_role;

CREATE OR REPLACE FUNCTION public.join_season(_season_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE _uid uuid := auth.uid();
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  IF NOT EXISTS (SELECT 1 FROM public.seasons WHERE id = _season_id) THEN
    RAISE EXCEPTION 'season_not_found';
  END IF;
  INSERT INTO public.season_participants (season_id, user_id, xp)
  VALUES (_season_id, _uid, 0)
  ON CONFLICT (season_id, user_id) DO NOTHING;
END;
$$;
REVOKE ALL ON FUNCTION public.join_season(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.join_season(uuid) TO authenticated;

-- 2. participants: freeze scoring/breach columns against direct client updates
CREATE OR REPLACE FUNCTION public.participants_protect_scoring()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
BEGIN
  IF current_user IN ('authenticated', 'anon') THEN
    NEW.integrity := OLD.integrity;
    NEW.breached := OLD.breached;
    NEW.breach_reason := OLD.breach_reason;
    NEW.breach_at := OLD.breach_at;
    NEW.user_id := OLD.user_id;
    NEW.room_id := OLD.room_id;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS participants_protect_scoring ON public.participants;
CREATE TRIGGER participants_protect_scoring
BEFORE UPDATE ON public.participants
FOR EACH ROW EXECUTE FUNCTION public.participants_protect_scoring();

-- 3. mentor_relationships: consent-based pairing
ALTER TABLE public.mentor_relationships ALTER COLUMN status SET DEFAULT 'pending';

DROP POLICY IF EXISTS "mentorship writable by either party" ON public.mentor_relationships;

CREATE POLICY "mentorship insert as pending by either party"
ON public.mentor_relationships FOR INSERT TO authenticated
WITH CHECK (
  (auth.uid() = mentor_id OR auth.uid() = mentee_id)
  AND mentor_id <> mentee_id
  AND status = 'pending'
);

-- Only the invited party (the one who did not create the row) can activate it.
CREATE POLICY "mentorship accept or decline by counterparty"
ON public.mentor_relationships FOR UPDATE TO authenticated
USING (auth.uid() = mentor_id OR auth.uid() = mentee_id)
WITH CHECK (
  (auth.uid() = mentor_id OR auth.uid() = mentee_id)
  AND status IN ('pending', 'active', 'declined')
);

CREATE POLICY "mentorship delete by either party"
ON public.mentor_relationships FOR DELETE TO authenticated
USING (auth.uid() = mentor_id OR auth.uid() = mentee_id);

CREATE OR REPLACE FUNCTION public.mentorship_guard()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE _uid uuid := auth.uid();
BEGIN
  IF current_user NOT IN ('authenticated', 'anon') THEN RETURN NEW; END IF;
  IF TG_OP = 'INSERT' THEN
    NEW.status := 'pending';
    RETURN NEW;
  END IF;
  -- Activation requires the counterparty (the invitee) to act.
  IF OLD.status = 'pending' AND NEW.status = 'active' THEN
    IF _uid = OLD.created_by_uid_placeholder() THEN
      RAISE EXCEPTION 'inviter_cannot_accept';
    END IF;
  END IF;
  NEW.mentor_id := OLD.mentor_id;
  NEW.mentee_id := OLD.mentee_id;
  RETURN NEW;
END;
$$;


-- ============================================================
-- 20260813003013_b1fe80b8-7e36-4146-b2ad-b2c350c2a3eb.sql
-- ============================================================

DROP FUNCTION IF EXISTS public.mentorship_guard();

ALTER TABLE public.mentor_relationships
  ADD COLUMN IF NOT EXISTS initiator_id uuid;

UPDATE public.mentor_relationships SET initiator_id = mentor_id WHERE initiator_id IS NULL;

DROP POLICY IF EXISTS "mentorship insert as pending by either party" ON public.mentor_relationships;
CREATE POLICY "mentorship insert as pending by either party"
ON public.mentor_relationships FOR INSERT TO authenticated
WITH CHECK (
  (auth.uid() = mentor_id OR auth.uid() = mentee_id)
  AND mentor_id <> mentee_id
  AND status = 'pending'
  AND initiator_id = auth.uid()
);

DROP POLICY IF EXISTS "mentorship accept or decline by counterparty" ON public.mentor_relationships;
CREATE POLICY "mentorship accept or decline by counterparty"
ON public.mentor_relationships FOR UPDATE TO authenticated
USING (
  (auth.uid() = mentor_id OR auth.uid() = mentee_id)
  AND auth.uid() <> initiator_id
)
WITH CHECK (
  (auth.uid() = mentor_id OR auth.uid() = mentee_id)
  AND auth.uid() <> initiator_id
  AND status IN ('active', 'declined')
);

CREATE OR REPLACE FUNCTION public.mentorship_freeze_parties()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
BEGIN
  IF current_user IN ('authenticated', 'anon') THEN
    NEW.mentor_id := OLD.mentor_id;
    NEW.mentee_id := OLD.mentee_id;
    NEW.initiator_id := OLD.initiator_id;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS mentorship_freeze_parties ON public.mentor_relationships;
CREATE TRIGGER mentorship_freeze_parties
BEFORE UPDATE ON public.mentor_relationships
FOR EACH ROW EXECUTE FUNCTION public.mentorship_freeze_parties();


-- ============================================================
-- 20260813004353_f684c8d4-cd7f-47f4-a10b-02ee164d91de.sql
-- ============================================================
-- 1. Internal trigger helpers must not be callable through the public API.
REVOKE ALL ON FUNCTION public.participants_protect_scoring() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.mentorship_freeze_parties() FROM PUBLIC, anon, authenticated;

-- 2. Harden participant anti-cheat columns against direct client writes.
CREATE OR REPLACE FUNCTION public.participants_protect_scoring()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
BEGIN
  -- Trusted server-side paths run as the table owner / service_role and are
  -- allowed to write scoring fields. End-user roles are not.
  IF current_user IN ('authenticated', 'anon') THEN
    IF NEW.integrity IS DISTINCT FROM OLD.integrity
       OR NEW.breached IS DISTINCT FROM OLD.breached
       OR NEW.breach_reason IS DISTINCT FROM OLD.breach_reason
       OR NEW.breach_at IS DISTINCT FROM OLD.breach_at
       OR NEW.user_id IS DISTINCT FROM OLD.user_id
       OR NEW.room_id IS DISTINCT FROM OLD.room_id THEN
      RAISE EXCEPTION 'scoring fields are read-only; use record_breach/finalize_focus_session'
        USING ERRCODE = '42501';
    END IF;
  END IF;
  RETURN NEW;
END;
$function$;

REVOKE ALL ON FUNCTION public.participants_protect_scoring() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS participants_protect_scoring ON public.participants;
CREATE TRIGGER participants_protect_scoring
  BEFORE UPDATE ON public.participants
  FOR EACH ROW EXECUTE FUNCTION public.participants_protect_scoring();
