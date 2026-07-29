
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
