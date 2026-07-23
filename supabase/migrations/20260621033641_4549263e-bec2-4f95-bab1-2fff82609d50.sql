
REVOKE ALL ON FUNCTION public.is_group_member(UUID, UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.record_breach(UUID, UUID, TEXT, public.breach_severity, INTEGER) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.finalize_focus_session(UUID, INTEGER, INTEGER, INTEGER, INTEGER, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.is_group_member(UUID, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.record_breach(UUID, UUID, TEXT, public.breach_severity, INTEGER) TO authenticated;
GRANT EXECUTE ON FUNCTION public.finalize_focus_session(UUID, INTEGER, INTEGER, INTEGER, INTEGER, TEXT) TO authenticated;
