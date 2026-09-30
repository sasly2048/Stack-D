-- 1. Safe, cross-user readable projection of profiles (no username_canonical,
--    username_changed_at, productivity_dna, or updated_at).
CREATE OR REPLACE VIEW public.public_profiles
WITH (security_invoker = off) AS
SELECT
  p.id,
  p.display_name,
  p.username,
  p.avatar_url,
  p.bio,
  p.title,
  p.prestige_level,
  p.banner_gradient,
  p.banner_url,
  p.pinned_showcase,
  p.lifetime_xp,
  p.current_focus_streak,
  p.best_streak,
  p.total_focus_seconds,
  p.created_at,
  p.last_active_at
FROM public.profiles p;

REVOKE ALL ON public.public_profiles FROM PUBLIC, anon;
GRANT SELECT ON public.public_profiles TO authenticated;
GRANT SELECT ON public.public_profiles TO service_role;

-- 2. Username availability without exposing other profiles.
CREATE OR REPLACE FUNCTION public.username_is_taken(_canonical text, _exclude_user uuid DEFAULT NULL)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO 'public'
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.profiles
    WHERE username_canonical = _canonical
      AND (_exclude_user IS NULL OR id <> _exclude_user)
  );
$$;

REVOKE ALL ON FUNCTION public.username_is_taken(text, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.username_is_taken(text, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.username_is_taken(text, uuid) TO service_role;

-- 3. Base table: no more blanket read for every signed-in account.
DROP POLICY IF EXISTS "Authenticated can view basic profile fields" ON public.profiles;

CREATE POLICY "Owner or friends can view full profile"
ON public.profiles
FOR SELECT
TO authenticated
USING (auth.uid() = id OR public.are_friends(auth.uid(), id));