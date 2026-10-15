-- =========================================================
-- Daily streak + earned streak freeze (UI/UX audit item 10)
-- =========================================================
-- Every screen labels the streak in days ("3-day streak", "0d streak"), but
-- finalize_focus_session counted consecutive clean SESSIONS: a breach reset
-- it and missed days did nothing. This makes the server match the label:
--   * a local day counts once it has one clean stack;
--   * a missed day resets the streak unless a freeze is held;
--   * a freeze is earned by 3 clean stacks in a local week, max 1 held,
--     spent automatically to bridge one missed day.
-- Existing streaks are recomputed from focus_history in local days.

ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS streak_last_day date,
  ADD COLUMN IF NOT EXISTS streak_freezes smallint NOT NULL DEFAULT 0 CHECK (streak_freezes BETWEEN 0 AND 1),
  ADD COLUMN IF NOT EXISTS freeze_week date,
  ADD COLUMN IF NOT EXISTS freeze_week_clean smallint NOT NULL DEFAULT 0;

-- Clients read their own streak state; only SECURITY DEFINER code writes it
-- (column-level UPDATE grants on profiles don't include these columns).
GRANT SELECT (streak_last_day, streak_freezes, freeze_week, freeze_week_clean) ON public.profiles TO authenticated;
CREATE OR REPLACE FUNCTION public.finalize_focus_session(
  _room_id uuid,
  _score integer,                       -- IGNORED (server derives score)
  _xp integer,                          -- IGNORED (server derives xp)
  _duration_seconds integer,            -- IGNORED (server derives duration)
  _breaches_count integer,              -- IGNORED (server counts breaks)
  _tier text,                           -- IGNORED (server derives tier)
  _scoring_version smallint DEFAULT 2,
  _abandonment_seconds integer DEFAULT 0  -- accepted, clamped, can only lower score
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
  _uid UUID := auth.uid();
  _history_id UUID;
  _room public.rooms%ROWTYPE;
  _part public.participants%ROWTYPE;
  _target INTEGER;
  _duration INTEGER;
  _minor INTEGER;
  _severe INTEGER;
  _breaches INTEGER;
  _abandon INTEGER;
  _penalty NUMERIC;
  _raw NUMERIC;
  _score_final INTEGER;
  _multiplier NUMERIC;
  _tier_key TEXT;
  _accept_xp INTEGER;
  _new_streak INTEGER;
  _today DATE;
  _clean BOOLEAN;
  _last DATE;
  _freezes SMALLINT;
  _fweek DATE;
  _fclean SMALLINT;
  _wk DATE;
  _started TIMESTAMPTZ;
  _ended TIMESTAMPTZ;
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  IF _room_id IS NULL THEN RAISE EXCEPTION 'room_required'; END IF;

  SELECT * INTO _part FROM public.participants WHERE room_id = _room_id AND user_id = _uid;
  IF _part.user_id IS NULL THEN RAISE EXCEPTION 'not_participant'; END IF;

  -- Idempotent: one finalize row per (profile, room).
  SELECT id INTO _history_id FROM public.focus_history WHERE profile_id = _uid AND room_id = _room_id;
  IF _history_id IS NOT NULL THEN RETURN _history_id; END IF;

  SELECT * INTO _room FROM public.rooms WHERE id = _room_id;
  _target := GREATEST(COALESCE(_room.target_duration_seconds, 1), 1);

  -- Server-derived focus duration from timestamps. Start at the later of the
  -- room start and the participant join; end at the participant's leave time or
  -- now. Cap at the target and floor at 0.
  _started := GREATEST(COALESCE(_room.started_at, _part.joined_at), _part.joined_at);
  _ended   := COALESCE(_part.left_at, _room.ended_at, now());
  _duration := LEAST(
                 GREATEST(EXTRACT(EPOCH FROM (_ended - _started))::INT, 0),
                 _target
               );

  -- Server-counted breaches by severity.
  SELECT
    COUNT(*) FILTER (WHERE severity = 'minor')::INT,
    COUNT(*) FILTER (WHERE severity = 'severe')::INT
  INTO _minor, _severe
  FROM public.breaks WHERE room_id = _room_id AND user_id = _uid;
  _breaches := _minor + _severe;

  -- Abandonment: client-supplied, clamped to [0, duration] so it can only
  -- reduce the score, never inflate it. Penalty is seconds past the 15s grace.
  _abandon := LEAST(GREATEST(COALESCE(_abandonment_seconds, 0), 0), _duration);
  _penalty := (_minor * 10) + (_severe * 40) + GREATEST(_abandon - 15, 0);

  -- Score per the documented formula; tier/XP derive from the UNROUNDED score.
  _raw := LEAST(GREATEST((_duration::NUMERIC / _target) * 100 - _penalty, 0), 100);
  _score_final := ROUND(_raw)::INT;

  IF _raw >= 95 THEN _tier_key := 'flow';         _multiplier := 1.5;
  ELSIF _raw >= 85 THEN _tier_key := 'pristine';   _multiplier := 1.0;
  ELSIF _raw >= 70 THEN _tier_key := 'steady';     _multiplier := 0.5;
  ELSIF _raw >= 40 THEN _tier_key := 'fragmented'; _multiplier := 0.0;
  ELSE                  _tier_key := 'compromised'; _multiplier := 0.0;
  END IF;

  _accept_xp := GREATEST(FLOOR(_raw * (_duration::NUMERIC / 60) * _multiplier)::INT, 0);

  INSERT INTO public.focus_history (
    profile_id, room_id, score, xp_earned, duration_seconds, breaches_count, tier, scoring_version
  )
  VALUES (
    _uid, _room_id, _score_final, _accept_xp, _duration, _breaches, _tier_key,
    COALESCE(_scoring_version, 2)
  )
  RETURNING id INTO _history_id;

  -- Daily streak (local day): a day counts once it has one clean stack
  -- (no breaches, score >= 70). A broken stack no longer resets anything; that
  -- day simply doesn't count unless another clean stack lands. Missed days are
  -- handled by decay_streaks() (hourly), which spends a held freeze to bridge
  -- one missed day or resets the streak.
  _today := (now() AT TIME ZONE public.user_timezone(_uid))::date;
  _clean := _breaches = 0 AND _score_final >= 70;
  SELECT current_focus_streak, streak_last_day, streak_freezes, freeze_week, freeze_week_clean
    INTO _new_streak, _last, _freezes, _fweek, _fclean
    FROM public.profiles WHERE id = _uid;

  IF _clean THEN
    IF _last = _today THEN
      _new_streak := GREATEST(_new_streak, 1);
    ELSIF _last = _today - 1 THEN
      _new_streak := _new_streak + 1;
    ELSIF _last = _today - 2 AND _freezes > 0 THEN
      -- decay hasn't run for yesterday yet: bridge it here the same way.
      _new_streak := _new_streak + 1;
      _freezes := _freezes - 1;
    ELSE
      _new_streak := 1;
    END IF;
    _last := _today;

    -- A freeze is earned, never given: the 3rd clean stack of a local week
    -- grants one, and at most one is held.
    _wk := date_trunc('week', _today)::date;
    IF _fweek IS DISTINCT FROM _wk THEN _fweek := _wk; _fclean := 0; END IF;
    _fclean := _fclean + 1;
    IF _fclean = 3 AND _freezes < 1 THEN _freezes := 1; END IF;
  END IF;

  UPDATE public.profiles
     SET lifetime_xp = lifetime_xp + _accept_xp,
         current_focus_streak = _new_streak,
         best_streak = GREATEST(best_streak, _new_streak),
         streak_last_day = _last,
         streak_freezes = _freezes,
         freeze_week = _fweek,
         freeze_week_clean = _fclean,
         total_focus_seconds = total_focus_seconds + _duration,
         updated_at = now()
   WHERE id = _uid;

  UPDATE public.focus_groups g
     SET total_group_xp = total_group_xp + _accept_xp,
         updated_at = now()
   WHERE g.id IN (SELECT group_id FROM public.group_members WHERE profile_id = _uid);

  PERFORM public.evaluate_achievements(_uid, _history_id);

  RETURN _history_id;
END;
$function$;

REVOKE ALL ON FUNCTION public.finalize_focus_session(uuid, integer, integer, integer, integer, text, smallint, integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.finalize_focus_session(uuid, integer, integer, integer, integer, text, smallint, integer) TO authenticated, service_role;

-- Hourly: for every user whose local "yesterday" had no clean stack, spend a
-- held freeze to bridge it, or reset the streak. Hourly because local
-- midnight arrives at a different UTC hour for each timezone.
CREATE OR REPLACE FUNCTION public.decay_streaks()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
BEGIN
  -- Bridge exactly one missed day with a held freeze.
  UPDATE public.profiles p
     SET streak_freezes = p.streak_freezes - 1,
         streak_last_day = (now() AT TIME ZONE public.user_timezone(p.id))::date - 1,
         updated_at = now()
   WHERE p.current_focus_streak > 0
     AND p.streak_freezes > 0
     AND p.streak_last_day = (now() AT TIME ZONE public.user_timezone(p.id))::date - 2;

  -- Anything older than yesterday (and not bridged above) breaks the streak.
  UPDATE public.profiles p
     SET current_focus_streak = 0,
         updated_at = now()
   WHERE p.current_focus_streak > 0
     AND (p.streak_last_day IS NULL
          OR p.streak_last_day < (now() AT TIME ZONE public.user_timezone(p.id))::date - 1);
END;
$$;
REVOKE ALL ON FUNCTION public.decay_streaks() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.decay_streaks() TO service_role;

DO $cron$ BEGIN
  PERFORM cron.unschedule('stackd-streak-decay');
EXCEPTION WHEN OTHERS THEN NULL;
END $cron$;
SELECT cron.schedule('stackd-streak-decay', '7 * * * *', $$ SELECT public.decay_streaks(); $$);

-- Recompute existing streaks as consecutive local days with a clean stack,
-- ending today or yesterday (gaps-and-islands over distinct clean days).
WITH clean_days AS (
  SELECT DISTINCT h.profile_id,
         (h.created_at AT TIME ZONE public.user_timezone(h.profile_id))::date AS d
    FROM public.focus_history h
   WHERE COALESCE(h.breaches_count, 0) = 0 AND COALESCE(h.score, 0) >= 70
), islands AS (
  SELECT profile_id, d, d - (ROW_NUMBER() OVER (PARTITION BY profile_id ORDER BY d))::int AS grp
    FROM clean_days
), runs AS (
  SELECT profile_id, MAX(d) AS last_day, COUNT(*)::int AS len
    FROM islands GROUP BY profile_id, grp
), latest AS (
  SELECT DISTINCT ON (profile_id) profile_id, last_day, len
    FROM runs ORDER BY profile_id, last_day DESC
)
UPDATE public.profiles p
   SET streak_last_day = l.last_day,
       current_focus_streak = CASE
         WHEN l.last_day >= (now() AT TIME ZONE public.user_timezone(p.id))::date - 1 THEN l.len
         ELSE 0 END,
       best_streak = GREATEST(p.best_streak, l.len)
  FROM latest l
 WHERE l.profile_id = p.id;
