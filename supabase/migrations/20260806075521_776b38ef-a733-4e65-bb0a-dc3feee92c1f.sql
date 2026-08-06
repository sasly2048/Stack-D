CREATE OR REPLACE FUNCTION public.are_friends(_a uuid, _b uuid)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO 'public'
AS $function$
  SELECT CASE
    WHEN auth.uid() IS NOT NULL AND auth.uid() <> _a AND auth.uid() <> _b THEN FALSE
    ELSE EXISTS (
      SELECT 1 FROM public.friendships
      WHERE status='accepted' AND (
        (requester_id=_a AND addressee_id=_b) OR
        (requester_id=_b AND addressee_id=_a)
      )
    )
  END;
$function$;

CREATE OR REPLACE FUNCTION public.is_group_member(_group_id uuid, _user_id uuid)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO 'public'
AS $function$
  SELECT CASE
    WHEN auth.uid() IS NOT NULL AND auth.uid() <> _user_id THEN FALSE
    ELSE EXISTS (
      SELECT 1 FROM public.group_members
      WHERE group_id = _group_id AND profile_id = _user_id
    )
  END;
$function$;

CREATE OR REPLACE FUNCTION public.is_room_host(_room_id uuid, _user_id uuid)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO 'public'
AS $function$
  SELECT CASE
    WHEN auth.uid() IS NOT NULL AND auth.uid() <> _user_id THEN FALSE
    ELSE EXISTS (SELECT 1 FROM public.rooms WHERE id = _room_id AND host_id = _user_id)
  END;
$function$;

CREATE OR REPLACE FUNCTION public.is_room_moderator(_room_id uuid, _user_id uuid)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO 'public'
AS $function$
  SELECT CASE
    WHEN auth.uid() IS NOT NULL AND auth.uid() <> _user_id THEN FALSE
    ELSE (
      public.is_room_host(_room_id, _user_id)
      OR EXISTS (SELECT 1 FROM public.room_moderators WHERE room_id = _room_id AND user_id = _user_id)
    )
  END;
$function$;

CREATE OR REPLACE FUNCTION public.is_room_participant(_room_id uuid, _user_id uuid)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO 'public'
AS $function$
  SELECT CASE
    WHEN auth.uid() IS NOT NULL AND auth.uid() <> _user_id THEN FALSE
    ELSE EXISTS (
      SELECT 1 FROM public.participants
      WHERE room_id = _room_id AND user_id = _user_id AND left_at IS NULL
    )
  END;
$function$;

REVOKE ALL ON FUNCTION public.are_friends(uuid, uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.is_group_member(uuid, uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.is_room_host(uuid, uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.is_room_moderator(uuid, uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.is_room_participant(uuid, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.are_friends(uuid, uuid) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.is_group_member(uuid, uuid) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.is_room_host(uuid, uuid) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.is_room_moderator(uuid, uuid) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.is_room_participant(uuid, uuid) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.room_code_exists(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.room_code_exists(text) TO authenticated, service_role;