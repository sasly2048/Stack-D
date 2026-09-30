-- =========================================================
-- P1 #14: enforce user_blocks — a block must actually block
-- =========================================================
-- user_blocks (blocker_id -> blocked_id) is written by blockUser() and read
-- only to list your own blocks. Nothing consults it to stop the blocked user
-- from interacting, so a "block" is cosmetic: a blocked user can still send
-- friend requests. This closes the friend-request vector at the database, so
-- every client (web, Android, direct REST) is covered.
--
-- A block is stored directionally but takes effect symmetrically: if A blocked
-- B, neither A->B nor B->A friendship requests may be created. Enforcement is a
-- BEFORE INSERT trigger on friendships (RLS WITH CHECK can't reach another
-- table cleanly for both directions), backed by a SECURITY DEFINER helper so
-- the check sees block rows the requesting user can't read directly.
--
-- Scope note: this migration enforces blocks on friend requests only. Profile
-- visibility and room-join blocking are broader read-path surfaces (many RLS
-- policies) and are left as a separate, scoped change — see #14 follow-up.
--
-- Idempotent: CREATE OR REPLACE helper + trigger fn, recreate trigger.

-- Symmetric block check: does a block row exist in either direction?
CREATE OR REPLACE FUNCTION public.blocks_exist(_a uuid, _b uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.user_blocks
    WHERE (blocker_id = _a AND blocked_id = _b)
       OR (blocker_id = _b AND blocked_id = _a)
  );
$$;
REVOKE ALL ON FUNCTION public.blocks_exist(uuid, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.blocks_exist(uuid, uuid) TO authenticated;

CREATE OR REPLACE FUNCTION public.friendship_block_guard()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  -- Client callers only; SECURITY DEFINER / service-role writes are trusted.
  IF current_user IN ('authenticated', 'anon') THEN
    IF public.blocks_exist(NEW.requester_id, NEW.addressee_id) THEN
      RAISE EXCEPTION 'blocked' USING ERRCODE = 'check_violation';
    END IF;
  END IF;
  RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION public.friendship_block_guard() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS friendships_block_guard ON public.friendships;
CREATE TRIGGER friendships_block_guard
  BEFORE INSERT ON public.friendships
  FOR EACH ROW EXECUTE FUNCTION public.friendship_block_guard();
