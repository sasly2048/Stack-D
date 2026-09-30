-- Titles must be earned, not self-granted.
DROP POLICY IF EXISTS "user_titles owner write" ON public.user_titles;
REVOKE INSERT ON public.user_titles FROM authenticated;

CREATE OR REPLACE FUNCTION public.award_earned_titles()
RETURNS TABLE(title_id text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  uid uuid := auth.uid();
  v_streak int;
  v_hours numeric;
  v_flow int;
  v_night int;
  v_rooms int;
  v_events int;
BEGIN
  IF uid IS NULL THEN
    RAISE EXCEPTION 'not_authenticated';
  END IF;

  SELECT COALESCE(p.current_focus_streak, 0), COALESCE(p.total_focus_seconds, 0) / 3600.0
    INTO v_streak, v_hours
  FROM public.profiles p WHERE p.id = uid;

  SELECT
    COUNT(*) FILTER (WHERE COALESCE(h.score, 0) >= 95),
    COUNT(*) FILTER (WHERE EXTRACT(HOUR FROM h.created_at) >= 22 OR EXTRACT(HOUR FROM h.created_at) < 4)
    INTO v_flow, v_night
  FROM public.focus_history h WHERE h.profile_id = uid;

  SELECT COUNT(DISTINCT pa.room_id) INTO v_rooms
  FROM public.participants pa WHERE pa.user_id = uid;

  SELECT COUNT(*) INTO v_events
  FROM public.room_scheduled_events e WHERE e.created_by = uid;

  RETURN QUERY
  INSERT INTO public.user_titles (user_id, title_id)
  SELECT uid, t.id
  FROM (VALUES
    ('night_owl', COALESCE(v_night, 0) >= 3),
    ('deep_thinker', COALESCE(v_flow, 0) >= 10),
    ('legend', COALESCE(v_hours, 0) >= 100),
    ('focused', COALESCE(v_streak, 0) >= 7),
    ('explorer', COALESCE(v_rooms, 0) >= 5),
    ('planner', COALESCE(v_events, 0) >= 3)
  ) AS t(id, earned)
  WHERE t.earned
  ON CONFLICT (user_id, title_id) DO NOTHING
  RETURNING user_titles.title_id;
END;
$$;

REVOKE ALL ON FUNCTION public.award_earned_titles() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.award_earned_titles() TO authenticated;