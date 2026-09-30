-- =========================================================
-- P1 (Codex #30): claim_room_seat rejects a blocked co-membership
-- =========================================================
-- Blocking is meant to prevent interaction, but claim_room_seat let a user join
-- a room that already contains someone they've blocked (or who blocked them),
-- forcing co-presence in a live focus session. Add a block gate: you cannot
-- take a seat if a block exists (either direction) between you and the host or
-- any current participant.
--
-- Tradeoff (accepted): this is first-in-room-wins — whoever is already seated
-- keeps the room; the blocked party is turned away. A block is a strong signal,
-- so erring toward separation is the intended behavior.
--
-- claim_room_seat is SECURITY DEFINER owned by postgres, so it may call
-- blocks_exist() (also owner-executable) even though blocks_exist is revoked
-- from clients.
--
-- Idempotent: CREATE OR REPLACE.

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

  -- Block gate: refuse if a block exists (either direction) between the joiner
  -- and the host or any already-seated participant. Skip for the host joining
  -- their own room.
  IF _uid <> _room.host_id THEN
    IF public.blocks_exist(_uid, _room.host_id) THEN
      RAISE EXCEPTION 'blocked' USING ERRCODE = 'check_violation';
    END IF;
    IF EXISTS (
      SELECT 1 FROM public.participants p
      WHERE p.room_id = _room.id
        AND p.user_id <> _uid
        AND public.blocks_exist(_uid, p.user_id)
    ) THEN
      RAISE EXCEPTION 'blocked' USING ERRCODE = 'check_violation';
    END IF;
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
