-- Lock down the private export bucket: only its owner may read/write objects.
CREATE POLICY "export_bucket_owner_select" ON storage.objects
  FOR SELECT TO authenticated
  USING (bucket_id = 'database_export_15_08_26' AND owner = auth.uid());

CREATE POLICY "export_bucket_owner_insert" ON storage.objects
  FOR INSERT TO authenticated
  WITH CHECK (bucket_id = 'database_export_15_08_26' AND owner = auth.uid());

CREATE POLICY "export_bucket_owner_update" ON storage.objects
  FOR UPDATE TO authenticated
  USING (bucket_id = 'database_export_15_08_26' AND owner = auth.uid())
  WITH CHECK (bucket_id = 'database_export_15_08_26' AND owner = auth.uid());

CREATE POLICY "export_bucket_owner_delete" ON storage.objects
  FOR DELETE TO authenticated
  USING (bucket_id = 'database_export_15_08_26' AND owner = auth.uid());