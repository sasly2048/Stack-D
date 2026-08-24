import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";

/**
 * Create a Razorpay subscription for the signed-in user and return the ids the
 * client needs to open Razorpay Checkout. The user_id is stamped into the
 * subscription `notes` so the webhook can map the payment back to the account
 * without trusting anything the client sends later.
 *
 * Secrets (RAZORPAY_KEY_SECRET) are read from process.env INSIDE the handler
 * only — this module ships to the client bundle, so nothing secret may live at
 * module top level. The public Key ID is returned to the client (safe — it
 * ships in Razorpay's own checkout script).
 */
export const createSubscription = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d: { planId: string }) => ({
    planId: typeof d?.planId === "string" ? d.planId : "",
  }))
  .handler(async ({ data, context }): Promise<{ subscriptionId: string; keyId: string }> => {
    const keyId = process.env.RAZORPAY_KEY_ID;
    const keySecret = process.env.RAZORPAY_KEY_SECRET;
    if (!keyId || !keySecret) {
      throw new Error("Payments are not configured.");
    }

    // #23: don't spawn a new provider subscription if the user already has an
    // active one — that's how rapid re-clicks create multiple Razorpay subs and
    // double-charge. A lifetime or still-valid paid sub blocks a fresh checkout.
    const { data: existing } = await context.supabase
      .from("subscriptions")
      .select("source, current_period_end")
      .eq("user_id", context.userId)
      .maybeSingle();
    const existingSub = existing as {
      source?: string;
      current_period_end?: string | null;
    } | null;
    if (existingSub) {
      const active =
        existingSub.source === "lifetime" ||
        (existingSub.current_period_end != null &&
          new Date(existingSub.current_period_end) > new Date());
      if (active) throw new Error("You already have an active subscription.");
    }

    // Rate-limit checkout starts so a burst of clicks can't each create a
    // provider subscription before the first one settles.
    const { isRateLimited } = await import("@/lib/rate-limit.server");
    if (await isRateLimited(`checkout:${context.userId}`, 60, 3)) {
      throw new Error("Too many checkout attempts. Please wait a moment.");
    }

    // Resolve the local plan -> Razorpay plan_id (provider_ref). Reading via
    // the caller's client is fine: plans are world-readable, and we only need
    // the mapping, not any privileged data.
    const { data: plan, error } = await context.supabase
      .from("plans" as never)
      .select("provider_ref, interval")
      .eq("id" as never, data.planId as never)
      .maybeSingle();
    if (error) throw new Error(error.message);
    const planRow = plan as { provider_ref?: string; interval?: string } | null;
    const razorpayPlanId = planRow?.provider_ref;
    if (!razorpayPlanId) {
      throw new Error("This plan isn't available for checkout yet.");
    }

    // total_count is the number of BILLING CYCLES, and a cycle is one plan
    // interval — so it must scale with the interval, not be a flat 12. A flat 12
    // on an annual plan meant 12 yearly charges (a 12-year commitment). Give
    // each a ~5-year horizon: 60 monthly cycles, or 5 annual cycles. Razorpay
    // auto-charges each cycle; the user cancels whenever they like.
    const totalCount = planRow?.interval === "annual" ? 5 : 60;

    const auth = Buffer.from(`${keyId}:${keySecret}`).toString("base64");
    const res = await fetch("https://api.razorpay.com/v1/subscriptions", {
      method: "POST",
      headers: {
        Authorization: `Basic ${auth}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        plan_id: razorpayPlanId,
        total_count: totalCount,
        customer_notify: 1,
        notes: { user_id: context.userId },
      }),
    });

    if (!res.ok) {
      const body = await res.text();
      console.error("razorpay_subscription_create_failed", res.status, body.slice(0, 500));
      throw new Error("Couldn't start checkout. Please try again.");
    }

    const sub = (await res.json()) as { id?: string };
    if (!sub.id) throw new Error("Checkout unavailable. Please try again.");

    return { subscriptionId: sub.id, keyId };
  });
