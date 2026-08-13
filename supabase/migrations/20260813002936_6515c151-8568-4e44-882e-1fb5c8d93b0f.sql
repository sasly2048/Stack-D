
-- 1. season_participants: block direct client XP writes
DROP POLICY IF EXISTS "season_participants owner write" ON public.season_participants;
DROP POLICY IF EXISTS "season_participants owner update" ON public.season_participants;
REVOKE INSERT, UPDATE ON public.season_participants FROM authenticated;
GRANT ALL ON public.season_participants TO service_role;

CREATE OR REPLACE FUNCTION public.join_season(_season_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE _uid uuid := auth.uid();
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  IF NOT EXISTS (SELECT 1 FROM public.seasons WHERE id = _season_id) THEN
    RAISE EXCEPTION 'season_not_found';
  END IF;
  INSERT INTO public.season_participants (season_id, user_id, xp)
  VALUES (_season_id, _uid, 0)
  ON CONFLICT (season_id, user_id) DO NOTHING;
END;
$$;
REVOKE ALL ON FUNCTION public.join_season(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.join_season(uuid) TO authenticated;

-- 2. participants: freeze scoring/breach columns against direct client updates
CREATE OR REPLACE FUNCTION public.participants_protect_scoring()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
BEGIN
  IF current_user IN ('authenticated', 'anon') THEN
    NEW.integrity := OLD.integrity;
    NEW.breached := OLD.breached;
    NEW.breach_reason := OLD.breach_reason;
    NEW.breach_at := OLD.breach_at;
    NEW.user_id := OLD.user_id;
    NEW.room_id := OLD.room_id;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS participants_protect_scoring ON public.participants;
CREATE TRIGGER participants_protect_scoring
BEFORE UPDATE ON public.participants
FOR EACH ROW EXECUTE FUNCTION public.participants_protect_scoring();

-- 3. mentor_relationships: consent-based pairing
ALTER TABLE public.mentor_relationships ALTER COLUMN status SET DEFAULT 'pending';

DROP POLICY IF EXISTS "mentorship writable by either party" ON public.mentor_relationships;

CREATE POLICY "mentorship insert as pending by either party"
ON public.mentor_relationships FOR INSERT TO authenticated
WITH CHECK (
  (auth.uid() = mentor_id OR auth.uid() = mentee_id)
  AND mentor_id <> mentee_id
  AND status = 'pending'
);

-- Only the invited party (the one who did not create the row) can activate it.
CREATE POLICY "mentorship accept or decline by counterparty"
ON public.mentor_relationships FOR UPDATE TO authenticated
USING (auth.uid() = mentor_id OR auth.uid() = mentee_id)
WITH CHECK (
  (auth.uid() = mentor_id OR auth.uid() = mentee_id)
  AND status IN ('pending', 'active', 'declined')
);

CREATE POLICY "mentorship delete by either party"
ON public.mentor_relationships FOR DELETE TO authenticated
USING (auth.uid() = mentor_id OR auth.uid() = mentee_id);

CREATE OR REPLACE FUNCTION public.mentorship_guard()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE _uid uuid := auth.uid();
BEGIN
  IF current_user NOT IN ('authenticated', 'anon') THEN RETURN NEW; END IF;
  IF TG_OP = 'INSERT' THEN
    NEW.status := 'pending';
    RETURN NEW;
  END IF;
  -- Activation requires the counterparty (the invitee) to act.
  IF OLD.status = 'pending' AND NEW.status = 'active' THEN
    IF _uid = OLD.created_by_uid_placeholder() THEN
      RAISE EXCEPTION 'inviter_cannot_accept';
    END IF;
  END IF;
  NEW.mentor_id := OLD.mentor_id;
  NEW.mentee_id := OLD.mentee_id;
  RETURN NEW;
END;
$$;
