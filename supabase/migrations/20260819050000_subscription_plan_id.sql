-- =========================================================
-- Record which local plan a subscription is on
-- =========================================================
-- The Manage Subscription panel needs the exact plan (interval + price), but
-- subscriptions.provider_ref stores the Razorpay SUBSCRIPTION id, not the plan.
-- Filtering plans by tier alone returns two rows (monthly + annual), so the
-- panel could show the wrong interval/price. Store the local plan id at grant
-- time and read it back exactly.

ALTER TABLE public.subscriptions
  ADD COLUMN IF NOT EXISTS plan_id TEXT REFERENCES public.plans(id);

-- Extend grant_subscription with the local plan id. Adding an argument makes a
-- new signature (Postgres overloads by arg list), so drop the old 4-arg version
-- first to avoid two coexisting functions — the webhook is the only caller and
-- it moves to the 5-arg form below.
DROP FUNCTION IF EXISTS public.grant_subscription(UUID, public.access_tier, TEXT, TIMESTAMPTZ);

CREATE OR REPLACE FUNCTION public.grant_subscription(
  _user_id      UUID,
  _tier         public.access_tier,
  _provider_ref TEXT,
  _period_end   TIMESTAMPTZ,
  _plan_id      TEXT DEFAULT NULL
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  INSERT INTO public.subscriptions (user_id, tier, source, provider_ref, current_period_end, plan_id)
  VALUES (_user_id, _tier, 'razorpay', _provider_ref, _period_end, _plan_id)
  ON CONFLICT (user_id) DO UPDATE
    SET tier = EXCLUDED.tier,
        source = 'razorpay',
        provider_ref = EXCLUDED.provider_ref,
        current_period_end = EXCLUDED.current_period_end,
        plan_id = EXCLUDED.plan_id;
END;
$$;

-- Re-assert the lockdown for the new 5-arg signature (the 4-arg REVOKE/GRANT
-- from the earlier migration doesn't cover this overload).
REVOKE ALL ON FUNCTION public.grant_subscription(UUID, public.access_tier, TEXT, TIMESTAMPTZ, TEXT)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.grant_subscription(UUID, public.access_tier, TEXT, TIMESTAMPTZ, TEXT)
  TO service_role;
