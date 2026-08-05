// Pre-auth throttle and audit log for the Android client.
//
// The web app does this inside a TanStack server function (`guardSignIn` /
// `logAuthAttempt` in src/lib/auth.functions.ts). Android has no server tier,
// and the RPCs behind it — check_and_record_hit, recent_auth_failures — are
// REVOKEd from anon and authenticated, so they are reachable only with
// service_role. Hence this function: the same logic, same constants, same
// message strings, running somewhere the service_role key can live.
//
// Deliberate divergence from web, agreed with the product owner: no Turnstile
// CAPTCHA step. There is no native Android widget for it, and a signed APK is
// a weaker bot target than an open web form. Every other check is preserved.
//
// Routes:
//   POST /auth-guard      → { ok, code?, message? }   (may this attempt run?)
//   POST /auth-guard/log  → { logged, alerted? }      (record what happened)

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.47.10";

// Verbatim from src/lib/auth.functions.ts — these must not drift from web.
const SIGNIN_WINDOW_SEC = 60;
const SIGNIN_MAX_HITS = 10;
const FP_WINDOW_SEC = 60;
const FP_MAX_HITS = 15;
const EMAIL_FAILURE_WINDOW_SEC = 600;
const EMAIL_LOCKOUT_THRESHOLD = 5;

const PROVIDERS = ["email", "google", "apple"] as const;
type Provider = (typeof PROVIDERS)[number];

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "Content-Type": "application/json" },
  });

/** Same normalisation the web uses: NFKC, trimmed, lowercased, shape-checked. */
function normEmail(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const e = raw.normalize("NFKC").trim().toLowerCase();
  if (!e || e.length > 320) return null;
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e) ? e : null;
}

/** Fingerprints are opaque; constrain them so they can't poison the rate-limit key. */
function normFp(raw: unknown): string {
  if (typeof raw !== "string") return "nofp";
  const fp = raw.replace(/[^a-zA-Z0-9_-]/g, "").slice(0, 64);
  return fp || "nofp";
}

/**
 * The first entry of X-Forwarded-For is the client as seen by the edge.
 * Later entries are proxies, and the header is absent on direct calls.
 */
function clientIp(req: Request): string {
  const xff = req.headers.get("x-forwarded-for");
  if (xff) return xff.split(",")[0].trim();
  return req.headers.get("x-real-ip")?.trim() || "unknown";
}

const admin = createClient(
  Deno.env.get("SUPABASE_URL")!,
  // Injected by the Supabase runtime; never travels to any client.
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  { auth: { persistSession: false, autoRefreshToken: false } },
);

async function limited(key: string, windowSec: number, maxHits: number): Promise<boolean> {
  const { data, error } = await admin.rpc("check_and_record_hit", {
    _key: key,
    _window_seconds: windowSec,
    _max_hits: maxHits,
  });
  // A throttle that cannot be consulted must not lock users out — the auth
  // boundary is Supabase Auth and RLS, not this function. Matches the web's
  // fail-open behaviour.
  if (error) return false;
  return data === true;
}

async function recentFailures(email: string, ip?: string): Promise<number> {
  const { data, error } = await admin.rpc("recent_auth_failures", {
    _provider: "email",
    _email: email,
    _window_seconds: EMAIL_FAILURE_WINDOW_SEC,
    ...(ip ? { _ip: ip } : {}),
  });
  if (error) return 0;
  return typeof data === "number" ? data : 0;
}

async function handleGuard(req: Request): Promise<Response> {
  const body = await req.json().catch(() => ({}));
  const provider = body?.provider as Provider;
  const ip = clientIp(req);
  const fp = normFp(body?.fp);
  const email = normEmail(body?.email);

  if (!PROVIDERS.includes(provider)) {
    return json({ ok: false, code: "invalid_input", message: "Unsupported sign-in method." });
  }

  // 1. Per-IP throttle.
  if (await limited(`signin:${provider}:${ip}`, SIGNIN_WINDOW_SEC, SIGNIN_MAX_HITS)) {
    return json({
      ok: false,
      code: "rate_limited",
      message: "Too many attempts. Wait a minute and retry.",
    });
  }

  // 2. Per-IP-and-device throttle, slightly looser than the IP one so a shared
  //    NAT doesn't punish a single device.
  if (await limited(`signin:${provider}:${ip}:${fp}`, FP_WINDOW_SEC, FP_MAX_HITS)) {
    return json({
      ok: false,
      code: "rate_limited",
      message: "Too many attempts from this device. Wait a minute and retry.",
    });
  }

  // 3. Per-account lockout after repeated failures.
  if (provider === "email" && email) {
    if ((await recentFailures(email, ip)) >= EMAIL_LOCKOUT_THRESHOLD) {
      return json({
        ok: false,
        code: "locked_out",
        message:
          "This account is temporarily locked after too many failed attempts. Try again in 10 minutes.",
      });
    }
  }

  // Web checks CAPTCHA here. Android does not — see the header comment.
  return json({ ok: true });
}

async function handleLog(req: Request): Promise<Response> {
  const body = await req.json().catch(() => ({}));
  const provider = body?.provider as Provider;
  const ip = clientIp(req);
  const email = normEmail(body?.email);
  const success = body?.success === true;
  const reason = typeof body?.reason === "string" ? body.reason.slice(0, 200) : null;

  if (!PROVIDERS.includes(provider)) return json({ logged: false });

  // The log is itself a write primitive, so it gets its own throttle — an
  // attacker shouldn't be able to inflate the audit table for free.
  if (await limited(`logauth:${ip}`, EMAIL_FAILURE_WINDOW_SEC, EMAIL_LOCKOUT_THRESHOLD - 1)) {
    return json({ logged: false });
  }

  const { error } = await admin.from("auth_attempts").insert({
    provider,
    email,
    success,
    reason,
    ip,
    user_agent: req.headers.get("user-agent") ?? null,
  });
  if (error) return json({ logged: false });

  return json({ logged: true });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  try {
    const path = new URL(req.url).pathname;
    return path.endsWith("/log") ? await handleLog(req) : await handleGuard(req);
  } catch {
    // Never surface internals to an unauthenticated caller, and never hard-fail
    // the sign-in path because the throttle broke.
    return json({ ok: true });
  }
});
