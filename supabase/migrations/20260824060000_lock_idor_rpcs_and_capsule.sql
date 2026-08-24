-- =========================================================
-- P0/P1 (Codex #14,#15,#16,#19): lock IDOR-able RPCs, hide block enumeration,
-- protect time-capsule lifecycle columns
-- =========================================================

-- #14: refresh_personality(_user_id) was EXECUTE-granted to authenticated with
-- an arbitrary uuid and no ownership check — a user could recompute/overwrite
-- another user's productivity_dna and emit activity events on their behalf.
-- Restrict to the caller's own id (the finalize/service flows pass auth.uid()
-- anyway). Keep service_role for server-side batch use.
CREATE OR REPLACE FUNCTION public.refresh_personality(_user_id uuid)
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  _tz TEXT;
  _total INT;
  _avg_dur NUMERIC;
  _night INT; _early INT; _weekend INT;
  _days INT; _flow INT; _perfect INT;
  _streak INT;
  _traits TEXT[] := '{}';
  _label TEXT;
  _prev TEXT;
BEGIN
  -- IDOR guard: a client may only refresh its own personality. service_role
  -- (current_user is the table owner, not 'authenticated') may target anyone.
  IF current_user IN ('authenticated', 'anon') AND _user_id IS DISTINCT FROM auth.uid() THEN
    RAISE EXCEPTION 'forbidden' USING ERRCODE = 'insufficient_privilege';
  END IF;

  _tz := public.user_timezone(_user_id);
  SELECT COUNT(*)::INT, COALESCE(AVG(duration_seconds),0),
         COUNT(*) FILTER (WHERE EXTRACT(HOUR FROM created_at AT TIME ZONE _tz) >= 22
                            OR EXTRACT(HOUR FROM created_at AT TIME ZONE _tz) < 4)::INT,
         COUNT(*) FILTER (WHERE EXTRACT(HOUR FROM created_at AT TIME ZONE _tz) >= 4
                           AND EXTRACT(HOUR FROM created_at AT TIME ZONE _tz) < 9)::INT,
         COUNT(*) FILTER (WHERE EXTRACT(ISODOW FROM created_at AT TIME ZONE _tz) >= 6)::INT,
         COUNT(DISTINCT (created_at AT TIME ZONE _tz)::date)::INT,
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

-- #15: user_timezone(_user_id) leaked any user's timezone to any caller. Add
-- the same self-or-trusted guard. (Low sensitivity, but no reason to expose it.)
CREATE OR REPLACE FUNCTION public.user_timezone(_user_id uuid)
RETURNS text
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF current_user IN ('authenticated', 'anon') AND _user_id IS DISTINCT FROM auth.uid() THEN
    RAISE EXCEPTION 'forbidden' USING ERRCODE = 'insufficient_privilege';
  END IF;
  RETURN COALESCE(
    (SELECT p.timezone
       FROM public.profiles p
       JOIN pg_timezone_names z ON z.name = p.timezone
      WHERE p.id = _user_id),
    'UTC'
  );
END;
$$;
REVOKE ALL ON FUNCTION public.user_timezone(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.user_timezone(uuid) TO authenticated, service_role;

-- #16: blocks_exist(_a,_b) let any authenticated user probe whether any two
-- users have blocked each other. It only needs to be callable by the trigger
-- guards (which run as SECURITY DEFINER / table owner) — never directly by a
-- client. Revoke from authenticated; the block-guard triggers still call it
-- because they execute as the owner.
REVOKE ALL ON FUNCTION public.blocks_exist(uuid, uuid) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.blocks_exist(uuid, uuid) TO service_role;

-- #19: time_capsules is Elite FOR ALL, so a client could directly UPDATE
-- open_at into the past (unlocking early) or forge opened_at, bypassing
-- openCapsule()'s time check. Lock the lifecycle columns: clients may only
-- write the content column (message) and the create-time open_at is set on
-- INSERT. Reveal (opened_at) goes through a SECURITY DEFINER RPC below.
REVOKE UPDATE ON public.time_capsules FROM authenticated, anon;
-- No GRANT UPDATE: capsules are immutable after creation from the client's
-- side; opening is server-only via open_capsule().

CREATE OR REPLACE FUNCTION public.open_capsule(_id uuid)
RETURNS timestamptz
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  _uid uuid := auth.uid();
  _open_at timestamptz;
  _opened timestamptz;
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  SELECT open_at, opened_at INTO _open_at, _opened
    FROM public.time_capsules WHERE id = _id AND user_id = _uid;
  IF _open_at IS NULL THEN RAISE EXCEPTION 'not_found' USING ERRCODE = 'no_data_found'; END IF;
  IF _opened IS NOT NULL THEN RETURN _opened; END IF;      -- already opened
  IF _open_at > now() THEN RAISE EXCEPTION 'not_yet' USING ERRCODE = 'check_violation'; END IF;
  UPDATE public.time_capsules SET opened_at = now() WHERE id = _id AND user_id = _uid;
  RETURN now();
END;
$$;
REVOKE ALL ON FUNCTION public.open_capsule(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.open_capsule(uuid) TO authenticated;
