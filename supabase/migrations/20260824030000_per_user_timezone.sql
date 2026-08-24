-- =========================================================
-- P1 #16: per-user timezone — day/hour boundaries stop being UTC-only
-- =========================================================
-- Streak/daily-reward/challenge day boundaries and DNA hour buckets were all
-- computed with `... AT TIME ZONE 'UTC'`. For a non-UTC user the "day" rolls
-- over at the wrong local wall-clock time: an IST user claiming the daily
-- reward at 11pm local and again at 6am local looks like two different UTC
-- days at some offsets and the same one at others; a 9pm-IST session counts as
-- 15:30 UTC and never registers as "Night Owl".
--
-- Worse, the two paths that compute the same concept disagreed: the DNA screen
-- (getProductivityDna server fn) already bucketed hours/days in the user's
-- IANA timezone, while refresh_personality — which writes the visible
-- productivity_dna label — still used UTC. Same user, two answers.
--
-- Fix: one source of truth. profiles.timezone (IANA name, default 'UTC') is set
-- by the client and read by every day/hour computation. A helper resolves it
-- with a safe fallback so a bad/blank value never makes AT TIME ZONE raise.
--
-- Idempotent: ADD COLUMN IF NOT EXISTS, CREATE OR REPLACE.

-- 1) The column. IANA zone name; 'UTC' until the client reports otherwise.
ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS timezone text NOT NULL DEFAULT 'UTC';

-- 2) Resolver: the user's zone, or 'UTC' if unset or not a real IANA zone.
--    Guards AT TIME ZONE against a garbage value ever throwing at read time.
CREATE OR REPLACE FUNCTION public.user_timezone(_user_id uuid)
RETURNS text
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT COALESCE(
    (SELECT p.timezone
       FROM public.profiles p
       JOIN pg_timezone_names z ON z.name = p.timezone
      WHERE p.id = _user_id),
    'UTC'
  );
$$;
REVOKE ALL ON FUNCTION public.user_timezone(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.user_timezone(uuid) TO authenticated, service_role;

-- 3) Setter the client calls (via set_my_timezone RPC). Validates against
--    pg_timezone_names so only a real zone lands in the column.
CREATE OR REPLACE FUNCTION public.set_my_timezone(_tz text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  _uid uuid := auth.uid();
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  IF _tz IS NULL OR NOT EXISTS (SELECT 1 FROM pg_timezone_names WHERE name = _tz) THEN
    RAISE EXCEPTION 'invalid_timezone' USING ERRCODE = 'check_violation';
  END IF;
  UPDATE public.profiles SET timezone = _tz, updated_at = now() WHERE id = _uid;
END;
$$;
REVOKE ALL ON FUNCTION public.set_my_timezone(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.set_my_timezone(text) TO authenticated;

-- 4) claim_daily_reward: day boundary in the user's zone.
CREATE OR REPLACE FUNCTION public.claim_daily_reward()
RETURNS TABLE(reward_xp INT, new_streak INT, day_of_streak INT)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE
  _uid UUID := auth.uid();
  _tz  TEXT := public.user_timezone(auth.uid());
  _today DATE := (now() AT TIME ZONE _tz)::DATE;
  _row public.login_streaks%ROWTYPE;
  _new_streak INT;
  _reward INT;
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  SELECT * INTO _row FROM public.login_streaks WHERE user_id = _uid FOR UPDATE;
  IF _row.user_id IS NULL THEN
    _new_streak := 1;
    INSERT INTO public.login_streaks(user_id, streak, last_claim_date, total_claims, updated_at)
      VALUES (_uid, 1, _today, 1, now());
  ELSE
    IF _row.last_claim_date = _today THEN RAISE EXCEPTION 'already_claimed'; END IF;
    IF _row.last_claim_date = _today - 1 THEN _new_streak := _row.streak + 1;
    ELSE _new_streak := 1; END IF;
    UPDATE public.login_streaks
      SET streak = _new_streak, last_claim_date = _today,
          total_claims = _row.total_claims + 1, updated_at = now()
      WHERE user_id = _uid;
  END IF;
  _reward := CASE ((_new_streak - 1) % 7)
    WHEN 0 THEN 10 WHEN 1 THEN 20 WHEN 2 THEN 40
    WHEN 3 THEN 60 WHEN 4 THEN 80 WHEN 5 THEN 100
    ELSE 200 END;
  UPDATE public.profiles SET lifetime_xp = lifetime_xp + _reward, updated_at = now()
    WHERE id = _uid;
  INSERT INTO public.activity_events(user_id, kind, payload)
    VALUES (_uid, 'daily_reward', jsonb_build_object('xp', _reward, 'streak', _new_streak));
  reward_xp := _reward; new_streak := _new_streak; day_of_streak := ((_new_streak - 1) % 7) + 1;
  RETURN NEXT;
END;
$$;
REVOKE ALL ON FUNCTION public.claim_daily_reward() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.claim_daily_reward() TO authenticated;

-- 5) evaluate_challenges: daily/weekly period bucket in the user's zone.
CREATE OR REPLACE FUNCTION public.evaluate_challenges(_user_id UUID, _history_id UUID)
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE
  _h public.focus_history%ROWTYPE;
  _today DATE := (now() AT TIME ZONE public.user_timezone(_user_id))::DATE;
  _week_start DATE := date_trunc('week', now() AT TIME ZONE public.user_timezone(_user_id))::DATE;
  _c public.challenges%ROWTYPE;
  _period_start DATE;
  _increment INT;
  _row public.challenge_progress%ROWTYPE;
BEGIN
  SELECT * INTO _h FROM public.focus_history WHERE id = _history_id AND profile_id = _user_id;
  IF _h.id IS NULL THEN RETURN; END IF;

  FOR _c IN SELECT * FROM public.challenges LOOP
    _period_start := CASE WHEN _c.cadence = 'daily' THEN _today ELSE _week_start END;
    _increment := CASE _c.metric
      WHEN 'sessions'          THEN 1
      WHEN 'focus_minutes'     THEN GREATEST((_h.duration_seconds / 60), 0)
      WHEN 'perfect_sessions'  THEN CASE WHEN _h.breaches_count = 0 AND _h.duration_seconds >= 300 THEN 1 ELSE 0 END
      WHEN 'flow_sessions'     THEN CASE WHEN _h.score >= 95 THEN 1 ELSE 0 END
      ELSE 0
    END;
    IF _increment <= 0 THEN CONTINUE; END IF;

    INSERT INTO public.challenge_progress (user_id, challenge_id, period_start, progress)
    VALUES (_user_id, _c.id, _period_start, _increment)
    ON CONFLICT (user_id, challenge_id, period_start) DO UPDATE
      SET progress = public.challenge_progress.progress + EXCLUDED.progress,
          updated_at = now()
    RETURNING * INTO _row;

    IF _row.completed_at IS NULL AND _row.progress >= _c.target THEN
      UPDATE public.challenge_progress
         SET completed_at = now()
       WHERE user_id = _user_id AND challenge_id = _c.id AND period_start = _period_start;
      UPDATE public.profiles SET lifetime_xp = lifetime_xp + _c.xp_reward, updated_at = now()
       WHERE id = _user_id;
      INSERT INTO public.activity_events (user_id, kind, payload)
      VALUES (_user_id, 'challenge_complete', jsonb_build_object('id', _c.id, 'name', _c.name, 'xp', _c.xp_reward));
    END IF;
  END LOOP;
END;
$$;
REVOKE ALL ON FUNCTION public.evaluate_challenges(UUID,UUID) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.evaluate_challenges(UUID,UUID) TO service_role;

-- 6) refresh_personality: hour buckets, weekend, distinct-days in user's zone.
--    This is the path that disagreed with the DNA screen; now both use the
--    stored zone.
CREATE OR REPLACE FUNCTION public.refresh_personality(_user_id uuid)
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  _tz TEXT := public.user_timezone(_user_id);
  _total INT;
  _avg_dur NUMERIC;
  _night INT; _early INT; _weekend INT;
  _days INT; _flow INT; _perfect INT;
  _streak INT;
  _traits TEXT[] := '{}';
  _label TEXT;
  _prev TEXT;
BEGIN
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
