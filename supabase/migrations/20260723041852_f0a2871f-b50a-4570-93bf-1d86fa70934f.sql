
-- Daily rewards: track claim streak per user
CREATE TABLE public.login_streaks (
  user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  streak INT NOT NULL DEFAULT 0,
  last_claim_date DATE,
  total_claims INT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
GRANT SELECT, INSERT, UPDATE ON public.login_streaks TO authenticated;
GRANT ALL ON public.login_streaks TO service_role;
ALTER TABLE public.login_streaks ENABLE ROW LEVEL SECURITY;
CREATE POLICY "own login streak" ON public.login_streaks
  FOR SELECT TO authenticated USING (auth.uid() = user_id);

-- Time capsules
CREATE TABLE public.time_capsules (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  message TEXT NOT NULL,
  open_at TIMESTAMPTZ NOT NULL,
  opened_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
GRANT SELECT, INSERT, UPDATE, DELETE ON public.time_capsules TO authenticated;
GRANT ALL ON public.time_capsules TO service_role;
ALTER TABLE public.time_capsules ENABLE ROW LEVEL SECURITY;
CREATE POLICY "own capsules read" ON public.time_capsules
  FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "own capsules write" ON public.time_capsules
  FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY "own capsules update" ON public.time_capsules
  FOR UPDATE TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "own capsules delete" ON public.time_capsules
  FOR DELETE TO authenticated USING (auth.uid() = user_id);
CREATE INDEX time_capsules_user_open_idx ON public.time_capsules(user_id, open_at);

-- Trust & Safety: reports
CREATE TABLE public.user_reports (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  reporter_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  target_user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
  target_room_id UUID REFERENCES public.rooms(id) ON DELETE CASCADE,
  kind TEXT NOT NULL,
  reason TEXT,
  status TEXT NOT NULL DEFAULT 'open',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
GRANT SELECT, INSERT ON public.user_reports TO authenticated;
GRANT ALL ON public.user_reports TO service_role;
ALTER TABLE public.user_reports ENABLE ROW LEVEL SECURITY;
CREATE POLICY "reporters see their reports" ON public.user_reports
  FOR SELECT TO authenticated USING (auth.uid() = reporter_id);
CREATE POLICY "authenticated can file reports" ON public.user_reports
  FOR INSERT TO authenticated WITH CHECK (auth.uid() = reporter_id);

-- Trust & Safety: blocks
CREATE TABLE public.user_blocks (
  blocker_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  blocked_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (blocker_id, blocked_id)
);
GRANT SELECT, INSERT, DELETE ON public.user_blocks TO authenticated;
GRANT ALL ON public.user_blocks TO service_role;
ALTER TABLE public.user_blocks ENABLE ROW LEVEL SECURITY;
CREATE POLICY "own blocks read" ON public.user_blocks
  FOR SELECT TO authenticated USING (auth.uid() = blocker_id);
CREATE POLICY "own blocks write" ON public.user_blocks
  FOR INSERT TO authenticated WITH CHECK (auth.uid() = blocker_id);
CREATE POLICY "own blocks delete" ON public.user_blocks
  FOR DELETE TO authenticated USING (auth.uid() = blocker_id);

-- Daily reward claim RPC
CREATE OR REPLACE FUNCTION public.claim_daily_reward()
RETURNS TABLE(reward_xp INT, new_streak INT, day_of_streak INT)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE
  _uid UUID := auth.uid();
  _today DATE := (now() AT TIME ZONE 'UTC')::DATE;
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
  -- reward ramp: day1=10, day2=20, day3=40, day4=60, day5=80, day6=100, day7=200 (loop)
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
REVOKE ALL ON FUNCTION public.claim_daily_reward() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.claim_daily_reward() TO authenticated;

-- Prestige RPC: requires 100k lifetime XP per level, resets streak but keeps XP total
CREATE OR REPLACE FUNCTION public.prestige_up()
RETURNS TABLE(new_prestige INT)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE
  _uid UUID := auth.uid();
  _xp BIGINT;
  _level INT;
  _needed BIGINT;
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  SELECT lifetime_xp, COALESCE(prestige_level,0) INTO _xp, _level
    FROM public.profiles WHERE id = _uid FOR UPDATE;
  _needed := 100000::BIGINT * (_level + 1);
  IF _xp < _needed THEN RAISE EXCEPTION 'not_enough_xp'; END IF;
  UPDATE public.profiles
    SET prestige_level = _level + 1,
        current_focus_streak = 0,
        updated_at = now()
    WHERE id = _uid;
  INSERT INTO public.activity_events(user_id, kind, payload)
    VALUES (_uid, 'prestige', jsonb_build_object('level', _level + 1));
  new_prestige := _level + 1;
  RETURN NEXT;
END;
$$;
REVOKE ALL ON FUNCTION public.prestige_up() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.prestige_up() TO authenticated;

-- Enable Realtime for milestones so the room lobby streams new entries
ALTER PUBLICATION supabase_realtime ADD TABLE public.room_milestones;
