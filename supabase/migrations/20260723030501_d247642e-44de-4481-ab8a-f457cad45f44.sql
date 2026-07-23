
CREATE OR REPLACE FUNCTION public.evaluate_achievements(_user_id UUID, _history_id UUID)
RETURNS SETOF TEXT LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE
  _h public.focus_history%ROWTYPE;
  _profile public.profiles%ROWTYPE;
  _total_hours NUMERIC;
  _hour INT;
  _in_group BOOLEAN;
  _new TEXT;
BEGIN
  SELECT * INTO _h FROM public.focus_history WHERE id = _history_id AND profile_id = _user_id;
  IF _h.id IS NULL THEN RETURN; END IF;
  SELECT * INTO _profile FROM public.profiles WHERE id = _user_id;
  _total_hours := COALESCE(_profile.total_focus_seconds,0)::NUMERIC / 3600.0;
  _hour := EXTRACT(HOUR FROM _h.created_at AT TIME ZONE 'UTC')::INT;
  SELECT EXISTS(SELECT 1 FROM public.group_members WHERE profile_id = _user_id) INTO _in_group;

  FOR _new IN
    WITH candidates(aid, ok) AS (
      VALUES
        ('first_stack', TRUE),
        ('streak_7',  _profile.current_focus_streak >= 7),
        ('streak_30', _profile.current_focus_streak >= 30),
        ('hours_10',  _total_hours >= 10),
        ('hours_100', _total_hours >= 100),
        ('no_breach', _h.breaches_count = 0 AND _h.duration_seconds >= 300),
        ('flow_state', _h.score >= 95),
        ('team_player', _in_group),
        ('night_owl', _hour >= 22 OR _hour < 4),
        ('early_bird', _hour >= 4 AND _hour < 8)
    ), ins AS (
      INSERT INTO public.user_achievements (user_id, achievement_id)
      SELECT _user_id, aid FROM candidates WHERE ok
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
    VALUES (_user_id, 'achievement_unlock', jsonb_build_object('id', _new));
    RETURN NEXT _new;
  END LOOP;
END;
$$;
REVOKE ALL ON FUNCTION public.evaluate_achievements(UUID,UUID) FROM PUBLIC, anon, authenticated;
