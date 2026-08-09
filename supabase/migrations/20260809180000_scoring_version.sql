-- Stamp every completed session with the scoring ruleset that produced it.
--
-- Without this, changing the formula silently reinterprets history: a
-- leaderboard compares scores computed under different rules as though they
-- were equivalent, and there is no way afterwards to tell which rows came from
-- which version. Recording it is trivial now and impossible retroactively.
--
-- Existing rows default to 1 (the ruleset in force before this migration).
-- Version 2 derives tier and XP from the unrounded score; version 1 rounded
-- first, so a raw 84.5 crossed the "pristine" boundary at 85 and doubled the
-- XP multiplier.

ALTER TABLE public.focus_history
  ADD COLUMN IF NOT EXISTS scoring_version SMALLINT NOT NULL DEFAULT 1;

COMMENT ON COLUMN public.focus_history.scoring_version IS
  'Focus-score ruleset that produced this row. See SCORING_VERSION in src/lib/focus-score.ts.';

-- Analytics and leaderboards will want to segment or exclude by version once
-- more than one exists in the table.
CREATE INDEX IF NOT EXISTS idx_focus_history_scoring_version
  ON public.focus_history (scoring_version);

-- finalize_focus_session already recomputes score, duration and breaches
-- server-side; it now records which ruleset it used. The parameter is
-- defaulted so existing callers keep working without a client change.
CREATE OR REPLACE FUNCTION public.finalize_focus_session(
  _room_id uuid,
  _score integer,
  _xp integer,
  _duration_seconds integer,
  _breaches_count integer,
  _tier text,
  _scoring_version smallint DEFAULT 2
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
  _target INTEGER;
  _duration INTEGER;
  _breaches INTEGER;
  _score_clamped INTEGER;
  _multiplier NUMERIC;
  _tier_key TEXT;
  _server_xp INTEGER;
  _accept_xp INTEGER;
  _new_streak INTEGER;
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  IF _room_id IS NULL THEN RAISE EXCEPTION 'room_required'; END IF;

  IF NOT EXISTS (SELECT 1 FROM public.participants WHERE room_id = _room_id AND user_id = _uid) THEN
    RAISE EXCEPTION 'not_participant';
  END IF;

  SELECT id INTO _history_id FROM public.focus_history WHERE profile_id = _uid AND room_id = _room_id;
  IF _history_id IS NOT NULL THEN RETURN _history_id; END IF;

  SELECT * INTO _room FROM public.rooms WHERE id = _room_id;
  _target := GREATEST(COALESCE(_room.target_duration_seconds, 1), 1);

  _duration := LEAST(GREATEST(COALESCE(_duration_seconds, 0), 0), _target);
  SELECT COUNT(*)::INT INTO _breaches FROM public.breaks WHERE room_id = _room_id AND user_id = _uid;
  _score_clamped := LEAST(GREATEST(COALESCE(_score, 0), 0), 100);

  IF _score_clamped >= 95 THEN _tier_key := 'flow';         _multiplier := 1.5;
  ELSIF _score_clamped >= 85 THEN _tier_key := 'pristine';   _multiplier := 1.0;
  ELSIF _score_clamped >= 70 THEN _tier_key := 'steady';     _multiplier := 0.5;
  ELSIF _score_clamped >= 40 THEN _tier_key := 'fragmented'; _multiplier := 0.0;
  ELSE                            _tier_key := 'compromised'; _multiplier := 0.0;
  END IF;

  _server_xp := FLOOR(_score_clamped * (_duration::NUMERIC / 60) * _multiplier)::INT;
  _accept_xp := GREATEST(LEAST(COALESCE(_xp, _server_xp), _server_xp), 0);

  INSERT INTO public.focus_history (
    profile_id, room_id, score, xp_earned, duration_seconds, breaches_count, tier, scoring_version
  )
  VALUES (
    _uid, _room_id, _score_clamped, _accept_xp, _duration, _breaches, _tier_key,
    COALESCE(_scoring_version, 2)
  )
  RETURNING id INTO _history_id;

  _new_streak := CASE WHEN _breaches = 0 AND _score_clamped >= 70
                      THEN (SELECT current_focus_streak FROM public.profiles WHERE id = _uid) + 1
                      ELSE 0 END;

  UPDATE public.profiles
     SET lifetime_xp = lifetime_xp + _accept_xp,
         current_focus_streak = _new_streak,
         best_streak = GREATEST(best_streak, _new_streak),
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

-- Same privilege boundary as every other SECURITY DEFINER routine here:
-- reachable by a signed-in user, never by anon.
REVOKE ALL ON FUNCTION public.finalize_focus_session(uuid, integer, integer, integer, integer, text, smallint) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.finalize_focus_session(uuid, integer, integer, integer, integer, text, smallint) TO authenticated;
