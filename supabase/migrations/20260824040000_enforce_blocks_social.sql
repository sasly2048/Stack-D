-- =========================================================
-- P2-17: social/blocking integrity — blocks apply to social interactions,
-- not just friend requests
-- =========================================================
-- P1 #14 (20260824020000) stopped a blocked user from sending a FRIEND REQUEST,
-- but a block is supposed to sever interaction generally. Two directed social
-- surfaces still ignored it:
--
--   * session_reactions — user B can react (emoji) on user A's focus session
--     even when A blocked B. The session's owner is focus_history.profile_id.
--   * mentor_relationships — user B can pair (mentor/mentee) with user A across
--     a block.
--
-- Enforce at the database with BEFORE INSERT triggers so every client (web,
-- Android, direct REST) is covered, reusing the symmetric blocks_exist() helper
-- from #14. Guarded to client callers only; SECURITY DEFINER / service_role
-- writes stay trusted.
--
-- Idempotent: CREATE OR REPLACE fns, DROP/CREATE triggers.

-- 1) session_reactions: reject a reaction between blocked users. The reactor is
--    NEW.user_id; the session owner is looked up from focus_history.
CREATE OR REPLACE FUNCTION public.reaction_block_guard()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  _owner uuid;
BEGIN
  IF current_user IN ('authenticated', 'anon') THEN
    SELECT profile_id INTO _owner FROM public.focus_history WHERE id = NEW.session_id;
    IF _owner IS NOT NULL AND _owner <> NEW.user_id
       AND public.blocks_exist(NEW.user_id, _owner) THEN
      RAISE EXCEPTION 'blocked' USING ERRCODE = 'check_violation';
    END IF;
  END IF;
  RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION public.reaction_block_guard() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS session_reactions_block_guard ON public.session_reactions;
CREATE TRIGGER session_reactions_block_guard
  BEFORE INSERT ON public.session_reactions
  FOR EACH ROW EXECUTE FUNCTION public.reaction_block_guard();

-- 2) mentor_relationships: reject a pairing between blocked users (either role).
CREATE OR REPLACE FUNCTION public.mentorship_block_guard()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF current_user IN ('authenticated', 'anon') THEN
    IF public.blocks_exist(NEW.mentor_id, NEW.mentee_id) THEN
      RAISE EXCEPTION 'blocked' USING ERRCODE = 'check_violation';
    END IF;
  END IF;
  RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION public.mentorship_block_guard() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS mentor_relationships_block_guard ON public.mentor_relationships;
CREATE TRIGGER mentor_relationships_block_guard
  BEFORE INSERT ON public.mentor_relationships
  FOR EACH ROW EXECUTE FUNCTION public.mentorship_block_guard();
