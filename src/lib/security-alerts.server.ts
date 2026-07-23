// Server-only helpers: dispatch suspicious-auth alerts via email + webhook.
// Imported lazily inside server-function handlers — never at module scope of
// a client-reachable file.

const ALERT_COOLDOWN_SEC = 30 * 60; // dedupe identical alerts for 30 min
export const FAILURE_SPIKE_WINDOW_SEC = 600; // 10 min
export const FAILURE_SPIKE_THRESHOLD = 5; // >=5 failures per email/10min

export type AlertContext = {
  kind: "email_failure_spike";
  email: string;
  failureCount: number;
  ip: string;
  userAgent: string;
};

async function postWebhook(ctx: AlertContext) {
  const url = process.env.ALERT_WEBHOOK_URL;
  if (!url) return { ok: false, skipped: true as const };
  try {
    const r = await fetch(url, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        type: "auth.alert",
        ...ctx,
        at: new Date().toISOString(),
      }),
    });
    return { ok: r.ok, status: r.status };
  } catch (e) {
    console.error("alert_webhook_failed", e);
    return { ok: false, error: String(e) };
  }
}

async function sendAlertEmail(ctx: AlertContext) {
  const to = process.env.ADMIN_ALERT_EMAIL;
  if (!to) return { ok: false, skipped: true as const };
  try {
    const { supabaseAdmin } = await import("@/integrations/supabase/client.server");
    const subject = `Auth alert — failure spike on ${ctx.email}`;
    const html = `
      <h2>Suspicious authentication activity</h2>
      <p><strong>${ctx.failureCount}</strong> failed sign-in attempts for
      <code>${ctx.email}</code> in the last 10 minutes.</p>
      <ul>
        <li><strong>IP:</strong> ${ctx.ip}</li>
        <li><strong>User-Agent:</strong> ${ctx.userAgent || "unknown"}</li>
        <li><strong>Detected:</strong> ${new Date().toISOString()}</li>
      </ul>
      <p>The account is auto-locked for 10 minutes after the threshold is hit.</p>`;
    // enqueue_email RPC is provided by the Lovable email infrastructure once
    // setup_email_infra has run. If absent, this call simply errors and we
    // fall back to the webhook path.
    const { error } = await supabaseAdmin.rpc("enqueue_email" as never, {
      _queue: "transactional_emails",
      _template_name: "auth-alert",
      _recipient_email: to,
      _subject: subject,
      _html: html,
      _idempotency_key: `auth-alert-${ctx.email}-${Math.floor(Date.now() / (ALERT_COOLDOWN_SEC * 1000))}`,
    } as never);
    if (error) return { ok: false, error: error.message };
    return { ok: true };
  } catch (e) {
    return { ok: false, error: String(e) };
  }
}

/**
 * Dedupe-then-dispatch. Atomic insert guarantees only one alert per
 * (kind,subject) inside the cooldown window.
 */
export async function maybeDispatchAuthAlert(ctx: AlertContext) {
  try {
    const { supabaseAdmin } = await import("@/integrations/supabase/client.server");
    const { data: id } = await supabaseAdmin.rpc("record_auth_alert_if_new" as never, {
      _kind: ctx.kind,
      _subject: ctx.email,
      _cooldown_seconds: ALERT_COOLDOWN_SEC,
      _failure_count: ctx.failureCount,
      _details: { ip: ctx.ip, user_agent: ctx.userAgent },
    } as never);
    if (!id) return { dispatched: false, reason: "deduped" as const };

    const [webhook, email] = await Promise.all([postWebhook(ctx), sendAlertEmail(ctx)]);
    return { dispatched: true, webhook, email };
  } catch (e) {
    console.error("maybe_dispatch_auth_alert_failed", e);
    return { dispatched: false, reason: "error" as const };
  }
}

/**
 * Verify a Cloudflare Turnstile token server-side.
 * Returns true when the token is valid OR when no secret is configured
 * (development / pre-secret-rollout safe-mode).
 */
export async function verifyTurnstileToken(token: string | null | undefined, remoteIp: string) {
  const secret = process.env.TURNSTILE_SECRET_KEY;
  if (!secret) return { ok: true, reason: "no_secret" as const };
  if (!token || typeof token !== "string") return { ok: false, reason: "missing" as const };
  try {
    const body = new URLSearchParams({ secret, response: token, remoteip: remoteIp });
    const r = await fetch("https://challenges.cloudflare.com/turnstile/v0/siteverify", {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body,
    });
    const j = (await r.json()) as { success: boolean; "error-codes"?: string[] };
    return j.success
      ? { ok: true as const }
      : { ok: false as const, reason: "rejected" as const, codes: j["error-codes"] ?? [] };
  } catch (e) {
    console.error("turnstile_verify_failed", e);
    return { ok: false as const, reason: "exception" as const };
  }
}
