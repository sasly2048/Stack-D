import { createFileRoute } from "@tanstack/react-router";
import { createHmac, timingSafeEqual } from "node:crypto";
import { createClient, type SupabaseClient } from "@supabase/supabase-js";

/**
 * Razorpay subscription webhook. Deliberately unauthenticated at the transport
 * level — Razorpay calls it — so trust comes entirely from the HMAC signature
 * in the `x-razorpay-signature` header, verified against RAZORPAY_WEBHOOK_SECRET
 * over the RAW body. We reject anything that doesn't verify, then apply the
 * event idempotently (dedupe by Razorpay event id) via the service role.
 *
 * Secrets live in server env (Lovable Cloud / Supabase secrets), never the
 * bundle. The public Key ID is fine in the client; the Key Secret and this
 * Webhook Secret are server-only.
 */

function verifySignature(rawBody: string, signature: string, secret: string): boolean {
  const expected = createHmac("sha256", secret).update(rawBody).digest("hex");
  // Constant-time compare. Lengths must match for timingSafeEqual; the hex
  // digest is fixed-length, but guard anyway.
  const a = Buffer.from(expected, "utf8");
  const b = Buffer.from(signature, "utf8");
  if (a.length !== b.length) return false;
  return timingSafeEqual(a, b);
}

/** Map a Razorpay plan_id back to our local plan (id + tier) via the plans table. */
async function localPlanForRazorpay(
  supabase: SupabaseClient,
  planId: string,
): Promise<{ id: string; tier: "pro" | "elite" } | null> {
  const { data } = await supabase
    .from("plans")
    .select("id, tier")
    .eq("provider_ref", planId)
    .maybeSingle();
  const row = data as { id?: string; tier?: string } | null;
  if (!row?.id || (row.tier !== "pro" && row.tier !== "elite")) return null;
  return { id: row.id, tier: row.tier };
}

export const Route = createFileRoute("/api/public/razorpay-webhook")({
  server: {
    handlers: {
      POST: async ({ request }) => {
        const secret = process.env.RAZORPAY_WEBHOOK_SECRET;
        const supabaseUrl = process.env.SUPABASE_URL;
        const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;

        if (!secret || !supabaseUrl || !serviceKey) {
          console.error("razorpay_webhook_misconfigured");
          return Response.json({ error: "Server configuration error" }, { status: 500 });
        }

        // Read the RAW body — signature is computed over exact bytes, so we
        // must not re-serialize parsed JSON.
        const rawBody = await request.text();
        const signature = request.headers.get("x-razorpay-signature") ?? "";
        const eventId = request.headers.get("x-razorpay-event-id") ?? "";

        if (!signature || !verifySignature(rawBody, signature, secret)) {
          return Response.json({ error: "Invalid signature" }, { status: 401 });
        }

        let event: {
          event?: string;
          payload?: {
            subscription?: {
              entity?: {
                id?: string;
                plan_id?: string;
                current_end?: number; // unix seconds
                notes?: Record<string, string>;
              };
            };
          };
        };
        try {
          event = JSON.parse(rawBody);
        } catch {
          return Response.json({ error: "Bad payload" }, { status: 400 });
        }

        const eventType = event.event ?? "unknown";
        const supabase = createClient(supabaseUrl, serviceKey);

        // We only act on events that establish/extend paid access. Cancellation
        // and completion simply let current_period_end lapse — my_entitlement()
        // already collapses an expired sub to free, so no write is needed. These
        // are ignored BEFORE the idempotency claim so they never occupy the
        // ledger and can't block a retry of something meaningful.
        const ACTIVATING = new Set([
          "subscription.activated",
          "subscription.charged",
          "subscription.resumed",
        ]);
        if (!ACTIVATING.has(eventType)) {
          return Response.json({ ok: true, ignored: eventType });
        }

        // Idempotency with real processing state: claim the event as
        // 'processing'. Only a genuinely 'processed' event is skipped — a prior
        // failed/crashed attempt returns 'new' so Razorpay's retry re-runs it.
        const dedupeId = eventId || signature.slice(0, 64);
        const { data: claim, error: claimErr } = await supabase.rpc("begin_webhook_event", {
          _id: dedupeId,
          _type: eventType,
        });
        if (claimErr) {
          console.error("razorpay_webhook_claim_failed", claimErr);
          // 500 → Razorpay retries. Nothing was marked processed.
          return Response.json({ error: "Server error" }, { status: 500 });
        }
        if (claim === "processed") {
          return Response.json({ ok: true, deduped: true });
        }

        const sub = event.payload?.subscription?.entity;
        const planId = sub?.plan_id;
        const subId = sub?.id;
        // user id is passed through when we create the subscription, via notes.
        const userId = sub?.notes?.user_id;
        const currentEnd = sub?.current_end;

        // Helper: on any failure past the claim, mark the event failed (keeps it
        // retryable + visible) and return 5xx so Razorpay redelivers.
        const failAndRetry = async (reason: string, extra?: unknown) => {
          console.error(`razorpay_webhook_${reason}`, extra);
          await supabase.rpc("fail_webhook_event", { _id: dedupeId });
          return Response.json({ error: reason }, { status: 500 });
        };

        if (!planId || !subId || !userId || !currentEnd) {
          return failAndRetry("missing_fields", { planId, subId, userId, currentEnd });
        }

        const localPlan = await localPlanForRazorpay(supabase, planId);
        if (!localPlan) {
          return failAndRetry("unknown_plan", { planId });
        }

        const periodEnd = new Date(currentEnd * 1000).toISOString();
        const { error: grantErr } = await supabase.rpc("grant_subscription", {
          _user_id: userId,
          _tier: localPlan.tier,
          _provider_ref: subId,
          _period_end: periodEnd,
          _plan_id: localPlan.id,
        });
        if (grantErr) {
          return failAndRetry("grant_failed", grantErr);
        }

        // Provisioning succeeded — NOW mark processed. If this mark itself fails,
        // a redelivery re-grants (grant_subscription is an idempotent upsert), so
        // it's safe.
        const { error: completeErr } = await supabase.rpc("complete_webhook_event", {
          _id: dedupeId,
        });
        if (completeErr) {
          console.error("razorpay_webhook_complete_failed", completeErr);
          // Grant already applied; a retry is harmless (idempotent upsert).
        }

        return Response.json({ ok: true, tier: localPlan.tier, until: periodEnd });
      },
    },
  },
});
