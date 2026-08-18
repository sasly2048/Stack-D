import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";

import type { AccessTier } from "./subscription.functions";

export interface SubscriptionDetail {
  tier: AccessTier;
  /** 'none' | 'razorpay' | 'lifetime' | 'manual' | 'admin'. */
  source: string;
  /** null = non-expiring (lifetime/admin) or no paid sub. */
  currentPeriodEnd: string | null;
  interval: "monthly" | "annual" | null;
  priceInr: number | null;
  displayName: string | null;
  /** True only for a live Razorpay subscription the user can cancel. */
  cancellable: boolean;
}

/**
 * The signed-in user's subscription, joined to their plan for interval/amount.
 * Reads the caller's own row (RLS: read-own) plus the public plans catalog.
 * provider_ref (the Razorpay id) is intentionally NOT returned to the client —
 * cancellation resolves it server-side.
 */
export const getSubscriptionDetail = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<SubscriptionDetail> => {
    const { data: sub } = await context.supabase
      .from("subscriptions" as never)
      .select("tier, source, current_period_end, provider_ref, plan_id")
      .eq("user_id" as never, context.userId as never)
      .maybeSingle();

    const s = sub as {
      tier?: AccessTier;
      source?: string;
      current_period_end?: string | null;
      provider_ref?: string | null;
      plan_id?: string | null;
    } | null;

    if (!s || !s.tier || s.tier === "free") {
      return {
        tier: "free",
        source: "none",
        currentPeriodEnd: null,
        interval: null,
        priceInr: null,
        displayName: null,
        cancellable: false,
      };
    }

    // Look up the EXACT plan the subscription is on (stored at grant time), so
    // the interval/price/label are correct — not an arbitrary row of the tier.
    let plan: PlanRow | null = null;
    if (s.plan_id) {
      const { data: p } = await context.supabase
        .from("plans" as never)
        .select("interval, price_inr, display_name")
        .eq("id" as never, s.plan_id as never)
        .maybeSingle();
      plan = (p as unknown as PlanRow | null) ?? null;
    }

    const isRazorpay = s.source === "razorpay" && Boolean(s.provider_ref);

    return {
      tier: s.tier,
      source: s.source ?? "none",
      currentPeriodEnd: s.current_period_end ?? null,
      interval: plan?.interval ?? null,
      priceInr: plan?.price_inr ?? null,
      displayName: plan?.display_name ?? null,
      cancellable: isRazorpay,
    };
  });

interface PlanRow {
  interval: "monthly" | "annual";
  price_inr: number;
  display_name: string;
}

export type CancelResult = "cancelled" | "not_cancellable" | "error";

/**
 * Cancel the caller's Razorpay subscription at cycle end — access continues
 * until current_period_end, matching the "cancel anytime, keep what you paid
 * for" promise. Reads the provider_ref via the admin client (never trusts a
 * client-supplied id) and calls Razorpay's cancel API. The eventual lapse to
 * free is driven by the subscription.cancelled/completed webhook + expiry, so
 * this doesn't downgrade the row itself.
 */
export const cancelSubscription = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<{ result: CancelResult; message: string }> => {
    const keyId = process.env.RAZORPAY_KEY_ID;
    const keySecret = process.env.RAZORPAY_KEY_SECRET;
    if (!keyId || !keySecret) {
      return { result: "error", message: "Billing isn't configured." };
    }

    const { supabaseAdmin } = await import("@/integrations/supabase/client.server");
    const { data: sub } = await supabaseAdmin
      .from("subscriptions")
      .select("source, provider_ref")
      .eq("user_id", context.userId)
      .maybeSingle();

    const s = sub as { source?: string; provider_ref?: string | null } | null;
    if (!s || s.source !== "razorpay" || !s.provider_ref) {
      return { result: "not_cancellable", message: "No active subscription to cancel." };
    }

    const auth = Buffer.from(`${keyId}:${keySecret}`).toString("base64");
    const res = await fetch(`https://api.razorpay.com/v1/subscriptions/${s.provider_ref}/cancel`, {
      method: "POST",
      headers: { Authorization: `Basic ${auth}`, "Content-Type": "application/json" },
      // cancel_at_cycle_end: 1 -> keep access until the paid period ends.
      body: JSON.stringify({ cancel_at_cycle_end: 1 }),
    });

    if (!res.ok) {
      const body = await res.text();
      console.error("razorpay_cancel_failed", res.status, body.slice(0, 300));
      return { result: "error", message: "Couldn't cancel right now. Please try again." };
    }

    return {
      result: "cancelled",
      message: "Cancelled. You keep full access until your current period ends.",
    };
  });
