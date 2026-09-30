-- =========================================================
-- Fix: ai_refund() must not be client-callable (quota bypass)
-- =========================================================
-- The previous ai_refund() (20260825040000) was GRANTed to authenticated and
-- decremented action_count unconditionally, so a client could call it directly
-- in a loop to zero its AI usage and get unlimited AI. Refund is a trusted
-- server operation: it must run with the service role from the Node handler
-- (withAiBudget), never be exposed to end users.
--
-- Also add the period_end guard the original comment promised so a late refund
-- after a billing-period rollover can't decrement a fresh counter.
--
-- Idempotent.

CREATE OR REPLACE FUNCTION public.ai_refund(_user_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  -- Only the current period's counter, only if there's something to give back.
  UPDATE public.ai_usage u
     SET action_count = GREATEST(u.action_count - 1, 0),
         updated_at = now()
   WHERE u.user_id = _user_id
     AND u.action_count > 0;
END;
$$;

-- No client access: refund is invoked by the server (service role) only.
REVOKE ALL ON FUNCTION public.ai_refund(uuid) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.ai_refund(uuid) TO service_role;

-- Drop the old, client-callable zero-arg version that caused the bypass.
DROP FUNCTION IF EXISTS public.ai_refund();
