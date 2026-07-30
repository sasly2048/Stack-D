-- 1. Allowlists ------------------------------------------------------------
ALTER TABLE public.room_events DROP CONSTRAINT IF EXISTS room_events_kind_check;
ALTER TABLE public.room_events ADD CONSTRAINT room_events_kind_check CHECK (kind = ANY (ARRAY[
  'joined','left','started','paused','resumed','breach','completed','pinned','goal_hit',
  'moderator_added','moderator_removed','join_requested','join_approved','join_denied',
  'ready','unready','all_ready','disconnected','reconnected'
]));

ALTER TABLE public.activity_events DROP CONSTRAINT IF EXISTS activity_events_kind_check;
ALTER TABLE public.activity_events ADD CONSTRAINT activity_events_kind_check CHECK (kind = ANY (ARRAY[
  'session_complete','achievement_unlock','challenge_complete','friend_add','daily_reward','prestige',
  'session.started','session.completed','session.breached','room.created','room.joined',
  'atlas.recommendation_shown','atlas.recommendation_dismissed','low_power.toggled','integration.viewed',
  'milestone_unlock','personality_shift','season_join','wrapped.viewed'
]));

ALTER TABLE public.achievements DROP CONSTRAINT IF EXISTS achievements_tier_check;
ALTER TABLE public.achievements ADD CONSTRAINT achievements_tier_check CHECK (tier = ANY (ARRAY[
  'bronze','silver','gold','obsidian','milestone'
]));

-- 2. Weekly named seasons ---------------------------------------------------
CREATE OR REPLACE FUNCTION public.ensure_current_season()
RETURNS public.seasons
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  _start TIMESTAMPTZ := date_trunc('week', now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC';
  _end   TIMESTAMPTZ := _start + INTERVAL '7 days' - INTERVAL '1 second';
  _s public.seasons%ROWTYPE;
  _names TEXT[] := ARRAY[
    'Season of Quiet Hands','The Long Dark','Ember Week','Still Water','Iron Silence',
    'The Slow Burn','Obsidian Tide','Held Breath','The Deep Field','Ash and Focus',
    'Northern Quiet','The Steady Hour','Lantern Week','Cold Forge','The Unbroken Line',
    'Glass Morning'
  ];
  _idx INT;
  _ord INT;
BEGIN
  SELECT * INTO _s FROM public.seasons WHERE starts_at = _start LIMIT 1;
  IF _s.id IS NOT NULL THEN RETURN _s; END IF;

  _ord := (EXTRACT(EPOCH FROM _start)::BIGINT / 604800)::INT;
  _idx := (_ord % array_length(_names, 1)) + 1;

  INSERT INTO public.seasons (name, description, starts_at, ends_at, xp_multiplier)
  VALUES (_names[_idx], 'Week ' || _ord::TEXT, _start, _end, 1.0)
  ON CONFLICT DO NOTHING
  RETURNING * INTO _s;

  IF _s.id IS NULL THEN
    SELECT * INTO _s FROM public.seasons WHERE starts_at = _start LIMIT 1;
  END IF;
  RETURN _s;
END;
$$;

REVOKE ALL ON FUNCTION public.ensure_current_season() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.ensure_current_season() TO authenticated, service_role;

CREATE UNIQUE INDEX IF NOT EXISTS seasons_starts_at_key ON public.seasons (starts_at);

-- 3. Lifetime milestones ----------------------------------------------------
CREATE OR REPLACE FUNCTION public.evaluate_milestones(_user_id uuid)
RETURNS SETOF text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  _hours NUMERIC;
  _sessions INT;
  _best INT;
  _new TEXT;
BEGIN
  SELECT COALESCE(total_focus_seconds,0)/3600.0, COALESCE(best_streak,0)
    INTO _hours, _best FROM public.profiles WHERE id = _user_id;
  SELECT COUNT(*)::INT INTO _sessions FROM public.focus_history WHERE profile_id = _user_id;

  FOR _new IN
    WITH candidates(aid, ok) AS (
      VALUES
        ('ms_hours_100',  _hours >= 100),
        ('ms_hours_200',  _hours >= 200),
        ('ms_hours_300',  _hours >= 300),
        ('ms_hours_500',  _hours >= 500),
        ('ms_hours_1000', _hours >= 1000),
        ('ms_hours_2000', _hours >= 2000),
        ('ms_sessions_100',  _sessions >= 100),
        ('ms_sessions_500',  _sessions >= 500),
        ('ms_sessions_1000', _sessions >= 1000),
        ('ms_streak_50',  _best >= 50),
        ('ms_streak_100', _best >= 100)
    ), ins AS (
      INSERT INTO public.user_achievements (user_id, achievement_id)
      SELECT _user_id, aid FROM candidates
       WHERE ok AND EXISTS (SELECT 1 FROM public.achievements a WHERE a.id = aid)
      ON CONFLICT DO NOTHING
      RETURNING achievement_id
    )
    SELECT achievement_id FROM ins
  LOOP
    UPDATE public.profiles p
       SET lifetime_xp = lifetime_xp + a.xp_reward, updated_at = now()
      FROM public.achievements a
     WHERE a.id = _new AND p.id = _user_id;
    INSERT INTO public.activity_events (user_id, kind, payload)
    VALUES (_user_id, 'milestone_unlock', jsonb_build_object('id', _new));
    RETURN NEXT _new;
  END LOOP;
END;
$$;

REVOKE ALL ON FUNCTION public.evaluate_milestones(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.evaluate_milestones(uuid) TO authenticated, service_role;

-- 4. Composite focus personality -------------------------------------------
CREATE OR REPLACE FUNCTION public.refresh_personality(_user_id uuid)
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
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
         COUNT(*) FILTER (WHERE EXTRACT(HOUR FROM created_at AT TIME ZONE 'UTC') >= 22
                            OR EXTRACT(HOUR FROM created_at AT TIME ZONE 'UTC') < 4)::INT,
         COUNT(*) FILTER (WHERE EXTRACT(HOUR FROM created_at AT TIME ZONE 'UTC') >= 4
                           AND EXTRACT(HOUR FROM created_at AT TIME ZONE 'UTC') < 9)::INT,
         COUNT(*) FILTER (WHERE EXTRACT(ISODOW FROM created_at AT TIME ZONE 'UTC') >= 6)::INT,
         COUNT(DISTINCT (created_at AT TIME ZONE 'UTC')::date)::INT,
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

-- 5. finalize_focus_session: season XP + milestones + personality -----------
CREATE OR REPLACE FUNCTION public.finalize_focus_session(_room_id uuid, _score integer, _xp integer, _duration_seconds integer, _breaches_count integer, _tier text)
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
  _season public.seasons%ROWTYPE;
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

  INSERT INTO public.focus_history (profile_id, room_id, score, xp_earned, duration_seconds, breaches_count, tier)
  VALUES (_uid, _room_id, _score_clamped, _accept_xp, _duration, _breaches, _tier_key)
  RETURNING id INTO _history_id;

  _new_streak := CASE WHEN _breaches = 0 AND _score_clamped >= 70
                      THEN (SELECT current_focus_streak FROM public.profiles WHERE id = _uid) + 1
                      ELSE 0 END;

  UPDATE public.profiles
     SET lifetime_xp = lifetime_xp + _accept_xp,
         current_focus_streak = _new_streak,
         best_streak = GREATEST(best_streak, _new_streak),
         total_focus_seconds = total_focus_seconds + _duration,
         last_active_at = now(),
         updated_at = now()
   WHERE id = _uid;

  UPDATE public.focus_groups g SET total_group_xp = total_group_xp + _accept_xp, updated_at = now()
   WHERE g.id IN (SELECT group_id FROM public.group_members WHERE profile_id = _uid);

  -- Weekly season standings (permanent XP is untouched above).
  _season := public.ensure_current_season();
  IF _season.id IS NOT NULL AND _accept_xp > 0 THEN
    INSERT INTO public.season_participants (season_id, user_id, xp, updated_at)
    VALUES (_season.id, _uid, _accept_xp, now())
    ON CONFLICT (season_id, user_id) DO UPDATE
      SET xp = public.season_participants.xp + EXCLUDED.xp, updated_at = now();
  END IF;

  INSERT INTO public.activity_events (user_id, kind, payload)
  VALUES (_uid, 'session_complete', jsonb_build_object(
    'score', _score_clamped, 'tier', _tier_key,
    'duration_seconds', _duration, 'xp', _accept_xp, 'breaches', _breaches
  ));

  PERFORM public.evaluate_achievements(_uid, _history_id);
  PERFORM public.evaluate_challenges(_uid, _history_id);
  PERFORM public.evaluate_milestones(_uid);
  PERFORM public.refresh_personality(_uid);

  RETURN _history_id;
END;
$function$;

REVOKE ALL ON FUNCTION public.finalize_focus_session(uuid, integer, integer, integer, integer, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.finalize_focus_session(uuid, integer, integer, integer, integer, text) TO authenticated, service_role;

-- 6. Weekly rollover job ----------------------------------------------------
CREATE EXTENSION IF NOT EXISTS pg_cron;
DO $$
BEGIN
  PERFORM cron.unschedule('stackd-weekly-season');
EXCEPTION WHEN OTHERS THEN NULL;
END $$;
SELECT cron.schedule('stackd-weekly-season', '0 0 * * 1', $$ SELECT public.ensure_current_season(); $$);