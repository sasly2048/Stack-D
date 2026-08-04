-- is_room_participant() never excluded departed members (left_at IS NOT NULL),
-- unlike the breaks/rooms/participants SELECT policies which filter left_at IS NULL
-- directly. room_events, room_milestones, room_moderators, and room_scheduled_events
-- all gate SELECT through this helper, so a user who left a room retained read
-- access to those tables for that room indefinitely. Require left_at IS NULL here too.
CREATE OR REPLACE FUNCTION public.is_room_participant(_room_id uuid, _user_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.participants
    WHERE room_id = _room_id AND user_id = _user_id AND left_at IS NULL
  );
$$;
