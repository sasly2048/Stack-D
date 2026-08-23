-- =========================================================
-- Prevent two subscriptions from sharing a Razorpay subscription id
-- =========================================================
-- provider_ref holds the Razorpay subscription id for a paid user. Without a
-- uniqueness guarantee, a webhook bug or replay could grant the same Razorpay
-- subscription to two different users. A partial unique index enforces one
-- provider_ref per row while still allowing many NULLs (free users).

CREATE UNIQUE INDEX IF NOT EXISTS subscriptions_provider_ref_key
  ON public.subscriptions (provider_ref)
  WHERE provider_ref IS NOT NULL;
