-- =========================================================
-- P0 (Codex #17): restrict record_room_event kinds by role
-- =========================================================
-- record_room_event(_room_id,_kind,_payload) let ANY room participant emit ANY
-- kind — including authoritative system/audit events like 'moderator_added',
-- 'join_approved', 'completed', 'started', 'goal_hit'. A plain participant could
-- forge a moderator-looking or lifecycle-looking audit trail.
--
-- Every real caller in the app emits a PRIVILEGED kind and is already gated on
-- host/moderator in the server function (pin message, grant/revoke mod, approve/
-- deny join). No caller emits a participant-level kind. So:
--   * privileged kinds  -> require host or moderator
--   * an explicit allowlist of participant-safe kinds -> any room member
--   * anything else      -> rejected (no free-form kinds)
--
-- This closes the forgery vector at the database, covering every client.
--
-- Idempotent: CREATE OR REPLACE.

CREATE OR REPLACE FUNCTION public.record_room_event(
  _room_id UUID, _kind TEXT, _payload JSONB DEFAULT '{}'::JSONB
) RETURNS UUID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE
  _uid UUID := auth.uid();
  _name TEXT;
  _id UUID;
  -- Authoritative events only a host/moderator (or the server) may record.
  _privileged CONSTANT TEXT[] := ARRAY[
    'moderator_added', 'moderator_removed', 'join_approved', 'join_denied',
    'pinned', 'started', 'completed', 'ended', 'goal_hit', 'aborted'
  ];
  -- Benign events any room member may record. (None today, but this makes the
  -- allowlist explicit so a new participant-level event is a deliberate add.)
  _member_ok CONSTANT TEXT[] := ARRAY['reaction'];
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;

  -- Must at least be in the room.
  IF NOT (public.is_room_host(_room_id, _uid) OR public.is_room_participant(_room_id, _uid)) THEN
    RAISE EXCEPTION 'not_room_member';
  END IF;

  IF _kind = ANY(_privileged) THEN
    IF NOT (public.is_room_host(_room_id, _uid) OR public.is_room_moderator(_room_id, _uid)) THEN
      RAISE EXCEPTION 'not_authorized_for_event_kind' USING ERRCODE = 'insufficient_privilege';
    END IF;
  ELSIF NOT (_kind = ANY(_member_ok)) THEN
    -- Unknown / free-form kinds are rejected outright.
    RAISE EXCEPTION 'unknown_event_kind' USING ERRCODE = 'check_violation';
  END IF;

  SELECT display_name INTO _name FROM public.profiles WHERE id = _uid;
  INSERT INTO public.room_events (room_id, actor_id, actor_name, kind, payload)
  VALUES (_room_id, _uid, COALESCE(_name, 'Anon'), _kind, COALESCE(_payload, '{}'::JSONB))
  RETURNING id INTO _id;
  RETURN _id;
END;
$$;
