-- Scope participants RLS policies to authenticated role only.
-- Previously these policies applied to PUBLIC/anon and evaluated is_room_host()
-- which is not executable by anon, causing "permission denied for function" on
-- any anonymous read (e.g. when landing on an open room page while signed out).

DROP POLICY IF EXISTS "Same-room active members can read participants" ON public.participants;
DROP POLICY IF EXISTS "Users can remove own participant or host can remove any" ON public.participants;
DROP POLICY IF EXISTS "Users can update own participant or host can update any" ON public.participants;

CREATE POLICY "Same-room active members can read participants"
  ON public.participants FOR SELECT
  TO authenticated
  USING (
    auth.uid() = user_id
    OR public.is_room_host(room_id, auth.uid())
    OR public.is_room_participant(room_id, auth.uid())
  );

CREATE POLICY "Users can remove own participant or host can remove any"
  ON public.participants FOR DELETE
  TO authenticated
  USING (
    auth.uid() = user_id
    OR public.is_room_host(room_id, auth.uid())
  );

CREATE POLICY "Users can update own participant or host can update any"
  ON public.participants FOR UPDATE
  TO authenticated
  USING (
    auth.uid() = user_id
    OR public.is_room_host(room_id, auth.uid())
  )
  WITH CHECK (
    auth.uid() = user_id
    OR public.is_room_host(room_id, auth.uid())
  );
