-- =========================================================
-- P0: protect lifetime entitlement + close the finalize race
-- =========================================================

-- ---------------------------------------------------------------------------
-- #10 — A Razorpay webhook must never downgrade a lifetime user.
-- grant_subscription() upserted with ON CONFLICT DO UPDATE unconditionally, so
-- a stale/late/out-of-order subscription.charged event for a user who has since
-- redeemed lifetime would overwrite source='lifetime' back to 'razorpay' with an
-- expiring period. Guard the UPDATE: never touch a row that is already lifetime.
-- ---------------------------------------------------------------------------
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
        plan_id = EXCLUDED.plan_id
    -- Lifetime is permanent: a payment event can't demote it.
    WHERE public.subscriptions.source <> 'lifetime';
END;
$$;

REVOKE ALL ON FUNCTION public.grant_subscription(UUID, public.access_tier, TEXT, TIMESTAMPTZ, TEXT)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.grant_subscription(UUID, public.access_tier, TEXT, TIMESTAMPTZ, TEXT)
  TO service_role;

-- ---------------------------------------------------------------------------
-- #12 — focus_history had no uniqueness on (profile_id, room_id), so
-- finalize_focus_session()'s "SELECT existing then INSERT/UPDATE" has a TOCTOU
-- window: two concurrent finalizations for the same room could both miss the
-- existing row and INSERT twice → duplicate history + double XP. Add the unique
-- constraint so the second insert can't land. (The function already takes the
-- lower of client/server XP and recomputes breaches; this closes the race.)
--
-- Deduplicate any pre-existing doubles first so the constraint can be created:
-- keep the earliest row per (profile_id, room_id).
DELETE FROM public.focus_history a
USING public.focus_history b
WHERE a.profile_id = b.profile_id
  AND a.room_id = b.room_id
  AND a.room_id IS NOT NULL
  AND a.ctid > b.ctid;

CREATE UNIQUE INDEX IF NOT EXISTS focus_history_profile_room_key
  ON public.focus_history (profile_id, room_id)
  WHERE room_id IS NOT NULL;
