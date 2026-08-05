-- Internal-only SECURITY DEFINER helpers: revoke direct API execution.
REVOKE ALL ON FUNCTION public.check_and_record_hit(text, integer, integer) FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.recent_auth_failures(text, text, integer) FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.recent_auth_failures(text, text, integer, text) FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.record_auth_alert_if_new(text, text, integer, integer, jsonb) FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.enqueue_email(text, jsonb) FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.delete_email(text, bigint) FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.read_email_batch(text, integer, integer) FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.move_to_dlq(text, text, bigint, jsonb) FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.email_queue_dispatch() FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.email_queue_wake() FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.evaluate_achievements(uuid, uuid) FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.evaluate_challenges(uuid, uuid) FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.evaluate_milestones(uuid) FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.refresh_personality(uuid) FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.ensure_current_season() FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.handle_new_user() FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.friendship_accepted_activity() FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.rooms_add_collective_seconds() FROM anon, authenticated;

-- Trusted server-side callers keep access.
GRANT EXECUTE ON FUNCTION public.check_and_record_hit(text, integer, integer) TO service_role;
GRANT EXECUTE ON FUNCTION public.recent_auth_failures(text, text, integer) TO service_role;
GRANT EXECUTE ON FUNCTION public.recent_auth_failures(text, text, integer, text) TO service_role;
GRANT EXECUTE ON FUNCTION public.record_auth_alert_if_new(text, text, integer, integer, jsonb) TO service_role;
GRANT EXECUTE ON FUNCTION public.enqueue_email(text, jsonb) TO service_role;
GRANT EXECUTE ON FUNCTION public.delete_email(text, bigint) TO service_role;
GRANT EXECUTE ON FUNCTION public.read_email_batch(text, integer, integer) TO service_role;
GRANT EXECUTE ON FUNCTION public.move_to_dlq(text, text, bigint, jsonb) TO service_role;
GRANT EXECUTE ON FUNCTION public.email_queue_dispatch() TO service_role, postgres;
GRANT EXECUTE ON FUNCTION public.evaluate_achievements(uuid, uuid) TO service_role;
GRANT EXECUTE ON FUNCTION public.evaluate_challenges(uuid, uuid) TO service_role;
GRANT EXECUTE ON FUNCTION public.evaluate_milestones(uuid) TO service_role;
GRANT EXECUTE ON FUNCTION public.refresh_personality(uuid) TO service_role;
GRANT EXECUTE ON FUNCTION public.ensure_current_season() TO service_role;