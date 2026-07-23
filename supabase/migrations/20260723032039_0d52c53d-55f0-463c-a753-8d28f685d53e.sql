
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
