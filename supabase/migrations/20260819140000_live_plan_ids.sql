-- =========================================================
-- Switch plan provider_refs to LIVE Razorpay plan ids
-- =========================================================
-- Razorpay test and live plans are separate objects with different ids. Going
-- live means the seeded test plan ids (plan_TR13...) no longer resolve, so
-- checkout would fail. Point each local plan at its LIVE Razorpay plan id.
--
-- Safe to re-run (plain UPDATEs). If you ever revert to test mode, re-apply the
-- test ids the same way.

UPDATE public.plans SET provider_ref = 'plan_TR9abCG3URLzO0' WHERE id = 'pro_monthly';
UPDATE public.plans SET provider_ref = 'plan_TR9cI3LPxdTbkN' WHERE id = 'pro_annual';
UPDATE public.plans SET provider_ref = 'plan_TR9uw5jiMyCoFH' WHERE id = 'elite_monthly';
UPDATE public.plans SET provider_ref = 'plan_TR9ccmwtimXi7L' WHERE id = 'elite_annual';
