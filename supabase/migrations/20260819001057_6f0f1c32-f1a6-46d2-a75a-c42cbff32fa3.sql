DROP POLICY IF EXISTS "Respond to own incoming request" ON public.friendships;
CREATE POLICY "Respond to own incoming request"
ON public.friendships
FOR UPDATE
TO authenticated
USING ((auth.uid() = addressee_id) OR (auth.uid() = requester_id))
WITH CHECK (
  status IN ('pending','accepted','blocked')
  AND (
    auth.uid() = addressee_id
    OR (auth.uid() = requester_id AND status <> 'accepted')
  )
);