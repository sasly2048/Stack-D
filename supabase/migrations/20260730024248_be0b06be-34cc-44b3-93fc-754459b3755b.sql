ALTER TABLE public.room_templates
  ADD COLUMN IF NOT EXISTS access TEXT NOT NULL DEFAULT 'public';

ALTER TABLE public.room_templates
  DROP CONSTRAINT IF EXISTS room_templates_access_check;
ALTER TABLE public.room_templates
  ADD CONSTRAINT room_templates_access_check CHECK (access IN ('public', 'restricted'));

DROP POLICY IF EXISTS "Templates readable by authenticated" ON public.room_templates;

CREATE POLICY "Public templates readable by authenticated"
  ON public.room_templates
  FOR SELECT
  TO authenticated
  USING (access = 'public');