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
