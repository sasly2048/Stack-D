
-- Fix infinite recursion between rooms and participants SELECT policies
-- by routing cross-table checks through SECURITY DEFINER helpers.

CREATE OR REPLACE FUNCTION public.is_room_host(_room_id uuid, _user_id uuid)
RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
  SELECT EXISTS (SELECT 1 FROM public.rooms WHERE id = _room_id AND host_id = _user_id);
$$;

REVOKE ALL ON FUNCTION public.is_room_host(uuid, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.is_room_host(uuid, uuid) TO authenticated, service_role;

-- Rooms: allow read if host OR a participant (via helper, no self-referential subquery)
DROP POLICY IF EXISTS "Host or active participant can read room" ON public.rooms;
CREATE POLICY "Host or active participant can read room"
ON public.rooms FOR SELECT
USING (
  auth.uid() = host_id
  OR public.is_room_participant(id, auth.uid())
);

-- Participants: replace the rooms subquery with the host helper
DROP POLICY IF EXISTS "Same-room active members can read participants" ON public.participants;
CREATE POLICY "Same-room active members can read participants"
ON public.participants FOR SELECT
USING (
  auth.uid() = user_id
  OR public.is_room_host(room_id, auth.uid())
  OR public.is_room_participant(room_id, auth.uid())
);

DROP POLICY IF EXISTS "Users can update own participant or host can update any" ON public.participants;
CREATE POLICY "Users can update own participant or host can update any"
ON public.participants FOR UPDATE
USING (auth.uid() = user_id OR public.is_room_host(room_id, auth.uid()))
WITH CHECK (auth.uid() = user_id OR public.is_room_host(room_id, auth.uid()));

DROP POLICY IF EXISTS "Users can remove own participant or host can remove any" ON public.participants;
CREATE POLICY "Users can remove own participant or host can remove any"
ON public.participants FOR DELETE
USING (auth.uid() = user_id OR public.is_room_host(room_id, auth.uid()));
