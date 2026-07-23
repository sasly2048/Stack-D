CREATE OR REPLACE FUNCTION public.dispatch_group_sprint(_group_id uuid, _active_session_id uuid, _active_session_code text, _started_at timestamp with time zone, _expires_at timestamp with time zone)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
DECLARE
  _uid uuid := auth.uid();
  _over_user boolean;
  _over_group boolean;
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  IF NOT public.is_group_member(_group_id, _uid) THEN
    RAISE EXCEPTION 'not_group_member';
  END IF;
  IF _active_session_id IS NULL OR _active_session_code IS NULL
     OR _started_at IS NULL OR _expires_at IS NULL THEN
    RAISE EXCEPTION 'bad_input';
  END IF;
  IF _expires_at <= _started_at THEN
    RAISE EXCEPTION 'bad_window';
  END IF;

  -- Rate limit: max 3 dispatches per user per 60s and 5 per group per 60s.
  _over_user  := public.check_and_record_hit('grp_sprint_user:' || _uid::text,   60, 3);
  _over_group := public.check_and_record_hit('grp_sprint_group:' || _group_id::text, 60, 5);
  IF _over_user OR _over_group THEN
    RAISE EXCEPTION 'rate_limited';
  END IF;

  UPDATE public.focus_groups
     SET active_session_id = _active_session_id,
         active_session_code = upper(_active_session_code),
         active_session_started_at = _started_at,
         active_session_expires_at = _expires_at,
         updated_at = now()
   WHERE id = _group_id;
END;
$function$;