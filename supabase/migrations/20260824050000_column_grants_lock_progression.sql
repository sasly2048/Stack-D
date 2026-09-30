-- =========================================================
-- P0 (Codex #1,#2,#3,#8,#9,#18): lock authority columns with column-level
-- UPDATE grants instead of relying on current_user in SECURITY DEFINER triggers
-- =========================================================
-- The column-freeze triggers (participants_protect_scoring,
-- rooms_protect_lifecycle, protect_profile_scoring, room_join_request_guard)
-- guard with `IF current_user IN ('authenticated','anon')`. But those trigger
-- functions are SECURITY DEFINER and owned by the migration role (postgres), so
-- inside them current_user = 'postgres', NEVER 'authenticated'. The guard branch
-- never runs for client calls, so the freezes are no-ops and a direct PATCH can
-- tamper integrity/xp/room lifecycle.
--
-- Fix with the canonical, bypass-proof Supabase pattern: REVOKE UPDATE from the
-- client roles, then GRANT UPDATE only on the columns a client may legitimately
-- change. PostgREST rejects any PATCH touching a non-granted column, and this
-- is enforced by Postgres privilege checks — independent of any trigger or
-- current_user value. SECURITY DEFINER RPCs (owned by postgres) still write every
-- column, so server-authoritative flows (finalize_focus_session, record_breach,
-- set_my_timezone, setTitle) are unaffected.
--
-- RLS still governs WHICH ROWS (auth.uid() = owner); these grants govern WHICH
-- COLUMNS. Both layers now hold without depending on the broken current_user
-- check. The triggers are left in place (harmless) but no longer load-bearing.
--
-- Idempotent: REVOKE/GRANT are declarative and repeatable.

-- ---------------------------------------------------------------------------
-- participants: client may only touch presence/cosmetic columns. Scoring and
-- identity columns are server-only (record_breach / finalize write them).
-- ---------------------------------------------------------------------------
REVOKE UPDATE ON public.participants FROM authenticated, anon;
GRANT UPDATE (display_name, last_heartbeat, left_at) ON public.participants TO authenticated;
-- integrity, breached, breach_reason, breach_at, user_id, room_id, id,
-- joined_at: NOT granted -> unwritable by clients.

-- ---------------------------------------------------------------------------
-- rooms: host may edit cosmetic/goal meta. Lifecycle, ownership, code and
-- server-maintained aggregates are server-only.
-- ---------------------------------------------------------------------------
REVOKE UPDATE ON public.rooms FROM authenticated, anon;
GRANT UPDATE (title, description, banner_url, pinned_message,
              collective_goal_seconds, shared_goal_hours, visibility)
  ON public.rooms TO authenticated;
-- status, started_at, ended_at, target_duration_seconds, host_id, code,
-- created_at, updated_at, collective_seconds, template_key, id: NOT granted.

-- ---------------------------------------------------------------------------
-- room_join_requests: a client never UPDATEs these directly — status
-- transitions go through respondToJoinRequest (moderator) / cancel paths that
-- run server-side. Revoke UPDATE entirely; identity/status can't be repointed.
-- ---------------------------------------------------------------------------
REVOKE UPDATE ON public.room_join_requests FROM authenticated, anon;
-- status/responded_at are changed by respondToJoinRequest, which still runs as
-- the user today. Grant just those two so the moderator flow keeps working;
-- user_id/room_id/display_name/id stay frozen so identity can't be repointed
-- (Codex #9). Moving the moderator check into a SECURITY DEFINER RPC (so status
-- is fully server-authoritative, Codex #10) is a scoped follow-up.
GRANT UPDATE (status, responded_at) ON public.room_join_requests TO authenticated;

-- ---------------------------------------------------------------------------
-- profiles: client may edit only the self-service cosmetic fields that
-- updateMyProfile writes. All progression columns are server-only.
-- ---------------------------------------------------------------------------
REVOKE UPDATE ON public.profiles FROM authenticated, anon;
GRANT UPDATE (display_name, bio, avatar_url, banner_gradient, banner_url,
              pinned_showcase,
              -- username trio: set by setUsername (with its own cooldown +
              -- unique-index checks). Not a progression/scoring surface; kept
              -- client-writable so that flow works. The unique index on
              -- username_canonical still prevents collisions.
              username, username_canonical, username_changed_at)
  ON public.profiles TO authenticated;
-- lifetime_xp, current_focus_streak, best_streak, total_focus_seconds,
-- prestige_level, productivity_dna, title, timezone, username,
-- username_canonical, username_changed_at, created_at, updated_at,
-- last_active_at, id: NOT granted. title/timezone are set via their own
-- SECURITY DEFINER RPCs below / set_my_timezone.

-- ---------------------------------------------------------------------------
-- equip_title(): now that profiles.title is client-unwritable, equipping a
-- title must go through a SECURITY DEFINER RPC that verifies the caller owns it
-- (in user_titles) before setting profiles.title. NULL clears the equipped
-- title. Runs as owner (postgres), so it writes the now-revoked column.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.equip_title(_title_id text)
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  _uid uuid := auth.uid();
  _name text;
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  IF _title_id IS NULL THEN
    UPDATE public.profiles SET title = NULL, updated_at = now() WHERE id = _uid;
    RETURN NULL;
  END IF;
  SELECT t.name INTO _name
  FROM public.user_titles ut
  JOIN public.titles t ON t.id = ut.title_id
  WHERE ut.user_id = _uid AND ut.title_id = _title_id;
  IF _name IS NULL THEN RAISE EXCEPTION 'not_owned' USING ERRCODE = 'check_violation'; END IF;
  UPDATE public.profiles SET title = _name, updated_at = now() WHERE id = _uid;
  RETURN _name;
END;
$$;
REVOKE ALL ON FUNCTION public.equip_title(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.equip_title(text) TO authenticated;
