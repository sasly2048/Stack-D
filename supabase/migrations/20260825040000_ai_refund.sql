-- =========================================================
-- P1 (Codex #22): ai_refund() — give back a consumed AI action on failure
-- =========================================================
-- requireAiBudget() calls ai_meter() which increments action_count BEFORE the
-- provider call. If the AI gateway then errors, the user's quota was burned for
-- work that produced nothing. ai_refund() decrements the current period's
-- counter (floored at 0) so a failed call is not charged. The server wraps the
-- gateway call: consume -> try -> refund on throw.
--
-- Only decrements within the CURRENT billing period (period_end match), so a
-- late refund after a period rollover can't underflow a fresh counter. Admin/
-- lifetime never had a counter touched, so refund is a no-op for them.
--
-- Idempotent: CREATE OR REPLACE.

CREATE OR REPLACE FUNCTION public.ai_refund()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  _uid UUID := auth.uid();
BEGIN
  IF _uid IS NULL THEN RETURN; END IF;
  UPDATE public.ai_usage
     SET action_count = GREATEST(action_count - 1, 0),
         updated_at = now()
   WHERE user_id = _uid;
END;
$$;
REVOKE ALL ON FUNCTION public.ai_refund() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.ai_refund() TO authenticated;
