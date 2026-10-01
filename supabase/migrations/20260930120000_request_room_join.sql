-- Request-to-join for 'request'-visibility rooms.
--
-- rooms SELECT RLS only exposes host/participant/'open' rooms, so a would-be
-- requester could never resolve a 'request' room's id: web requestToJoinRoom
-- always failed with not_found and claim_room_seat's needs_approval was a dead
-- end on both clients. This definer RPC resolves the room by code and files the
-- request in one step.
--
-- Returns the caller's request state:
--   'open'     not a request room (or caller is host) — just claim_room_seat
--   'pending'  request filed / already waiting
--   'approved' host approved — claim_room_seat will now succeed
--   'denied'   host declined (room_join_request_guard forbids the requester
--              reopening a denied request, so it stays denied)
CREATE OR REPLACE FUNCTION public.request_room_join(_code text, _message text DEFAULT NULL)
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE
  _uid uuid := auth.uid();
  _room public.rooms%ROWTYPE;
  _existing text;
  _name text;
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  IF _code IS NULL OR length(_code) <> 6 THEN RAISE EXCEPTION 'bad_code'; END IF;

  SELECT * INTO _room FROM public.rooms WHERE code = upper(_code);
  IF _room.id IS NULL OR _room.status IN ('aborted', 'complete') THEN
    RAISE EXCEPTION 'not_found';
  END IF;
  IF _room.visibility <> 'request' OR _uid = _room.host_id THEN
    RETURN 'open';
  END IF;
  IF public.blocks_exist(_uid, _room.host_id) THEN
    RAISE EXCEPTION 'blocked' USING ERRCODE = 'check_violation';
  END IF;

  SELECT status INTO _existing
    FROM public.room_join_requests
   WHERE room_id = _room.id AND user_id = _uid;
  IF _existing IN ('pending', 'approved', 'denied') THEN
    RETURN _existing;
  END IF;
  IF _existing = 'cancelled' THEN
    DELETE FROM public.room_join_requests WHERE room_id = _room.id AND user_id = _uid;
  END IF;

  SELECT COALESCE(display_name, 'Anon') INTO _name FROM public.profiles WHERE id = _uid;
  _name := COALESCE(_name, 'Anon');

  INSERT INTO public.room_join_requests (room_id, user_id, display_name, message, status)
  VALUES (_room.id, _uid, _name, left(NULLIF(btrim(_message), ''), 280), 'pending');

  INSERT INTO public.room_events (room_id, actor_id, actor_name, kind, payload)
  VALUES (_room.id, _uid, _name, 'join_requested', jsonb_build_object('requester_id', _uid));

  RETURN 'pending';
END;
$$;

REVOKE ALL ON FUNCTION public.request_room_join(text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.request_room_join(text, text) TO authenticated;
