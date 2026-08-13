-- 1. Internal trigger helpers must not be callable through the public API.
REVOKE ALL ON FUNCTION public.participants_protect_scoring() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.mentorship_freeze_parties() FROM PUBLIC, anon, authenticated;

-- 2. Harden participant anti-cheat columns against direct client writes.
CREATE OR REPLACE FUNCTION public.participants_protect_scoring()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
BEGIN
  -- Trusted server-side paths run as the table owner / service_role and are
  -- allowed to write scoring fields. End-user roles are not.
  IF current_user IN ('authenticated', 'anon') THEN
    IF NEW.integrity IS DISTINCT FROM OLD.integrity
       OR NEW.breached IS DISTINCT FROM OLD.breached
       OR NEW.breach_reason IS DISTINCT FROM OLD.breach_reason
       OR NEW.breach_at IS DISTINCT FROM OLD.breach_at
       OR NEW.user_id IS DISTINCT FROM OLD.user_id
       OR NEW.room_id IS DISTINCT FROM OLD.room_id THEN
      RAISE EXCEPTION 'scoring fields are read-only; use record_breach/finalize_focus_session'
        USING ERRCODE = '42501';
    END IF;
  END IF;
  RETURN NEW;
END;
$function$;

REVOKE ALL ON FUNCTION public.participants_protect_scoring() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS participants_protect_scoring ON public.participants;
CREATE TRIGGER participants_protect_scoring
  BEFORE UPDATE ON public.participants
  FOR EACH ROW EXECUTE FUNCTION public.participants_protect_scoring();