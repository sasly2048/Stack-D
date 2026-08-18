-- =========================================================
-- WEBHOOK RELIABILITY — received -> processing -> processed/failed
-- =========================================================
-- The original flow recorded a webhook event id BEFORE provisioning the
-- subscription. If grant_subscription() then failed, the event was already
-- "seen", so Razorpay's retry was deduped away and the paid user never got
-- access — a silent payment-without-provisioning failure.
--
-- This adds an explicit status so an event is only skipped once it has actually
-- been PROCESSED. A 'processing'/'failed' row stays retryable and idempotent.

ALTER TABLE public.webhook_events
  ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'processed'
    CHECK (status IN ('processing', 'processed', 'failed'));

-- Existing rows predate this column; they were only ever inserted on success,
-- so 'processed' (the default above) is correct for them.

-- ---------------------------------------------------------------------------
-- begin_webhook_event() — claim an event for processing. Returns:
--   'new'       -> first time (or a prior failed/processing attempt): PROCEED.
--   'processed' -> already fully handled: SKIP.
-- Row-locked so two concurrent deliveries of the same event can't both proceed.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.begin_webhook_event(_id TEXT, _type TEXT)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  _status TEXT;
BEGIN
  -- Try to claim: insert as 'processing'. If it already exists, lock and read.
  INSERT INTO public.webhook_events (id, event_type, status)
  VALUES (_id, _type, 'processing')
  ON CONFLICT (id) DO NOTHING;

  IF FOUND THEN
    RETURN 'new';
  END IF;

  SELECT status INTO _status FROM public.webhook_events WHERE id = _id FOR UPDATE;

  IF _status = 'processed' THEN
    RETURN 'processed';   -- genuinely done — skip
  END IF;

  -- 'processing' (a crashed/concurrent prior attempt) or 'failed': allow retry.
  UPDATE public.webhook_events SET status = 'processing' WHERE id = _id;
  RETURN 'new';
END;
$$;

-- ---------------------------------------------------------------------------
-- complete_webhook_event() — mark an event fully processed. Called ONLY after
-- the subscription grant has succeeded.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.complete_webhook_event(_id TEXT)
RETURNS VOID
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  UPDATE public.webhook_events
  SET status = 'processed', processed_at = now()
  WHERE id = _id;
$$;

-- ---------------------------------------------------------------------------
-- fail_webhook_event() — mark an attempt failed so it stays retryable and is
-- visible for debugging. Razorpay will redeliver; begin_webhook_event() will
-- return 'new' for a 'failed' row.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.fail_webhook_event(_id TEXT)
RETURNS VOID
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  UPDATE public.webhook_events SET status = 'failed' WHERE id = _id;
$$;

-- Server-only: revoke from anon + authenticated, grant service_role only.
-- Enforced by tests/unit/db-permissions.test.ts.
REVOKE ALL ON FUNCTION public.begin_webhook_event(TEXT, TEXT) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.complete_webhook_event(TEXT) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.fail_webhook_event(TEXT) FROM PUBLIC, anon, authenticated;

GRANT EXECUTE ON FUNCTION public.begin_webhook_event(TEXT, TEXT) TO service_role;
GRANT EXECUTE ON FUNCTION public.complete_webhook_event(TEXT) TO service_role;
GRANT EXECUTE ON FUNCTION public.fail_webhook_event(TEXT) TO service_role;
