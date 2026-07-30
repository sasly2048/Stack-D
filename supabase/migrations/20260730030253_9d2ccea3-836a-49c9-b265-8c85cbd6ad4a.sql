DROP POLICY IF EXISTS "See any user's unlocks" ON public.user_achievements;
CREATE POLICY "Owner or friends can view unlocks" ON public.user_achievements
FOR SELECT TO authenticated
USING (auth.uid() = user_id OR public.are_friends(auth.uid(), user_id));

DROP POLICY IF EXISTS "user_titles public read" ON public.user_titles;
CREATE POLICY "Owner or friends can view titles" ON public.user_titles
FOR SELECT TO authenticated
USING (auth.uid() = user_id OR public.are_friends(auth.uid(), user_id));