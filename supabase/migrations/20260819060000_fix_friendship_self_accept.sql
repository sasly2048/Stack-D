-- =========================================================
-- SECURITY: only the addressee can accept a friend request
-- =========================================================
-- The "Respond to own incoming request" UPDATE policy allowed EITHER party to
-- update the row with the same USING/WITH CHECK. So the REQUESTER could set
-- their own pending request to 'accepted', self-granting friendship — which,
-- via are_friends(), unlocks another user's friends-only data (activity_events,
-- user_achievements, user_titles, session_reactions).
--
-- RLS alone can't express "you didn't just flip status to accepted" (it can't
-- compare OLD vs NEW), so enforcement is a SECURITY DEFINER trigger, mirroring
-- the mentor_relationships fix. The policy still scopes UPDATE to the two
-- parties; the trigger blocks the requester from activating the request.

CREATE OR REPLACE FUNCTION public.friendship_guard()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  _uid uuid := auth.uid();
BEGIN
  -- Service role / internal writes bypass (no auth.uid()).
  IF _uid IS NULL THEN
    RETURN NEW;
  END IF;

  -- The requester may never move a request to 'accepted' — only the addressee
  -- can accept (or decline). The requester can still cancel via DELETE.
  IF OLD.status = 'pending'
     AND NEW.status = 'accepted'
     AND _uid <> OLD.addressee_id THEN
    RAISE EXCEPTION 'only the addressee can accept a friend request';
  END IF;

  -- Identities are immutable on update — you can't repoint a friendship.
  NEW.requester_id := OLD.requester_id;
  NEW.addressee_id := OLD.addressee_id;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS friendships_guard ON public.friendships;
CREATE TRIGGER friendships_guard
  BEFORE UPDATE ON public.friendships
  FOR EACH ROW EXECUTE FUNCTION public.friendship_guard();

-- Trigger function: never client-callable directly, and it takes no arguments,
-- but revoke anyway for consistency with the permission hygiene test.
REVOKE ALL ON FUNCTION public.friendship_guard() FROM PUBLIC, anon, authenticated;
