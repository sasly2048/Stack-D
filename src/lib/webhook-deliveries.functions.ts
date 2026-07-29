import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";
import { z } from "zod";

export interface Delivery {
  id: string;
  webhook_id: string;
  event: string;
  status_code: number | null;
  ok: boolean;
  response_snippet: string | null;
  attempt: number;
  created_at: string;
}

export const listDeliveries = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ webhookId: z.string().uuid() }).parse(d))
  .handler(async ({ data, context }): Promise<Delivery[]> => {
    const { data: rows, error } = await context.supabase
      .from("webhook_deliveries")
      .select("id, webhook_id, event, status_code, ok, response_snippet, attempt, created_at")
      .eq("webhook_id", data.webhookId)
      .eq("user_id", context.userId)
      .order("created_at", { ascending: false })
      .limit(50);
    if (error) throw new Error(error.message);
    return rows as Delivery[];
  });

/**
 * Fire a signed sample payload at the endpoint and log the response.
 * Uses supabaseAdmin only for the insert (RLS is user-select-only on deliveries).
 */
export const testWebhook = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ webhookId: z.string().uuid() }).parse(d))
  .handler(async ({ data, context }): Promise<Delivery> => {
    const { data: wh, error } = await context.supabase
      .from("webhooks")
      .select("id, url, secret, active")
      .eq("id", data.webhookId)
      .eq("user_id", context.userId)
      .maybeSingle();
    if (error || !wh) throw new Error("not_found");

    const payload = JSON.stringify({
      event: "session.complete",
      test: true,
      timestamp: new Date().toISOString(),
      data: { room: "TEST-000", score: 92, tier: "obsidian", duration_seconds: 1500 },
    });

    // HMAC-SHA256 signature over the raw body
    const enc = new TextEncoder();
    const key = await crypto.subtle.importKey(
      "raw",
      enc.encode(wh.secret ?? ""),
      { name: "HMAC", hash: "SHA-256" },
      false,
      ["sign"],
    );
    const sigBuf = await crypto.subtle.sign("HMAC", key, enc.encode(payload));
    const signature = Array.from(new Uint8Array(sigBuf))
      .map((b) => b.toString(16).padStart(2, "0"))
      .join("");

    let status: number | null = null;
    let ok = false;
    let snippet = "";
    try {
      const res = await fetch(wh.url, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-Stackd-Signature": `sha256=${signature}`,
          "X-Stackd-Event": "session.complete",
          "User-Agent": "Stackd-Webhooks/1.0 (test)",
        },
        body: payload,
        signal: AbortSignal.timeout(10_000),
      });
      status = res.status;
      ok = res.ok;
      const txt = await res.text().catch(() => "");
      snippet = txt.slice(0, 500);
    } catch (err) {
      snippet = `network_error: ${(err as Error).message}`.slice(0, 500);
    }

    const { supabaseAdmin } = await import("@/integrations/supabase/client.server");
    const { data: row, error: insErr } = await supabaseAdmin
      .from("webhook_deliveries")
      .insert({
        webhook_id: wh.id,
        user_id: context.userId,
        event: "session.complete",
        status_code: status,
        ok,
        response_snippet: snippet,
        attempt: 1,
      })
      .select("id, webhook_id, event, status_code, ok, response_snippet, attempt, created_at")
      .single();
    if (insErr) throw new Error(insErr.message);
    return row as Delivery;
  });
