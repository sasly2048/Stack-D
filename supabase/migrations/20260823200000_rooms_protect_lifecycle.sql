-- =========================================================
-- P0 #6: rooms — freeze lifecycle/authority columns against client writes
-- =========================================================
-- "Only host can update room" lets the host UPDATE ANY column of their room via
-- a direct REST call, including status, started_at, ended_at,
-- target_duration_seconds, host_id and code. Two earlier migrations already
-- moved these transitions server-side and said so explicitly:
--
--   * 20260809181000 (start_focus_session): "the single most important input to
--     the reward system came from an unverified client clock" — it made the
--     server write started_at, but the RLS policy still lets the host write it.
--   * 20260810000000 (reap_stale_rooms): status/ended_at owned by the cron sweep
--     and finalize_focus_session.
--
-- A host writing started_at directly can backdate the session to inflate
-- elapsed focus time, or raise target_duration_seconds mid-session to lift the
-- clamp ceiling that finalize_focus_session scores against. Close the door the
-- server-owned RPCs were meant to be the only entrance to.
--
-- Mirror participants_protect_scoring: a BEFORE UPDATE trigger freezes the
-- lifecycle/authority columns to OLD for client callers (authenticated/anon).
-- The cosmetic columns updateRoomMeta writes (title, description, banner_url,
-- pinned_message, collective_goal_seconds, visibility) stay host-editable.
-- SECURITY DEFINER RPCs (start_focus_session, reap_stale_rooms,
-- finalize_focus_session, dispatch_group_sprint) run as the table owner and are
-- unaffected — they remain the only writers of lifecycle state.
--
-- Idempotent: CREATE OR REPLACE + recreate trigger.

CREATE OR REPLACE FUNCTION public.rooms_protect_lifecycle()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
BEGIN
  IF current_user IN ('authenticated', 'anon') THEN
    NEW.status                  := OLD.status;
    NEW.started_at              := OLD.started_at;
    NEW.ended_at                := OLD.ended_at;
    NEW.target_duration_seconds := OLD.target_duration_seconds;
    NEW.host_id                 := OLD.host_id;
    NEW.code                    := OLD.code;
    NEW.created_at              := OLD.created_at;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS rooms_protect_lifecycle ON public.rooms;
CREATE TRIGGER rooms_protect_lifecycle
BEFORE UPDATE ON public.rooms
FOR EACH ROW EXECUTE FUNCTION public.rooms_protect_lifecycle();

REVOKE ALL ON FUNCTION public.rooms_protect_lifecycle() FROM PUBLIC, anon, authenticated;
