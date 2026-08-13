
DROP FUNCTION IF EXISTS public.mentorship_guard();

ALTER TABLE public.mentor_relationships
  ADD COLUMN IF NOT EXISTS initiator_id uuid;

UPDATE public.mentor_relationships SET initiator_id = mentor_id WHERE initiator_id IS NULL;

DROP POLICY IF EXISTS "mentorship insert as pending by either party" ON public.mentor_relationships;
CREATE POLICY "mentorship insert as pending by either party"
ON public.mentor_relationships FOR INSERT TO authenticated
WITH CHECK (
  (auth.uid() = mentor_id OR auth.uid() = mentee_id)
  AND mentor_id <> mentee_id
  AND status = 'pending'
  AND initiator_id = auth.uid()
);

DROP POLICY IF EXISTS "mentorship accept or decline by counterparty" ON public.mentor_relationships;
CREATE POLICY "mentorship accept or decline by counterparty"
ON public.mentor_relationships FOR UPDATE TO authenticated
USING (
  (auth.uid() = mentor_id OR auth.uid() = mentee_id)
  AND auth.uid() <> initiator_id
)
WITH CHECK (
  (auth.uid() = mentor_id OR auth.uid() = mentee_id)
  AND auth.uid() <> initiator_id
  AND status IN ('active', 'declined')
);

CREATE OR REPLACE FUNCTION public.mentorship_freeze_parties()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
BEGIN
  IF current_user IN ('authenticated', 'anon') THEN
    NEW.mentor_id := OLD.mentor_id;
    NEW.mentee_id := OLD.mentee_id;
    NEW.initiator_id := OLD.initiator_id;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS mentorship_freeze_parties ON public.mentor_relationships;
CREATE TRIGGER mentorship_freeze_parties
BEFORE UPDATE ON public.mentor_relationships
FOR EACH ROW EXECUTE FUNCTION public.mentorship_freeze_parties();
