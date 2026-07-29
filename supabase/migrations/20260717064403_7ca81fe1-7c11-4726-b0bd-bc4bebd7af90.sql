
-- Revoke public EXECUTE on all SECURITY DEFINER functions, then grant narrowly.

-- Trigger functions & internal helpers: server/trigger only
REVOKE ALL ON FUNCTION public.update_updated_at_column() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.handle_new_user() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.check_and_record_hit(text, integer, integer) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.recent_auth_failures(text, text, integer) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.recent_auth_failures(text, text, integer, text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.record_auth_alert_if_new(text, text, integer, integer, jsonb) FROM PUBLIC, anon, authenticated;

-- RLS helper functions: used inside policies (run as definer via RLS); no direct client execute needed
REVOKE ALL ON FUNCTION public.is_group_member(uuid, uuid) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.is_room_participant(uuid, uuid) FROM PUBLIC, anon, authenticated;

-- Client-callable RPCs: signed-in users only
REVOKE ALL ON FUNCTION public.record_breach(uuid, uuid, text, breach_severity, integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.record_breach(uuid, uuid, text, breach_severity, integer) TO authenticated;

REVOKE ALL ON FUNCTION public.room_code_exists(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.room_code_exists(text) TO authenticated;

REVOKE ALL ON FUNCTION public.claim_room_seat(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.claim_room_seat(text) TO authenticated;

REVOKE ALL ON FUNCTION public.finalize_focus_session(uuid, integer, integer, integer, integer, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.finalize_focus_session(uuid, integer, integer, integer, integer, text) TO authenticated;
