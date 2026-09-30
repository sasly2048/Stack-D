CREATE TABLE public.public_profiles (
  id uuid PRIMARY KEY,
  display_name text NOT NULL,
  username text,
  avatar_url text,
  bio text,
  created_at timestamptz NOT NULL,
  lifetime_xp integer NOT NULL DEFAULT 0,
  current_focus_streak integer NOT NULL DEFAULT 0,
  best_streak integer NOT NULL DEFAULT 0,
  total_focus_seconds bigint NOT NULL DEFAULT 0,
  last_active_at timestamptz,
  banner_url text,
  banner_gradient text,
  title text,
  prestige_level integer NOT NULL DEFAULT 0,
  pinned_showcase jsonb NOT NULL DEFAULT '[]'::jsonb
);

GRANT SELECT ON public.public_profiles TO authenticated;
GRANT ALL ON public.public_profiles TO service_role;

ALTER TABLE public.public_profiles ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Signed-in members can view public profiles"
ON public.public_profiles
FOR SELECT
TO authenticated
USING (true);

CREATE OR REPLACE FUNCTION public.sync_public_profile()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    DELETE FROM public.public_profiles WHERE id = OLD.id;
    RETURN OLD;
  END IF;

  INSERT INTO public.public_profiles (
    id, display_name, username, avatar_url, bio, created_at,
    lifetime_xp, current_focus_streak, best_streak, total_focus_seconds,
    last_active_at, banner_url, banner_gradient, title, prestige_level, pinned_showcase
  ) VALUES (
    NEW.id, NEW.display_name, NEW.username, NEW.avatar_url, NEW.bio, NEW.created_at,
    NEW.lifetime_xp, NEW.current_focus_streak, NEW.best_streak, NEW.total_focus_seconds,
    NEW.last_active_at, NEW.banner_url, NEW.banner_gradient, NEW.title, NEW.prestige_level,
    NEW.pinned_showcase
  )
  ON CONFLICT (id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    username = EXCLUDED.username,
    avatar_url = EXCLUDED.avatar_url,
    bio = EXCLUDED.bio,
    created_at = EXCLUDED.created_at,
    lifetime_xp = EXCLUDED.lifetime_xp,
    current_focus_streak = EXCLUDED.current_focus_streak,
    best_streak = EXCLUDED.best_streak,
    total_focus_seconds = EXCLUDED.total_focus_seconds,
    last_active_at = EXCLUDED.last_active_at,
    banner_url = EXCLUDED.banner_url,
    banner_gradient = EXCLUDED.banner_gradient,
    title = EXCLUDED.title,
    prestige_level = EXCLUDED.prestige_level,
    pinned_showcase = EXCLUDED.pinned_showcase;
  RETURN NEW;
END;
$$;

CREATE TRIGGER sync_public_profile_after_change
AFTER INSERT OR UPDATE OR DELETE ON public.profiles
FOR EACH ROW EXECUTE FUNCTION public.sync_public_profile();

DROP POLICY IF EXISTS "Authenticated can view basic profile fields" ON public.profiles;
CREATE POLICY "Users can view their own full profile"
ON public.profiles
FOR SELECT
TO authenticated
USING (auth.uid() = id);