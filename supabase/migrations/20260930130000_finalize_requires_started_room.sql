-- finalize_focus_session: record nothing for a room that never started.
--
-- Found on device: cancel a session at the placement screen (before the
-- start RPC), reopen the room, and the client's finalize wrote a 19-second
-- "compromised" (score 6) row — _started fell back to participant.joined_at,
-- so time spent in the lobby counted as focus. It also zeroed the focus streak
-- (score < 70) and added the lobby seconds to total_focus_seconds.
--
-- Returns NULL (no row) when rooms.started_at IS NULL; both clients also skip
-- finalize in that case. Body otherwise identical to
-- 20260825060000_finalize_server_authoritative_score.sql.
--
-- Idempotent: CREATE OR REPLACE.
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

  -- A room whose clock never started (aborted in the lobby / during
  -- placement) has no session to record. COALESCE(started_at, joined_at)
  -- below used to credit lobby time as focus: a cancelled room minted a
  -- "compromised" row, reset the focus streak and padded total focus time.
  IF _room.started_at IS NULL THEN
    RETURN NULL;
  END IF;
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

  _new_streak := CASE WHEN _breaches = 0 AND _score_final >= 70
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
