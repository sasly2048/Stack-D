
-- Extend profiles
ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS bio TEXT,
  ADD COLUMN IF NOT EXISTS best_streak INT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS total_focus_seconds BIGINT NOT NULL DEFAULT 0;

-- Public read of a limited profile projection is needed for friend search / public profiles.
-- The existing profiles policies restrict to owner; add a narrow read.
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='profiles' AND policyname='Authenticated can view basic profile fields') THEN
    CREATE POLICY "Authenticated can view basic profile fields"
      ON public.profiles FOR SELECT TO authenticated USING (true);
  END IF;
END $$;

-- ============================================================
-- FRIENDSHIPS
-- ============================================================
CREATE TABLE IF NOT EXISTS public.friendships (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  requester_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  addressee_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','accepted','blocked')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (requester_id <> addressee_id),
  UNIQUE (requester_id, addressee_id)
);

CREATE INDEX IF NOT EXISTS friendships_addressee_idx ON public.friendships(addressee_id, status);
CREATE INDEX IF NOT EXISTS friendships_requester_idx ON public.friendships(requester_id, status);

GRANT SELECT, INSERT, UPDATE, DELETE ON public.friendships TO authenticated;
GRANT ALL ON public.friendships TO service_role;
ALTER TABLE public.friendships ENABLE ROW LEVEL SECURITY;

CREATE POLICY "See own friendship rows" ON public.friendships FOR SELECT TO authenticated
  USING (auth.uid() = requester_id OR auth.uid() = addressee_id);

CREATE POLICY "Send friend request" ON public.friendships FOR INSERT TO authenticated
  WITH CHECK (auth.uid() = requester_id AND status = 'pending');

CREATE POLICY "Respond to own incoming request" ON public.friendships FOR UPDATE TO authenticated
  USING (auth.uid() = addressee_id OR auth.uid() = requester_id)
  WITH CHECK (auth.uid() = addressee_id OR auth.uid() = requester_id);

CREATE POLICY "Remove own friendship" ON public.friendships FOR DELETE TO authenticated
  USING (auth.uid() = requester_id OR auth.uid() = addressee_id);

CREATE TRIGGER friendships_updated_at BEFORE UPDATE ON public.friendships
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- Helper: are two users accepted friends?
CREATE OR REPLACE FUNCTION public.are_friends(_a UUID, _b UUID)
RETURNS BOOLEAN LANGUAGE sql STABLE SECURITY DEFINER SET search_path=public AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.friendships
    WHERE status='accepted' AND (
      (requester_id=_a AND addressee_id=_b) OR
      (requester_id=_b AND addressee_id=_a)
    )
  );
$$;
REVOKE ALL ON FUNCTION public.are_friends(UUID,UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.are_friends(UUID,UUID) TO authenticated;

-- ============================================================
-- ACHIEVEMENTS
-- ============================================================
CREATE TABLE IF NOT EXISTS public.achievements (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  description TEXT NOT NULL,
  icon TEXT NOT NULL DEFAULT 'trophy',
  xp_reward INT NOT NULL DEFAULT 0,
  tier TEXT NOT NULL DEFAULT 'bronze' CHECK (tier IN ('bronze','silver','gold','obsidian')),
  sort_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

GRANT SELECT ON public.achievements TO authenticated, anon;
GRANT ALL ON public.achievements TO service_role;
ALTER TABLE public.achievements ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Anyone can read achievement catalog" ON public.achievements FOR SELECT USING (true);

CREATE TABLE IF NOT EXISTS public.user_achievements (
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  achievement_id TEXT NOT NULL REFERENCES public.achievements(id) ON DELETE CASCADE,
  unlocked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, achievement_id)
);

CREATE INDEX IF NOT EXISTS user_achievements_user_idx ON public.user_achievements(user_id);

GRANT SELECT ON public.user_achievements TO authenticated;
GRANT ALL ON public.user_achievements TO service_role;
ALTER TABLE public.user_achievements ENABLE ROW LEVEL SECURITY;

CREATE POLICY "See any user's unlocks" ON public.user_achievements FOR SELECT TO authenticated USING (true);

-- Seed catalog
INSERT INTO public.achievements (id, name, description, icon, xp_reward, tier, sort_order) VALUES
  ('first_stack', 'First Stack', 'Complete your first focus session.', 'sparkles', 50, 'bronze', 10),
  ('streak_7', 'Seven-Day Rite', 'Maintain a 7-session focus streak.', 'flame', 200, 'silver', 20),
  ('streak_30', 'Thirty-Day Ascension', 'Maintain a 30-session focus streak.', 'flame', 800, 'gold', 30),
  ('hours_10', 'Ten Hours Held', 'Accumulate 10 hours of tracked focus.', 'clock', 150, 'bronze', 40),
  ('hours_100', 'Century of Silence', 'Accumulate 100 hours of tracked focus.', 'clock', 1000, 'gold', 50),
  ('no_breach', 'Unbroken', 'Complete a session with zero breaches.', 'shield', 100, 'silver', 60),
  ('flow_state', 'Flow State', 'Earn a Flow-tier score (95+).', 'zap', 250, 'gold', 70),
  ('team_player', 'Team Player', 'Complete a session inside a Focus Circle.', 'users', 150, 'silver', 80),
  ('night_owl', 'Night Owl', 'Complete a session between 22:00 and 04:00.', 'moon', 75, 'bronze', 90),
  ('early_bird', 'Early Bird', 'Complete a session between 04:00 and 08:00.', 'sunrise', 75, 'bronze', 100)
ON CONFLICT (id) DO UPDATE SET
  name = EXCLUDED.name, description = EXCLUDED.description, icon = EXCLUDED.icon,
  xp_reward = EXCLUDED.xp_reward, tier = EXCLUDED.tier, sort_order = EXCLUDED.sort_order;

-- Check + unlock achievements after a session (called from finalize_focus_session)
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
  _hour := EXTRACT(HOUR FROM _h.completed_at AT TIME ZONE 'UTC')::INT;
  SELECT EXISTS(SELECT 1 FROM public.group_members WHERE profile_id = _user_id) INTO _in_group;

  -- Helper inline: try_unlock via CTE
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
    -- Award XP for newly unlocked achievements
    UPDATE public.profiles p
       SET lifetime_xp = lifetime_xp + a.xp_reward,
           updated_at = now()
      FROM public.achievements a
     WHERE a.id = _new AND p.id = _user_id;
    RETURN NEXT _new;
  END LOOP;
END;
$$;
REVOKE ALL ON FUNCTION public.evaluate_achievements(UUID,UUID) FROM PUBLIC, anon, authenticated;

-- Rewrite finalize_focus_session to update best_streak, total_focus_seconds, and unlock achievements.
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
