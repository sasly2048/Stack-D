
-- Alert dedupe ledger
CREATE TABLE public.auth_alerts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  kind TEXT NOT NULL,
  subject TEXT NOT NULL,         -- e.g. normalized email, or "ip:1.2.3.4"
  failure_count INT NOT NULL DEFAULT 0,
  details JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX auth_alerts_kind_subject_created_at_idx
  ON public.auth_alerts (kind, subject, created_at DESC);

-- Service role only; no anon/authenticated grants. Reads happen via server.
GRANT ALL ON public.auth_alerts TO service_role;
ALTER TABLE public.auth_alerts ENABLE ROW LEVEL SECURITY;
-- (no policies — locked to service_role)

-- Atomic dedupe: returns the inserted row id if no recent alert of same
-- (kind, subject) exists within _cooldown_seconds; otherwise NULL.
CREATE OR REPLACE FUNCTION public.record_auth_alert_if_new(
  _kind TEXT, _subject TEXT, _cooldown_seconds INT,
  _failure_count INT, _details JSONB
) RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  _id UUID;
  _cutoff TIMESTAMPTZ := now() - make_interval(secs => _cooldown_seconds);
BEGIN
  PERFORM 1 FROM public.auth_alerts
   WHERE kind = _kind AND subject = _subject AND created_at > _cutoff
   LIMIT 1;
  IF FOUND THEN RETURN NULL; END IF;
  INSERT INTO public.auth_alerts (kind, subject, failure_count, details)
  VALUES (_kind, _subject, COALESCE(_failure_count,0), COALESCE(_details,'{}'::jsonb))
  RETURNING id INTO _id;
  RETURN _id;
END;
$$;
REVOKE EXECUTE ON FUNCTION public.record_auth_alert_if_new(TEXT,TEXT,INT,INT,JSONB) FROM PUBLIC, anon, authenticated;
