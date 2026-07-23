CREATE OR REPLACE FUNCTION public.recent_auth_failures(_provider text, _email text, _window_seconds integer, _ip text DEFAULT NULL)
 RETURNS integer
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
  SELECT COUNT(*)::INT
  FROM public.auth_attempts
  WHERE provider = _provider
    AND email = _email
    AND success = false
    AND at > now() - make_interval(secs => _window_seconds)
    AND (_ip IS NULL OR ip::text = _ip);
$function$;

REVOKE ALL ON FUNCTION public.recent_auth_failures(text, text, integer, text) FROM PUBLIC, anon, authenticated;