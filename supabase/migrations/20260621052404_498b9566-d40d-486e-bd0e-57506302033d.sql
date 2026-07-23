
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
