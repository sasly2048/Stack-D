DROP VIEW IF EXISTS public.public_profiles;

-- Restore row visibility (needed for leaderboards, rooms, circles, search) but
-- protect the sensitive columns with column-level privileges instead.
DROP POLICY IF EXISTS "Owner or friends can view full profile" ON public.profiles;

CREATE POLICY "Authenticated can view basic profile fields"
ON public.profiles
FOR SELECT
TO authenticated
USING (true);

REVOKE SELECT ON public.profiles FROM authenticated;
REVOKE SELECT ON public.profiles FROM anon;

GRANT SELECT (
  id,
  display_name,
  username,
  avatar_url,
  bio,
  title,
  prestige_level,
  banner_gradient,
  banner_url,
  pinned_showcase,
  lifetime_xp,
  current_focus_streak,
  best_streak,
  total_focus_seconds,
  created_at,
  last_active_at
) ON public.profiles TO authenticated;

GRANT ALL ON public.profiles TO service_role;

-- Owner-only access to the columns that are no longer readable through the API.
CREATE OR REPLACE FUNCTION public.get_my_private_profile()
RETURNS TABLE (
  username text,
  username_canonical text,
  username_changed_at timestamptz,
  productivity_dna text
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO 'public'
AS $$
  SELECT p.username, p.username_canonical, p.username_changed_at, p.productivity_dna
  FROM public.profiles p
  WHERE p.id = auth.uid();
$$;

REVOKE ALL ON FUNCTION public.get_my_private_profile() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.get_my_private_profile() FROM anon;
GRANT EXECUTE ON FUNCTION public.get_my_private_profile() TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_my_private_profile() TO service_role;