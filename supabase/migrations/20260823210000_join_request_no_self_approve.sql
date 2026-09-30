-- =========================================================
-- P0 #7: room_join_requests — stop a user self-approving their own request
-- =========================================================
-- The UPDATE policy "User cancels own, moderator responds" is:
--   USING/CHECK ( auth.uid() = user_id OR is_room_moderator(room_id, auth.uid()) )
-- The comment says the requester may only *cancel* and a moderator *responds*,
-- but the OR lets the requester UPDATE their own row to status='approved'. The
-- join gate for request-only rooms (join_room) accepts any row with
-- status='approved', so a user can approve their own request and walk in.
--
-- RLS alone can't fix this: WITH CHECK sees only the final row, not the
-- (OLD.status -> NEW.status, by whom) transition. So a BEFORE UPDATE trigger
-- enforces the per-role transition rules for client callers:
--
--   * The requester (auth.uid() = OLD.user_id) may only move pending -> cancelled
--     (or leave status unchanged). They may NOT reach approved/denied.
--   * A moderator of the room may move pending -> approved/denied.
--
-- respondToJoinRequest() writes approved/denied through the user's client
-- (context.supabase, current_user = authenticated) after checking
-- is_room_moderator in app code, so the moderator branch must be allowed here —
-- the trigger re-checks is_room_moderator server-side, turning that app-code
-- check into an enforced rule. SECURITY DEFINER / service_role writers bypass
-- the guard entirely.
--
-- Idempotent: CREATE OR REPLACE + recreate trigger.

CREATE OR REPLACE FUNCTION public.room_join_request_guard()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE
  _uid uuid := auth.uid();
BEGIN
  -- Trusted writers (service role, SECURITY DEFINER routines) are unrestricted.
  IF current_user NOT IN ('authenticated', 'anon') THEN
    RETURN NEW;
  END IF;

  -- Status unchanged: a metadata-only update (e.g. message edit). Allow.
  IF NEW.status IS NOT DISTINCT FROM OLD.status THEN
    RETURN NEW;
  END IF;

  -- The requester may only cancel their own pending request.
  IF _uid = OLD.user_id THEN
    IF OLD.status = 'pending' AND NEW.status = 'cancelled' THEN
      RETURN NEW;
    END IF;
    RAISE EXCEPTION 'requester_may_only_cancel';
  END IF;

  -- A room moderator may approve or deny a pending request.
  IF public.is_room_moderator(OLD.room_id, _uid) THEN
    IF OLD.status = 'pending' AND NEW.status IN ('approved', 'denied') THEN
      RETURN NEW;
    END IF;
    RAISE EXCEPTION 'moderator_may_only_respond_to_pending';
  END IF;

  RAISE EXCEPTION 'not_authorized_to_change_join_request';
END;
$$;

DROP TRIGGER IF EXISTS room_join_request_guard ON public.room_join_requests;
CREATE TRIGGER room_join_request_guard
BEFORE UPDATE ON public.room_join_requests
FOR EACH ROW EXECUTE FUNCTION public.room_join_request_guard();

REVOKE ALL ON FUNCTION public.room_join_request_guard() FROM PUBLIC, anon, authenticated;
