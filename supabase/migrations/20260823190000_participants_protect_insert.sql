-- =========================================================
-- P0 #4: participants — also protect scoring/breach columns on INSERT
-- =========================================================
-- participants_protect_scoring (20260813002936) freezes integrity, breached,
-- breach_reason, breach_at, user_id and room_id on UPDATE, so a client cannot
-- clear a breach or inflate integrity after joining. But it only fired
-- BEFORE UPDATE. The INSERT policy ("Users can join as themselves") lets a user
-- insert their own participant row with any column values, so a crafted direct
-- REST join could seed e.g. breached=false with a chosen integrity, or (more to
-- the point) re-join mid-session with a clean row.
--
-- Today those seeded values happen to match the column defaults, so the
-- exploit yields nothing — but relying on "the safe value equals the default"
-- is fragile the moment a default changes. Close the trust-boundary gap: force
-- the scoring/breach columns to their safe baseline for any client INSERT too.
-- A fresh join is always breached=false, integrity=100, no breach metadata —
-- exactly what a legitimate join produces. record_breach() runs SECURITY
-- DEFINER (current_user = table owner), so it still writes freely.
--
-- Idempotent: CREATE OR REPLACE the function, recreate the trigger for both ops.

CREATE OR REPLACE FUNCTION public.participants_protect_scoring()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
BEGIN
  IF current_user IN ('authenticated', 'anon') THEN
    IF TG_OP = 'INSERT' THEN
      -- A client join always starts clean; it may not seed breach/integrity.
      NEW.integrity     := 100;
      NEW.breached      := false;
      NEW.breach_reason := NULL;
      NEW.breach_at     := NULL;
    ELSE
      -- UPDATE: freeze the authority columns to their previous values.
      NEW.integrity     := OLD.integrity;
      NEW.breached      := OLD.breached;
      NEW.breach_reason := OLD.breach_reason;
      NEW.breach_at     := OLD.breach_at;
      NEW.user_id       := OLD.user_id;
      NEW.room_id       := OLD.room_id;
    END IF;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS participants_protect_scoring ON public.participants;
CREATE TRIGGER participants_protect_scoring
BEFORE INSERT OR UPDATE ON public.participants
FOR EACH ROW EXECUTE FUNCTION public.participants_protect_scoring();
