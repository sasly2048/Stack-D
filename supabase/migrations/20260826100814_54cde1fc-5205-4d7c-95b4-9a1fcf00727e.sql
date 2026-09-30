CREATE OR REPLACE FUNCTION public.finish_focus_room(
  _room_id uuid,
  _outcome public.room_status
)
RETURNS public.rooms
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE
  _uid uuid := auth.uid();
  _room public.rooms%ROWTYPE;
BEGIN
  IF _uid IS NULL THEN
    RAISE EXCEPTION 'not_authenticated';
  END IF;

  IF _room_id IS NULL OR _outcome NOT IN ('complete'::public.room_status, 'aborted'::public.room_status) THEN
    RAISE EXCEPTION 'invalid_outcome' USING ERRCODE = 'check_violation';
  END IF;

  SELECT * INTO _room
  FROM public.rooms
  WHERE id = _room_id
  FOR UPDATE;

  IF _room.id IS NULL THEN
    RAISE EXCEPTION 'not_found' USING ERRCODE = 'no_data_found';
  END IF;

  IF _room.host_id <> _uid THEN
    RAISE EXCEPTION 'not_host' USING ERRCODE = 'insufficient_privilege';
  END IF;

  IF _room.status IN ('complete', 'aborted') THEN
    RETURN _room;
  END IF;

  IF _outcome = 'complete' AND _room.status <> 'active' THEN
    RAISE EXCEPTION 'not_active' USING ERRCODE = 'check_violation';
  END IF;

  UPDATE public.rooms
  SET status = _outcome,
      ended_at = now(),
      updated_at = now()
  WHERE id = _room_id
  RETURNING * INTO _room;

  RETURN _room;
END;
$$;

REVOKE ALL ON FUNCTION public.finish_focus_room(uuid, public.room_status) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.finish_focus_room(uuid, public.room_status) TO authenticated;