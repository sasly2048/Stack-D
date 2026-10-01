-- =========================================================
-- Lobby "ready" presence: let room members record ready/unready
-- =========================================================
-- 20260825010000_restrict_room_event_kinds limited plain members to
-- `_member_ok := ARRAY['reaction']`. Both clients (web + Android) emit
-- 'ready' / 'unready' from the lobby toggle, so every such write has been
-- rejected with unknown_event_kind since then: the ready indicator only ever
-- updated on the device that tapped it and never reached other members.
--
-- 'ready' / 'unready' are self-describing presence signals about the caller —
-- not authoritative lifecycle or moderation events — so they belong on the
-- member allowlist. They are already permitted by room_events_kind_check
-- (20260730022718). Privileged kinds stay host/moderator-only, unchanged.
--
-- Idempotent: CREATE OR REPLACE with the previous body, only _member_ok grows.

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
  -- Benign events any room member may record about themselves.
  _member_ok CONSTANT TEXT[] := ARRAY['reaction', 'ready', 'unready'];
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
