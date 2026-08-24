-- =========================================================
-- P0 (Codex #10,#12): fix trigger guards that used current_user
-- =========================================================
-- friendship_block_guard, reaction_block_guard, mentorship_block_guard,
-- room_join_request_guard and mentorship_freeze_parties all decided "is a
-- client calling?" with `current_user IN ('authenticated','anon')`. But they are
-- SECURITY DEFINER owned by postgres, so inside them current_user='postgres' —
-- the branch never matched. Result: block enforcement and join-request
-- self-approval protection never actually ran (Codex #10, #12).
--
-- The correct signal — already used by friendship_guard (20260819060000) — is
-- auth.uid(): it is the JWT subject, NON-NULL whenever a real user is behind
-- the request (directly or via an RPC) and NULL for service_role/internal
-- writes. So:
--   enforce-when-client   : IF auth.uid() IS NOT NULL THEN <checks>
--   bypass-when-trusted   : IF auth.uid() IS NULL THEN RETURN NEW;
--
-- (The similarly-broken mentorship_guard() is an orphan — no trigger executes
-- it — so it's left alone; the wired trigger is mentorship_freeze_parties.)
--
-- Idempotent: CREATE OR REPLACE only; triggers already exist and are unchanged.

-- friendship_block_guard --------------------------------------------------
CREATE OR REPLACE FUNCTION public.friendship_block_guard()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF auth.uid() IS NOT NULL THEN
    IF public.blocks_exist(NEW.requester_id, NEW.addressee_id) THEN
      RAISE EXCEPTION 'blocked' USING ERRCODE = 'check_violation';
    END IF;
  END IF;
  RETURN NEW;
END;
$$;

-- reaction_block_guard ----------------------------------------------------
CREATE OR REPLACE FUNCTION public.reaction_block_guard()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  _owner uuid;
BEGIN
  IF auth.uid() IS NOT NULL THEN
    SELECT profile_id INTO _owner FROM public.focus_history WHERE id = NEW.session_id;
    IF _owner IS NOT NULL AND _owner <> NEW.user_id
       AND public.blocks_exist(NEW.user_id, _owner) THEN
      RAISE EXCEPTION 'blocked' USING ERRCODE = 'check_violation';
    END IF;
  END IF;
  RETURN NEW;
END;
$$;

-- mentorship_block_guard --------------------------------------------------
CREATE OR REPLACE FUNCTION public.mentorship_block_guard()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF auth.uid() IS NOT NULL THEN
    IF public.blocks_exist(NEW.mentor_id, NEW.mentee_id) THEN
      RAISE EXCEPTION 'blocked' USING ERRCODE = 'check_violation';
    END IF;
  END IF;
  RETURN NEW;
END;
$$;

-- room_join_request_guard -------------------------------------------------
CREATE OR REPLACE FUNCTION public.room_join_request_guard()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE
  _uid uuid := auth.uid();
BEGIN
  -- Trusted writers (service role / internal) have no auth context.
  IF _uid IS NULL THEN
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

-- mentorship_freeze_parties -----------------------------------------------
-- This is the ACTUAL wired mentorship trigger (mentorship_guard() is an
-- orphaned function with no trigger). It froze mentor_id/mentee_id/initiator_id
-- but under the broken current_user guard, so identities were mutable (Codex
-- #11, #32). Additionally enforce the invitee-only activation rule here (only
-- the party who did NOT initiate may move pending -> active), since the RLS
-- policy only checks that the actor is *a* party, not the invitee.
CREATE OR REPLACE FUNCTION public.mentorship_freeze_parties()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE _uid uuid := auth.uid();
BEGIN
  IF _uid IS NULL THEN RETURN NEW; END IF;   -- trusted/internal writes

  -- Only the invitee (not the initiator) may activate a pending pairing.
  IF OLD.status = 'pending' AND NEW.status = 'active' AND _uid = OLD.initiator_id THEN
    RAISE EXCEPTION 'inviter_cannot_accept' USING ERRCODE = 'check_violation';
  END IF;

  -- Identities are immutable on update — can't repoint the relationship.
  NEW.mentor_id     := OLD.mentor_id;
  NEW.mentee_id     := OLD.mentee_id;
  NEW.initiator_id  := OLD.initiator_id;
  RETURN NEW;
END;
$$;
