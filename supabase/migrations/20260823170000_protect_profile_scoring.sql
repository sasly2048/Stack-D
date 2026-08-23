-- =========================================================
-- P0 #2: profiles — protect scoring/progression columns from client writes
-- =========================================================
-- The "Users can update their own profile" RLS policy allows a user to UPDATE
-- any column of their OWN row, so a direct REST call could set
-- lifetime_xp = 999999, inflate streaks, prestige, total_focus_seconds, or
-- forge their DNA/title/timestamps — all of which are meant to be written only
-- by SECURITY DEFINER RPCs (finalize_focus_session, prestige_up, evaluate_*,
-- title/DNA flows).
--
-- Rather than enumerate an allow-list in RLS (brittle as columns are added), a
-- BEFORE UPDATE trigger freezes the protected columns back to their OLD values
-- for any caller that isn't the service role or a SECURITY DEFINER routine. A
-- normal client UPDATE can still change the safe presentation fields
-- (display_name, avatar_url, bio, banners, pins) — attempts to touch the
-- progression columns are silently reverted, not errored, so legitimate edits
-- that happen to include them still succeed.

CREATE OR REPLACE FUNCTION public.profiles_protect_scoring()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  -- Trusted writers bypass: the service role, and SECURITY DEFINER functions
  -- (which run as the table owner, not 'authenticated'/'anon').
  IF current_user NOT IN ('authenticated', 'anon') THEN
    RETURN NEW;
  END IF;

  -- Freeze every progression / integrity column to its previous value. NOTE:
  -- username / username_canonical / username_changed_at are deliberately NOT
  -- frozen — the client changes those through its own rate-limited update
  -- (username.functions.ts), and the unique constraint on username_canonical
  -- stops collisions. display_name, avatar_url, bio, banners and pins are also
  -- left editable.
  NEW.lifetime_xp           := OLD.lifetime_xp;
  NEW.current_focus_streak  := OLD.current_focus_streak;
  NEW.best_streak           := OLD.best_streak;
  NEW.total_focus_seconds   := OLD.total_focus_seconds;
  NEW.prestige_level        := OLD.prestige_level;
  NEW.productivity_dna      := OLD.productivity_dna;
  -- title is left editable: equipTitle() sets it via the user's client after
  -- checking ownership, so freezing it would break equipping a title. (A direct
  -- REST write could set arbitrary title TEXT, which is cosmetic-only.)
  NEW.scoring_version       := OLD.scoring_version;
  NEW.created_at            := OLD.created_at;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_profiles_protect_scoring ON public.profiles;
CREATE TRIGGER trg_profiles_protect_scoring
  BEFORE UPDATE ON public.profiles
  FOR EACH ROW EXECUTE FUNCTION public.profiles_protect_scoring();

-- Trigger function: never client-callable directly, no args.
REVOKE ALL ON FUNCTION public.profiles_protect_scoring() FROM PUBLIC, anon, authenticated;
