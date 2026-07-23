
-- ============================================================
-- ENGAGEMENT LOOP: session tags/notes, daily challenges
-- ============================================================
ALTER TABLE public.focus_history
  ADD COLUMN IF NOT EXISTS notes TEXT,
  ADD COLUMN IF NOT EXISTS tags TEXT[] NOT NULL DEFAULT '{}';

ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS last_active_at TIMESTAMPTZ;

-- Daily challenge catalog (seeded rules; date-scoped assignments derived)
CREATE TABLE IF NOT EXISTS public.challenges (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  description TEXT NOT NULL,
  cadence TEXT NOT NULL CHECK (cadence IN ('daily','weekly')),
  metric TEXT NOT NULL CHECK (metric IN ('sessions','focus_minutes','perfect_sessions','flow_sessions')),
  target INT NOT NULL,
  xp_reward INT NOT NULL DEFAULT 0,
  sort_order INT NOT NULL DEFAULT 0
);
GRANT SELECT ON public.challenges TO anon, authenticated;
GRANT ALL ON public.challenges TO service_role;
ALTER TABLE public.challenges ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Anyone reads challenge catalog" ON public.challenges FOR SELECT USING (true);

INSERT INTO public.challenges (id, name, description, cadence, metric, target, xp_reward, sort_order) VALUES
  ('d_two_sessions',  'Two Sessions',   'Complete 2 focus sessions today.',       'daily',  'sessions',         2, 40, 10),
  ('d_ninety_min',    'Ninety Minutes', 'Log 90 focused minutes today.',          'daily',  'focus_minutes',   90, 60, 20),
  ('d_no_breach',     'Unbroken Day',   'Finish a session today with 0 breaches.','daily',  'perfect_sessions', 1, 80, 30),
  ('w_ten_sessions',  'Ten Sessions',   'Complete 10 sessions this week.',        'weekly', 'sessions',        10, 200, 40),
  ('w_twelve_hours',  'Twelve Hours',   'Log 12 hours of focus this week.',       'weekly', 'focus_minutes',  720, 300, 50),
  ('w_five_flow',     'Five in Flow',   'Reach Flow tier (95+) five times this week.','weekly','flow_sessions', 5, 400, 60)
ON CONFLICT (id) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, cadence=EXCLUDED.cadence,
  metric=EXCLUDED.metric, target=EXCLUDED.target, xp_reward=EXCLUDED.xp_reward,
  sort_order=EXCLUDED.sort_order;

-- Track completion per period (period_start truncated to day for daily, ISO week for weekly).
CREATE TABLE IF NOT EXISTS public.challenge_progress (
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  challenge_id TEXT NOT NULL REFERENCES public.challenges(id) ON DELETE CASCADE,
  period_start DATE NOT NULL,
  progress INT NOT NULL DEFAULT 0,
  completed_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, challenge_id, period_start)
);
CREATE INDEX IF NOT EXISTS challenge_progress_user_period_idx ON public.challenge_progress(user_id, period_start DESC);
GRANT SELECT ON public.challenge_progress TO authenticated;
GRANT ALL ON public.challenge_progress TO service_role;
ALTER TABLE public.challenge_progress ENABLE ROW LEVEL SECURITY;
CREATE POLICY "See own challenge progress" ON public.challenge_progress FOR SELECT TO authenticated USING (auth.uid() = user_id);

-- Activity feed (append-only, friend/self visible)
CREATE TABLE IF NOT EXISTS public.activity_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  kind TEXT NOT NULL CHECK (kind IN ('session_complete','achievement_unlock','challenge_complete','friend_add')),
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS activity_events_user_idx ON public.activity_events(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS activity_events_created_idx ON public.activity_events(created_at DESC);
GRANT SELECT ON public.activity_events TO authenticated;
GRANT ALL ON public.activity_events TO service_role;
ALTER TABLE public.activity_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY "See own or friends' activity"
  ON public.activity_events FOR SELECT TO authenticated
  USING (
    user_id = auth.uid()
    OR public.are_friends(auth.uid(), user_id)
  );

-- Evaluate challenges + write activity events. Called from finalize_focus_session.
CREATE OR REPLACE FUNCTION public.evaluate_challenges(_user_id UUID, _history_id UUID)
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE
  _h public.focus_history%ROWTYPE;
  _today DATE := (now() AT TIME ZONE 'UTC')::DATE;
  _week_start DATE := date_trunc('week', now() AT TIME ZONE 'UTC')::DATE;
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

-- Extend finalize_focus_session to emit an activity event + evaluate challenges.
CREATE OR REPLACE FUNCTION public.finalize_focus_session(_room_id uuid, _score integer, _xp integer, _duration_seconds integer, _breaches_count integer, _tier text)
 RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public' AS $function$
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

  INSERT INTO public.activity_events (user_id, kind, payload)
  VALUES (_uid, 'session_complete', jsonb_build_object(
    'score', _score_clamped, 'tier', _tier_key,
    'duration_seconds', _duration, 'xp', _accept_xp, 'breaches', _breaches
  ));

  PERFORM public.evaluate_achievements(_uid, _history_id);
  PERFORM public.evaluate_challenges(_uid, _history_id);

  RETURN _history_id;
END;
$function$;

-- User-supplied session meta (notes + tags) written after finalize
CREATE OR REPLACE FUNCTION public.update_session_meta(_history_id UUID, _notes TEXT, _tags TEXT[])
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE _uid UUID := auth.uid();
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  UPDATE public.focus_history
     SET notes = LEFT(COALESCE(_notes, ''), 2000),
         tags = COALESCE((SELECT array_agg(DISTINCT LOWER(TRIM(t))) FROM unnest(_tags) t WHERE LENGTH(TRIM(t)) BETWEEN 1 AND 24), '{}'::TEXT[])
   WHERE id = _history_id AND profile_id = _uid;
END;
$$;
REVOKE ALL ON FUNCTION public.update_session_meta(UUID, TEXT, TEXT[]) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.update_session_meta(UUID, TEXT, TEXT[]) TO authenticated;

-- Friend-add activity trigger (fires when a friendship transitions to accepted)
CREATE OR REPLACE FUNCTION public.friendship_accepted_activity()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
BEGIN
  IF NEW.status = 'accepted' AND (OLD.status IS DISTINCT FROM 'accepted') THEN
    INSERT INTO public.activity_events (user_id, kind, payload)
    VALUES (NEW.requester_id, 'friend_add', jsonb_build_object('friend_id', NEW.addressee_id)),
           (NEW.addressee_id, 'friend_add', jsonb_build_object('friend_id', NEW.requester_id));
  END IF;
  RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS friendship_accepted_activity_trg ON public.friendships;
CREATE TRIGGER friendship_accepted_activity_trg
  AFTER UPDATE ON public.friendships
  FOR EACH ROW EXECUTE FUNCTION public.friendship_accepted_activity();

-- Heartbeat function for presence (self-updates last_active_at)
CREATE OR REPLACE FUNCTION public.presence_heartbeat()
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE _uid UUID := auth.uid();
BEGIN
  IF _uid IS NULL THEN RETURN; END IF;
  UPDATE public.profiles SET last_active_at = now() WHERE id = _uid;
END;
$$;
REVOKE ALL ON FUNCTION public.presence_heartbeat() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.presence_heartbeat() TO authenticated;
