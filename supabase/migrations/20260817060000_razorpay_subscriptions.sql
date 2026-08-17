-- =========================================================
-- RAZORPAY SUBSCRIPTIONS — payment wiring
-- =========================================================
-- Adds the pieces the Razorpay integration needs on top of the entitlement
-- backend (20260817050000):
--   1. plans.provider_ref     — the Razorpay plan_id each local plan maps to.
--   2. webhook_events          — idempotency ledger so a replayed/duplicated
--                                Razorpay webhook is applied at most once.
--   3. grant_subscription()    — the single writer the webhook calls to move a
--                                user onto/along a paid tier. SECURITY DEFINER,
--                                server-only (revoked from anon + authenticated;
--                                only service_role / the webhook invokes it).
--
-- No client can grant itself a subscription: this RPC is not callable by
-- authenticated users, and the webhook route authenticates the payload via
-- Razorpay's HMAC signature before calling it.

ALTER TABLE public.plans ADD COLUMN IF NOT EXISTS provider_ref TEXT;

-- ---------------------------------------------------------------------------
-- webhook_events — idempotency. Razorpay may deliver an event more than once;
-- we insert the event id here first and skip if it already exists.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.webhook_events (
  id           TEXT PRIMARY KEY,            -- razorpay event id (x-razorpay-event-id)
  event_type   TEXT NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.webhook_events ENABLE ROW LEVEL SECURITY;
GRANT ALL ON public.webhook_events TO service_role;
-- Server-only: no anon/authenticated grants, no policies.

-- ---------------------------------------------------------------------------
-- grant_subscription() — upsert a user's paid state. Called by the webhook
-- after signature verification. `_period_end` NULL only for non-expiring
-- grants (not used by Razorpay, which always sends a period end).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.grant_subscription(
  _user_id      UUID,
  _tier         public.access_tier,
  _provider_ref TEXT,
  _period_end   TIMESTAMPTZ
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  INSERT INTO public.subscriptions (user_id, tier, source, provider_ref, current_period_end)
  VALUES (_user_id, _tier, 'razorpay', _provider_ref, _period_end)
  ON CONFLICT (user_id) DO UPDATE
    SET tier = EXCLUDED.tier,
        source = 'razorpay',
        provider_ref = EXCLUDED.provider_ref,
        current_period_end = EXCLUDED.current_period_end;
END;
$$;

-- ---------------------------------------------------------------------------
-- record_webhook_event() — insert-if-absent, returns true when this is the
-- first time we've seen the event (i.e. the caller should process it).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.record_webhook_event(_id TEXT, _type TEXT)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  INSERT INTO public.webhook_events (id, event_type) VALUES (_id, _type);
  RETURN true;
EXCEPTION WHEN unique_violation THEN
  RETURN false;   -- already processed
END;
$$;

-- ---------------------------------------------------------------------------
-- Lock down EXECUTE. Both are SECURITY DEFINER and server-only: revoke from
-- anon AND authenticated (a signed-in user must not grant itself a sub), leave
-- only service_role. Enforced by tests/unit/db-permissions.test.ts.
-- ---------------------------------------------------------------------------
REVOKE ALL ON FUNCTION public.grant_subscription(UUID, public.access_tier, TEXT, TIMESTAMPTZ)
  FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.record_webhook_event(TEXT, TEXT)
  FROM PUBLIC, anon, authenticated;

GRANT EXECUTE ON FUNCTION public.grant_subscription(UUID, public.access_tier, TEXT, TIMESTAMPTZ)
  TO service_role;
GRANT EXECUTE ON FUNCTION public.record_webhook_event(TEXT, TEXT)
  TO service_role;

-- ---------------------------------------------------------------------------
-- Map each local plan to its Razorpay plan_id. These come from the Razorpay
-- dashboard (Subscriptions > Plans); test-mode IDs shown here. When switching
-- to live mode, re-create the plans on the live account and update these to the
-- rzp_live plan_ids (or run scripts/razorpay-setup-plans.mjs against live).
-- ---------------------------------------------------------------------------
UPDATE public.plans SET provider_ref = 'plan_TR133rXgAe3tPs' WHERE id = 'pro_monthly';
UPDATE public.plans SET provider_ref = 'plan_TR13jDreVvufax' WHERE id = 'pro_annual';
UPDATE public.plans SET provider_ref = 'plan_TR141R9aEkP0dl' WHERE id = 'elite_monthly';
UPDATE public.plans SET provider_ref = 'plan_TR14KzfILy9Ccx' WHERE id = 'elite_annual';
