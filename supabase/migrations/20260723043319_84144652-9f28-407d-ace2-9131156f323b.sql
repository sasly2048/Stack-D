
CREATE POLICY "host reads room reports"
  ON public.user_reports FOR SELECT
  TO authenticated
  USING (
    target_room_id IS NOT NULL
    AND EXISTS (
      SELECT 1 FROM public.rooms r
      WHERE r.id = user_reports.target_room_id
        AND r.host_id = auth.uid()
    )
  );

CREATE POLICY "host resolves room reports"
  ON public.user_reports FOR UPDATE
  TO authenticated
  USING (
    target_room_id IS NOT NULL
    AND EXISTS (
      SELECT 1 FROM public.rooms r
      WHERE r.id = user_reports.target_room_id
        AND r.host_id = auth.uid()
    )
  )
  WITH CHECK (
    target_room_id IS NOT NULL
    AND EXISTS (
      SELECT 1 FROM public.rooms r
      WHERE r.id = user_reports.target_room_id
        AND r.host_id = auth.uid()
    )
  );
