DROP POLICY IF EXISTS "Anyone can view season participants" ON public.season_participants;
DROP POLICY IF EXISTS "Season standings are viewable by authenticated users" ON public.season_participants;
DROP POLICY IF EXISTS "season_participants_select" ON public.season_participants;
DROP POLICY IF EXISTS "Authenticated can view standings" ON public.season_participants;

CREATE POLICY "Users can view their own season standing"
ON public.season_participants
FOR SELECT
TO authenticated
USING (auth.uid() = user_id);

CREATE OR REPLACE FUNCTION public.season_standings(_season_id uuid, _limit integer DEFAULT 50)
RETURNS TABLE(user_id uuid, display_name text, avatar_url text, xp integer, rank integer)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO 'public'
AS $$
  SELECT sp.user_id,
         p.display_name,
         p.avatar_url,
         sp.xp,
         (ROW_NUMBER() OVER (ORDER BY sp.xp DESC, sp.user_id))::int AS rank
  FROM public.season_participants sp
  LEFT JOIN public.profiles p ON p.id = sp.user_id
  WHERE sp.season_id = _season_id
    AND auth.uid() IS NOT NULL
  ORDER BY sp.xp DESC, sp.user_id
  LIMIT LEAST(GREATEST(COALESCE(_limit, 50), 1), 100);
$$;

REVOKE ALL ON FUNCTION public.season_standings(uuid, integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.season_standings(uuid, integer) TO authenticated;

CREATE OR REPLACE FUNCTION public.my_season_rank(_season_id uuid)
RETURNS integer
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO 'public'
AS $$
  SELECT r.rank FROM (
    SELECT sp.user_id, (ROW_NUMBER() OVER (ORDER BY sp.xp DESC, sp.user_id))::int AS rank
    FROM public.season_participants sp
    WHERE sp.season_id = _season_id
  ) r
  WHERE r.user_id = auth.uid();
$$;

REVOKE ALL ON FUNCTION public.my_season_rank(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.my_season_rank(uuid) TO authenticated;