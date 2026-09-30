-- =========================================================
-- AI USAGE METERING — per-user, tier-based, billing-cycle reset
-- =========================================================
-- Real AI-gateway calls cost money. Cap them per user per billing cycle so a
-- runaway (or abusive) user can't rack up unbounded cost, and show the usage
-- transparently.
--
-- Allowances (monthly-equivalent, per cycle): free 0, pro 20, elite 200.
-- admin + lifetime = unlimited. One gateway call = one action. When the limit
-- is reached the call is refused with a clear message. The window is the user's
-- billing cycle: when their subscriptions.current_period_end advances, the
-- counter resets to zero and adopts the new period end.

CREATE TABLE public.ai_usage (
  user_id     UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  -- The subscription period this count belongs to. When the sub rolls to a new
  -- period_end, count resets. NULL means "no dated period yet" (first use).
  period_end  TIMESTAMPTZ,
  action_count INTEGER NOT NULL DEFAULT 0 CHECK (action_count >= 0),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.ai_usage ENABLE ROW LEVEL SECURITY;
GRANT ALL ON public.ai_usage TO service_role;
-- Server-only: written exclusively through the SECURITY DEFINER RPCs below.

-- Per-tier monthly-equivalent allowance. Central so it's one edit to tune.
CREATE OR REPLACE FUNCTION public.ai_allowance(_tier public.access_tier)
RETURNS INTEGER
LANGUAGE sql
IMMUTABLE
SET search_path = public
AS $$
  SELECT CASE _tier
    WHEN 'pro' THEN 20
    WHEN 'elite' THEN 200
    ELSE 0            -- free
  END;
$$;

-- ---------------------------------------------------------------------------
-- ai_meter() — atomically check-and-consume one AI action for the caller.
-- Returns (ok, used, allowance, remaining, unlimited). ok=false means the call
-- must be refused. Row-locked so concurrent calls can't overspend the cap.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.ai_meter()
RETURNS TABLE (ok BOOLEAN, used INTEGER, allowance INTEGER, remaining INTEGER, unlimited BOOLEAN)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  _uid   UUID := auth.uid();
  _ent   RECORD;
  _allow INTEGER;
  _pend  TIMESTAMPTZ;
  _row   public.ai_usage%ROWTYPE;
BEGIN
  IF _uid IS NULL THEN
    RETURN QUERY SELECT false, 0, 0, 0, false;
    RETURN;
  END IF;

  SELECT * INTO _ent FROM public.my_entitlement();

  -- admin + lifetime: unlimited. Don't touch the counter.
  IF _ent.is_admin OR _ent.source = 'lifetime' THEN
    RETURN QUERY SELECT true, 0, 0, 0, true;
    RETURN;
  END IF;

  _allow := public.ai_allowance(_ent.tier);
  IF _allow <= 0 THEN
    -- free / no AI allowance at all.
    RETURN QUERY SELECT false, 0, 0, 0, false;
    RETURN;
  END IF;

  _pend := _ent.expires_at;  -- current billing-period end

  -- Claim/lock the row.
  SELECT * INTO _row FROM public.ai_usage WHERE user_id = _uid FOR UPDATE;

  IF NOT FOUND THEN
    INSERT INTO public.ai_usage (user_id, period_end, action_count)
    VALUES (_uid, _pend, 0)
    RETURNING * INTO _row;
  ELSIF _row.period_end IS DISTINCT FROM _pend
        AND (_pend IS NULL OR _row.period_end IS NULL OR _pend > _row.period_end) THEN
    -- Billing period advanced → reset the counter to this new period.
    UPDATE public.ai_usage
      SET period_end = _pend, action_count = 0, updated_at = now()
      WHERE user_id = _uid
      RETURNING * INTO _row;
  END IF;

  IF _row.action_count >= _allow THEN
    RETURN QUERY SELECT false, _row.action_count, _allow, 0, false;
    RETURN;
  END IF;

  UPDATE public.ai_usage
    SET action_count = action_count + 1, updated_at = now()
    WHERE user_id = _uid
    RETURNING * INTO _row;

  RETURN QUERY SELECT true, _row.action_count, _allow, (_allow - _row.action_count), false;
END;
$$;

-- ---------------------------------------------------------------------------
-- ai_usage_status() — read-only view of the caller's current usage, for the UI.
-- Does not consume. Mirrors ai_meter's period-reset logic in a read so a stale
-- count from a past period reads as 0 used.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.ai_usage_status()
RETURNS TABLE (used INTEGER, allowance INTEGER, remaining INTEGER, unlimited BOOLEAN)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  _uid  UUID := auth.uid();
  _ent  RECORD;
  _allow INTEGER;
  _row  public.ai_usage%ROWTYPE;
  _used INTEGER;
BEGIN
  IF _uid IS NULL THEN
    RETURN QUERY SELECT 0, 0, 0, false;
    RETURN;
  END IF;

  SELECT * INTO _ent FROM public.my_entitlement();
  IF _ent.is_admin OR _ent.source = 'lifetime' THEN
    RETURN QUERY SELECT 0, 0, 0, true;
    RETURN;
  END IF;

  _allow := public.ai_allowance(_ent.tier);
  SELECT * INTO _row FROM public.ai_usage WHERE user_id = _uid;

  -- If the stored count belongs to a past period, it reads as 0 used.
  _used := 0;
  IF FOUND AND (_row.period_end IS NOT DISTINCT FROM _ent.expires_at) THEN
    _used := _row.action_count;
  END IF;

  RETURN QUERY SELECT _used, _allow, GREATEST(_allow - _used, 0), false;
END;
$$;

-- Lock down. All three are SECURITY DEFINER and identify the user via
-- auth.uid(), so they must run as `authenticated` (not service_role, where
-- auth.uid() is null) — and being definer-scoped to auth.uid(), a user can only
-- meter/read THEIR OWN usage, never anyone else's. anon is always revoked.
-- Enforced by tests/unit/db-permissions.test.ts.
REVOKE ALL ON FUNCTION public.ai_meter() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.ai_usage_status() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.ai_allowance(public.access_tier) FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION public.ai_meter() TO authenticated;
GRANT EXECUTE ON FUNCTION public.ai_usage_status() TO authenticated;
GRANT EXECUTE ON FUNCTION public.ai_allowance(public.access_tier) TO authenticated;
