ALTER TABLE public.focus_groups
  ADD COLUMN IF NOT EXISTS active_session_id UUID REFERENCES public.rooms(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS active_session_code TEXT,
  ADD COLUMN IF NOT EXISTS active_session_started_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS active_session_expires_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS focus_groups_active_session_expires_idx
  ON public.focus_groups (active_session_expires_at)
  WHERE active_session_id IS NOT NULL;

-- Allow group members (not just creator) to update the active_session_* fields
-- when launching a sprint. Existing "Creator updates group" policy stays.
CREATE POLICY "Members update active session"
  ON public.focus_groups FOR UPDATE
  TO authenticated
  USING (public.is_group_member(id, auth.uid()))
  WITH CHECK (public.is_group_member(id, auth.uid()));

-- Ensure focus_groups changes stream over Realtime (postgres_changes).
ALTER TABLE public.focus_groups REPLICA IDENTITY FULL;
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables
    WHERE pubname = 'supabase_realtime' AND tablename = 'focus_groups'
  ) THEN
    EXECUTE 'ALTER PUBLICATION supabase_realtime ADD TABLE public.focus_groups';
  END IF;
END$$;