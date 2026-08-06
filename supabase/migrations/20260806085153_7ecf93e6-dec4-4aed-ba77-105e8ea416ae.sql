REVOKE ALL ON FUNCTION public.room_code_exists(text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.room_code_exists(text) TO service_role;