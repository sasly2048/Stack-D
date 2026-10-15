-- =========================================================
-- Weekly leagues with promotion and demotion (UI/UX audit item 18)
-- =========================================================
-- The leaderboard was one all-time XP list of every user: a gap nobody closes
-- in a week, so it motivated no one. Leagues shrink the comparison to a small
-- group in your tier that resets every week:
--   * tiers 1..5: Bronze, Silver, Gold, Ember, Obsidian (profiles.league_tier);
--   * you join this week's group with your first finalized stack of the week
--     (groups of up to 30 per tier; Monday-start UTC weeks);
--   * weekly XP = sum of focus_history.xp_earned since the week started;
--   * Monday 00:05 UTC, last week's groups settle: top 20% (with XP) move up a
--     tier, bottom 20% move down.

ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS league_tier smallint NOT NULL DEFAULT 1 CHECK (league_tier BETWEEN 1 AND 5);
GRANT SELECT (league_tier) ON public.profiles TO authenticated;

CREATE TABLE IF NOT EXISTS public.league_members (
  week      date     NOT NULL,
  tier      smallint NOT NULL CHECK (tier BETWEEN 1 AND 5),
  group_no  integer  NOT NULL,
  user_id   uuid     NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  joined_at timestamptz NOT NULL DEFAULT now(),
  -- Set when the week settles: 'promoted' | 'stayed' | 'demoted'.
  outcome   text,
  PRIMARY KEY (week, user_id)
);
CREATE INDEX IF NOT EXISTS league_members_group_idx ON public.league_members (week, tier, group_no);

-- No direct client access; boards are served by my_league().
ALTER TABLE public.league_members ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.league_members FROM anon, authenticated;

CREATE OR REPLACE FUNCTION public.league_week(_at timestamptz DEFAULT now())
RETURNS date LANGUAGE sql IMMUTABLE AS $$
  SELECT date_trunc('week', _at AT TIME ZONE 'UTC')::date
$$;

-- Joins the current week's league on a user's first finalized stack.
CREATE OR REPLACE FUNCTION public.league_join_on_history()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE
  _week date := public.league_week(NEW.created_at);
  _tier smallint;
  _group integer;
BEGIN
  IF EXISTS (SELECT 1 FROM public.league_members WHERE week = _week AND user_id = NEW.profile_id) THEN
    RETURN NEW;
  END IF;
  SELECT league_tier INTO _tier FROM public.profiles WHERE id = NEW.profile_id;
  _tier := COALESCE(_tier, 1);
  -- Fill the lowest-numbered group with room; open a new one when all are full.
  SELECT g.group_no INTO _group
    FROM public.league_members g
   WHERE g.week = _week AND g.tier = _tier
   GROUP BY g.group_no
  HAVING COUNT(*) < 30
   ORDER BY g.group_no
   LIMIT 1;
  IF _group IS NULL THEN
    SELECT COALESCE(MAX(group_no), 0) + 1 INTO _group
      FROM public.league_members WHERE week = _week AND tier = _tier;
  END IF;
  INSERT INTO public.league_members (week, tier, group_no, user_id)
  VALUES (_week, _tier, _group, NEW.profile_id)
  ON CONFLICT DO NOTHING;
  RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION public.league_join_on_history() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS league_join_after_history ON public.focus_history;
CREATE TRIGGER league_join_after_history
AFTER INSERT ON public.focus_history
FOR EACH ROW EXECUTE FUNCTION public.league_join_on_history();

-- The caller's board for this week: rank, weekly XP and zone per member.
CREATE OR REPLACE FUNCTION public.my_league()
RETURNS TABLE (
  user_id uuid,
  display_name text,
  username text,
  avatar_url text,
  weekly_xp integer,
  rank integer,
  zone text,
  tier smallint,
  week_ends_at timestamptz,
  is_me boolean
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE
  _uid uuid := auth.uid();
  _week date := public.league_week();
  _me public.league_members%ROWTYPE;
  _n integer;
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  SELECT * INTO _me FROM public.league_members m WHERE m.week = _week AND m.user_id = _uid;
  IF _me.user_id IS NULL THEN RETURN; END IF;  -- not placed yet this week
  SELECT COUNT(*) INTO _n FROM public.league_members m
   WHERE m.week = _week AND m.tier = _me.tier AND m.group_no = _me.group_no;

  RETURN QUERY
  WITH board AS (
    SELECT m.user_id,
           COALESCE((SELECT SUM(h.xp_earned) FROM public.focus_history h
                      WHERE h.profile_id = m.user_id AND h.created_at >= _week::timestamptz), 0)::int AS xp,
           m.joined_at
      FROM public.league_members m
     WHERE m.week = _week AND m.tier = _me.tier AND m.group_no = _me.group_no
  ), ranked AS (
    SELECT b.*, (ROW_NUMBER() OVER (ORDER BY b.xp DESC, b.joined_at))::int AS r FROM board b
  )
  SELECT r.user_id, pp.display_name, pp.username, pp.avatar_url, r.xp, r.r,
         CASE
           WHEN r.r <= CEIL(_n * 0.2) AND _me.tier < 5 AND r.xp > 0 THEN 'promote'
           WHEN r.r > _n - FLOOR(_n * 0.2) AND _me.tier > 1 THEN 'demote'
           ELSE 'safe'
         END,
         _me.tier,
         (_week + 7)::timestamptz,
         r.user_id = _uid
    FROM ranked r
    JOIN public.public_profiles pp ON pp.id = r.user_id
   ORDER BY r.r;
END;
$$;
REVOKE ALL ON FUNCTION public.my_league() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.my_league() TO authenticated;

-- Settles a finished week: promotion/demotion per group, applied to profiles.
CREATE OR REPLACE FUNCTION public.settle_league_week(_week date DEFAULT public.league_week() - 7)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
BEGIN
  WITH board AS (
    SELECT m.week, m.tier, m.group_no, m.user_id, m.joined_at,
           COALESCE((SELECT SUM(h.xp_earned) FROM public.focus_history h
                      WHERE h.profile_id = m.user_id
                        AND h.created_at >= m.week::timestamptz
                        AND h.created_at < (m.week + 7)::timestamptz), 0) AS xp
      FROM public.league_members m
     WHERE m.week = _week AND m.outcome IS NULL
  ), ranked AS (
    SELECT b.*,
           ROW_NUMBER() OVER (PARTITION BY b.tier, b.group_no ORDER BY b.xp DESC, b.joined_at) AS r,
           COUNT(*) OVER (PARTITION BY b.tier, b.group_no) AS n
      FROM board b
  ), decided AS (
    SELECT user_id, tier,
           CASE
             WHEN r <= CEIL(n * 0.2) AND tier < 5 AND xp > 0 THEN 'promoted'
             WHEN r > n - FLOOR(n * 0.2) AND tier > 1 THEN 'demoted'
             ELSE 'stayed'
           END AS outcome
      FROM ranked
  ), marked AS (
    UPDATE public.league_members m
       SET outcome = d.outcome
      FROM decided d
     WHERE m.week = _week AND m.user_id = d.user_id
    RETURNING m.user_id, d.tier, d.outcome
  )
  UPDATE public.profiles p
     SET league_tier = CASE k.outcome
                         WHEN 'promoted' THEN k.tier + 1
                         WHEN 'demoted' THEN k.tier - 1
                         ELSE k.tier END,
         updated_at = now()
    FROM marked k
   WHERE p.id = k.user_id;
END;
$$;
REVOKE ALL ON FUNCTION public.settle_league_week(date) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.settle_league_week(date) TO service_role;

DO $cron$ BEGIN
  PERFORM cron.unschedule('stackd-league-settle');
EXCEPTION WHEN OTHERS THEN NULL;
END $cron$;
SELECT cron.schedule('stackd-league-settle', '5 0 * * 1', $$ SELECT public.settle_league_week(); $$);

-- Place everyone who already stacked this week, so the board isn't empty
-- until their next session.
INSERT INTO public.league_members (week, tier, group_no, user_id, joined_at)
SELECT public.league_week(), COALESCE(p.league_tier, 1), 1, h.profile_id, MIN(h.created_at)
  FROM public.focus_history h
  JOIN public.profiles p ON p.id = h.profile_id
 WHERE h.created_at >= public.league_week()::timestamptz
 GROUP BY h.profile_id, p.league_tier
ON CONFLICT DO NOTHING;
