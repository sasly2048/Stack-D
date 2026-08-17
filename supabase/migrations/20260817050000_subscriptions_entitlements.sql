-- =========================================================
-- SUBSCRIPTIONS / ENTITLEMENTS / LIFETIME PROMO
-- =========================================================
-- Server-authoritative access control for the premium paywall.
--
-- Access tiers, ordered: free < pro < elite. `admin` and `lifetime` both
-- resolve to elite-level access (the top of what any feature can require).
-- Admin is an email allowlist; lifetime is coupon-granted. Everything a
-- feature ever checks flows through public.my_entitlement(), so gating logic
-- never re-derives tier rules and never trusts the client.
--
-- Design goal (per product ask): plans, prices, the coupon code, the
-- redemption cap, and the promo window are all DATA, not code. Tuning any of
-- them is an UPDATE, not a deploy.

-- ---------------------------------------------------------------------------
-- Tier enum (ordered by declaration; we compare via tier_rank() below since
-- enum ordinal comparison is brittle across future ALTER TYPE ... ADD VALUE)
-- ---------------------------------------------------------------------------
CREATE TYPE public.access_tier AS ENUM ('free', 'pro', 'elite');

CREATE OR REPLACE FUNCTION public.tier_rank(_t public.access_tier)
RETURNS INTEGER
LANGUAGE sql
IMMUTABLE
SET search_path = public
AS $$
  SELECT CASE _t
    WHEN 'free' THEN 0
    WHEN 'pro' THEN 1
    WHEN 'elite' THEN 2
  END;
$$;

-- ---------------------------------------------------------------------------
-- plans — the public pricing catalog. UI reads this; nothing is hardcoded.
-- ---------------------------------------------------------------------------
CREATE TABLE public.plans (
  id           TEXT PRIMARY KEY,              -- 'pro_monthly', 'elite_annual', ...
  tier         public.access_tier NOT NULL,
  interval     TEXT NOT NULL CHECK (interval IN ('monthly', 'annual')),
  price_inr    INTEGER NOT NULL CHECK (price_inr >= 0),   -- whole rupees
  display_name TEXT NOT NULL,
  is_active    BOOLEAN NOT NULL DEFAULT true,
  sort_order   INTEGER NOT NULL DEFAULT 0,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.plans ENABLE ROW LEVEL SECURITY;
GRANT SELECT ON public.plans TO anon, authenticated;
GRANT ALL ON public.plans TO service_role;

-- Pricing is public marketing data — anyone may read the active catalog.
CREATE POLICY "Anyone can read active plans"
  ON public.plans FOR SELECT
  USING (is_active);

INSERT INTO public.plans (id, tier, interval, price_inr, display_name, sort_order) VALUES
  ('pro_monthly',   'pro',   'monthly', 129,  'Pro Monthly',   1),
  ('pro_annual',    'pro',   'annual',  899,  'Pro Annual',    2),
  ('elite_monthly', 'elite', 'monthly', 249,  'Elite Monthly', 3),
  ('elite_annual',  'elite', 'annual',  1799, 'Elite Annual',  4);

-- ---------------------------------------------------------------------------
-- admin_emails — server-side admin allowlist. Keyed on auth.users.email, which
-- profiles does not carry, so admin status is only resolvable inside a
-- SECURITY DEFINER function that can read auth.users. This is the "admin must
-- be server-side, not a frontend check" requirement.
-- ---------------------------------------------------------------------------
CREATE TABLE public.admin_emails (
  email      TEXT PRIMARY KEY,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.admin_emails ENABLE ROW LEVEL SECURITY;
-- No policies, no grants to anon/authenticated: this table is server-only.
-- It is read exclusively via SECURITY DEFINER functions and the service role.
GRANT ALL ON public.admin_emails TO service_role;

INSERT INTO public.admin_emails (email) VALUES
  ('raghavendrasujith204800@gmail.com'),
  ('msv817400@gmail.com');

-- ---------------------------------------------------------------------------
-- subscriptions — one row per user, their current paid state. Absent row =
-- free. Written by the (future) Razorpay webhook via service role and by
-- redeem_lifetime(); never client-writable.
-- ---------------------------------------------------------------------------
CREATE TABLE public.subscriptions (
  user_id            UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  tier               public.access_tier NOT NULL DEFAULT 'free',
  source             TEXT NOT NULL DEFAULT 'none'
                       CHECK (source IN ('none', 'razorpay', 'lifetime', 'manual')),
  -- NULL current_period_end = non-expiring (lifetime / manual grant).
  current_period_end TIMESTAMPTZ,
  provider_ref       TEXT,                    -- razorpay subscription id, etc.
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_subscriptions_updated_at
  BEFORE UPDATE ON public.subscriptions
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

ALTER TABLE public.subscriptions ENABLE ROW LEVEL SECURITY;
GRANT SELECT ON public.subscriptions TO authenticated;   -- own row only, via policy
GRANT ALL ON public.subscriptions TO service_role;

-- A user may read their own subscription row (for display), but never write it
-- — all writes go through service role / SECURITY DEFINER, so scoring and
-- entitlement can never be client-forged.
CREATE POLICY "Users read own subscription"
  ON public.subscriptions FOR SELECT
  TO authenticated
  USING (auth.uid() = user_id);

-- ---------------------------------------------------------------------------
-- lifetime_promo — single-row config for the promotional lifetime offer.
-- coupon_code is NULL until the operator sets it (product ask: keep it
-- configurable, decide the code later). max_redemptions caps total redemptions
-- at 500 — once redeemed_count reaches it, redeem_lifetime() returns 'sold_out'
-- for everyone, valid code or not. redeemed_count is the server-side counter.
-- ---------------------------------------------------------------------------
CREATE TABLE public.lifetime_promo (
  id              INTEGER PRIMARY KEY DEFAULT 1 CHECK (id = 1),  -- singleton
  coupon_code     TEXT,                          -- NULL = not yet set
  max_redemptions INTEGER NOT NULL DEFAULT 500 CHECK (max_redemptions > 0),
  redeemed_count  INTEGER NOT NULL DEFAULT 0 CHECK (redeemed_count >= 0),
  is_active       BOOLEAN NOT NULL DEFAULT false,
  starts_at       TIMESTAMPTZ,
  ends_at         TIMESTAMPTZ,
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_lifetime_promo_updated_at
  BEFORE UPDATE ON public.lifetime_promo
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

ALTER TABLE public.lifetime_promo ENABLE ROW LEVEL SECURITY;
GRANT ALL ON public.lifetime_promo TO service_role;
-- Server-only: the coupon code must never reach the client. Redemption count
-- and "is the promo live / seats left" are exposed through lifetime_promo_status().

INSERT INTO public.lifetime_promo (id, coupon_code, max_redemptions, is_active)
  VALUES (1, NULL, 500, false);

-- ---------------------------------------------------------------------------
-- lifetime_redemptions — audit + duplicate guard. One row per user, ever.
-- ---------------------------------------------------------------------------
CREATE TABLE public.lifetime_redemptions (
  user_id     UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  redeemed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.lifetime_redemptions ENABLE ROW LEVEL SECURITY;
GRANT ALL ON public.lifetime_redemptions TO service_role;
-- Server-only.

-- ---------------------------------------------------------------------------
-- my_entitlement() — THE single source of truth. Every gate calls this.
-- Resolves admin (email allowlist) > lifetime/active-sub > free, and reports
-- the effective tier plus admin/premium booleans. SECURITY DEFINER so it can
-- read admin_emails + auth.users, which are not directly readable by clients.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.my_entitlement()
RETURNS TABLE (
  tier        public.access_tier,
  is_admin    BOOLEAN,
  is_premium  BOOLEAN,
  source      TEXT,
  expires_at  TIMESTAMPTZ
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  _uid   UUID := auth.uid();
  _email TEXT;
  _admin BOOLEAN := false;
  _sub   public.subscriptions%ROWTYPE;
BEGIN
  IF _uid IS NULL THEN
    -- Unauthenticated: free, nothing.
    RETURN QUERY SELECT 'free'::public.access_tier, false, false, 'none'::TEXT, NULL::TIMESTAMPTZ;
    RETURN;
  END IF;

  SELECT u.email INTO _email FROM auth.users u WHERE u.id = _uid;

  IF _email IS NOT NULL AND EXISTS (
    SELECT 1 FROM public.admin_emails a WHERE lower(a.email) = lower(_email)
  ) THEN
    _admin := true;
  END IF;

  IF _admin THEN
    RETURN QUERY SELECT 'elite'::public.access_tier, true, true, 'admin'::TEXT, NULL::TIMESTAMPTZ;
    RETURN;
  END IF;

  SELECT * INTO _sub FROM public.subscriptions s WHERE s.user_id = _uid;

  IF _sub.user_id IS NULL THEN
    RETURN QUERY SELECT 'free'::public.access_tier, false, false, 'none'::TEXT, NULL::TIMESTAMPTZ;
    RETURN;
  END IF;

  -- Expired paid subscription collapses to free. NULL period_end = never expires.
  IF _sub.tier <> 'free'
     AND _sub.current_period_end IS NOT NULL
     AND _sub.current_period_end < now() THEN
    RETURN QUERY SELECT 'free'::public.access_tier, false, false, 'none'::TEXT, NULL::TIMESTAMPTZ;
    RETURN;
  END IF;

  RETURN QUERY SELECT
    _sub.tier,
    false,
    (public.tier_rank(_sub.tier) >= public.tier_rank('pro')),
    _sub.source,
    _sub.current_period_end;
END;
$$;

-- ---------------------------------------------------------------------------
-- has_tier(required) — convenience boolean for server gates: does the caller
-- meet at least `required`? admin/lifetime satisfy 'elite'.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.has_tier(_required public.access_tier)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT public.tier_rank(e.tier) >= public.tier_rank(_required)
  FROM public.my_entitlement() e;
$$;

-- ---------------------------------------------------------------------------
-- lifetime_promo_status() — what the client is allowed to know about the
-- promo: is it live, how many seats remain. NEVER the coupon code.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.lifetime_promo_status()
RETURNS TABLE (
  active         BOOLEAN,
  seats_total    INTEGER,
  seats_remaining INTEGER,
  ends_at        TIMESTAMPTZ,
  already_redeemed BOOLEAN
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  _p public.lifetime_promo%ROWTYPE;
  _uid UUID := auth.uid();
BEGIN
  SELECT * INTO _p FROM public.lifetime_promo WHERE id = 1;
  RETURN QUERY SELECT
    (_p.is_active
       AND _p.coupon_code IS NOT NULL
       AND (_p.starts_at IS NULL OR _p.starts_at <= now())
       AND (_p.ends_at IS NULL OR _p.ends_at >= now())
       AND _p.redeemed_count < _p.max_redemptions),
    _p.max_redemptions,
    GREATEST(_p.max_redemptions - _p.redeemed_count, 0),
    _p.ends_at,
    (_uid IS NOT NULL AND EXISTS (
      SELECT 1 FROM public.lifetime_redemptions r WHERE r.user_id = _uid
    ));
END;
$$;

-- ---------------------------------------------------------------------------
-- redeem_lifetime(code) — atomic coupon redemption. Locks the promo row so
-- concurrent redeems cannot oversell the cap. Enforces: promo active, in
-- window, code matches, seats remain, caller has not already redeemed.
-- Returns a machine-readable status the UI maps to a message.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.redeem_lifetime(_code TEXT)
RETURNS TEXT   -- 'ok' | 'inactive' | 'bad_code' | 'sold_out' | 'already' | 'unauth'
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  _uid UUID := auth.uid();
  _p   public.lifetime_promo%ROWTYPE;
BEGIN
  IF _uid IS NULL THEN
    RETURN 'unauth';
  END IF;

  -- Row lock: serialize redemptions so the cap is exact under concurrency.
  SELECT * INTO _p FROM public.lifetime_promo WHERE id = 1 FOR UPDATE;

  IF NOT _p.is_active
     OR _p.coupon_code IS NULL
     OR (_p.starts_at IS NOT NULL AND _p.starts_at > now())
     OR (_p.ends_at IS NOT NULL AND _p.ends_at < now()) THEN
    RETURN 'inactive';
  END IF;

  -- Constant-work compare is unnecessary here (single low-entropy operator
  -- coupon, already rate-limited at the app layer); a plain match is fine.
  IF _code IS NULL OR _code <> _p.coupon_code THEN
    RETURN 'bad_code';
  END IF;

  IF EXISTS (SELECT 1 FROM public.lifetime_redemptions r WHERE r.user_id = _uid) THEN
    RETURN 'already';
  END IF;

  IF _p.redeemed_count >= _p.max_redemptions THEN
    RETURN 'sold_out';
  END IF;

  INSERT INTO public.lifetime_redemptions (user_id) VALUES (_uid);
  UPDATE public.lifetime_promo SET redeemed_count = redeemed_count + 1 WHERE id = 1;

  -- Grant elite-level, non-expiring access.
  INSERT INTO public.subscriptions (user_id, tier, source, current_period_end)
    VALUES (_uid, 'elite', 'lifetime', NULL)
    ON CONFLICT (user_id) DO UPDATE
      SET tier = 'elite', source = 'lifetime', current_period_end = NULL;

  RETURN 'ok';
END;
$$;

-- ---------------------------------------------------------------------------
-- Lock down EXECUTE. These are SECURITY DEFINER: revoke from anon (and PUBLIC),
-- grant only to authenticated. Enforced by tests/unit/db-permissions.test.ts.
-- ---------------------------------------------------------------------------
REVOKE ALL ON FUNCTION public.my_entitlement() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.has_tier(public.access_tier) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.lifetime_promo_status() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.redeem_lifetime(TEXT) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.tier_rank(public.access_tier) FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION public.my_entitlement() TO authenticated;
GRANT EXECUTE ON FUNCTION public.has_tier(public.access_tier) TO authenticated;
GRANT EXECUTE ON FUNCTION public.lifetime_promo_status() TO authenticated;
GRANT EXECUTE ON FUNCTION public.redeem_lifetime(TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.tier_rank(public.access_tier) TO authenticated;
