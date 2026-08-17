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

    // Resolve the local plan -> Razorpay plan_id (provider_ref). Reading via
    // the caller's client is fine: plans are world-readable, and we only need
    // the mapping, not any privileged data.
    const { data: plan, error } = await context.supabase
      .from("plans" as never)
      .select("provider_ref")
      .eq("id" as never, data.planId as never)
      .maybeSingle();
    if (error) throw new Error(error.message);
    const razorpayPlanId = (plan as { provider_ref?: string } | null)?.provider_ref;
    if (!razorpayPlanId) {
      throw new Error("This plan isn't available for checkout yet.");
    }

    // Razorpay subscription: 12 billing cycles then it ends unless renewed;
    // total_count is required. Annual plans bill yearly, monthly bill monthly
    // — the plan itself carries the interval, we just set how many cycles.
    const auth = Buffer.from(`${keyId}:${keySecret}`).toString("base64");
    const res = await fetch("https://api.razorpay.com/v1/subscriptions", {
      method: "POST",
      headers: {
        Authorization: `Basic ${auth}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        plan_id: razorpayPlanId,
        total_count: 12,
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
