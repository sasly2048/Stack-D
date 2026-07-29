
CREATE TABLE public.webhook_deliveries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  webhook_id UUID NOT NULL REFERENCES public.webhooks(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  event TEXT NOT NULL,
  status_code INT,
  ok BOOLEAN NOT NULL DEFAULT false,
  response_snippet TEXT,
  attempt INT NOT NULL DEFAULT 1,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX webhook_deliveries_wh_idx ON public.webhook_deliveries(webhook_id, created_at DESC);
CREATE INDEX webhook_deliveries_user_idx ON public.webhook_deliveries(user_id, created_at DESC);

GRANT SELECT ON public.webhook_deliveries TO authenticated;
GRANT ALL ON public.webhook_deliveries TO service_role;

ALTER TABLE public.webhook_deliveries ENABLE ROW LEVEL SECURITY;

CREATE POLICY "owner reads deliveries"
  ON public.webhook_deliveries FOR SELECT
  TO authenticated
  USING (user_id = auth.uid());
