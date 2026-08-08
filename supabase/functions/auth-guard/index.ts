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
 * The peer address of the TCP connection, taken from the runtime rather than
 * from any header.
 *
 * X-Forwarded-For is explicitly NOT used. A caller can send any XFF value it
 * likes, so keying a rate limit on it means an attacker rotates one header and
 * the throttle never fires — the limit would protect only honest clients. The
 * leftmost entry is the most forgeable part of all, being whatever the original
 * caller claimed. `remoteAddr` cannot be spoofed without actually controlling
 * the source address of the connection.
 */
function clientIp(info: Deno.ServeHandlerInfo): string {
  const addr = info.remoteAddr;
  return addr.transport === "tcp" || addr.transport === "udp" ? addr.hostname : "unknown";
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

async function handleGuard(req: Request, ip: string): Promise<Response> {
  const body = await req.json().catch(() => ({}));
  const provider = body?.provider as Provider;
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

/**
 * Runs the password sign-in here, so the outcome that reaches `auth_attempts`
 * is one this function observed rather than one the caller asserted.
 *
 * This is the whole point of the endpoint. An earlier revision exposed a
 * `/log` route that took `{email, success}` from the client and inserted it
 * directly. Because `recent_auth_failures` counts exactly those rows, five
 * unauthenticated POSTs claiming failure would lock any known address out of
 * its own account for ten minutes — an audit log turned into a remote lockout
 * weapon. Authenticating the caller would not have fixed it either: a signed-in
 * user could still lie about someone else's address. The only sound fix is to
 * stop accepting the claim, so no client-supplied outcome is trusted anywhere
 * below.
 *
 * Note the web app (`logAuthAttempt` in src/lib/auth.functions.ts) still has
 * the original weakness and should be migrated onto this same shape.
 */
async function handleSignIn(req: Request, ip: string): Promise<Response> {
  const body = await req.json().catch(() => ({}));
  const email = normEmail(body?.email);
  const password = typeof body?.password === "string" ? body.password : null;
  const fp = normFp(body?.fp);
  const userAgent = req.headers.get("user-agent") ?? null;

  if (!email || !password) {
    return json({ ok: false, code: "invalid_input", message: "Enter your email and password." });
  }

  // Same three gates as handleGuard, applied to the credential path.
  if (await limited(`signin:email:${ip}`, SIGNIN_WINDOW_SEC, SIGNIN_MAX_HITS)) {
    return json({
      ok: false,
      code: "rate_limited",
      message: "Too many attempts. Wait a minute and retry.",
    });
  }
  if (await limited(`signin:email:${ip}:${fp}`, FP_WINDOW_SEC, FP_MAX_HITS)) {
    return json({
      ok: false,
      code: "rate_limited",
      message: "Too many attempts from this device. Wait a minute and retry.",
    });
  }
  if ((await recentFailures(email, ip)) >= EMAIL_LOCKOUT_THRESHOLD) {
    return json({
      ok: false,
      code: "locked_out",
      message:
        "This account is temporarily locked after too many failed attempts. Try again in 10 minutes.",
    });
  }

  const { data, error } = await admin.auth.signInWithPassword({ email, password });

  // Observed outcome — not a client claim.
  await admin.from("auth_attempts").insert({
    provider: "email",
    email,
    success: !error,
    reason: error ? error.message.slice(0, 200) : null,
    ip,
    user_agent: userAgent,
  });

  if (error || !data.session) {
    // Deliberately uniform: distinguishing "no such account" from "wrong
    // password" here would turn this into an account-existence oracle.
    return json({ ok: false, code: "invalid_credentials", message: "Invalid email or password." });
  }

  return json({
    ok: true,
    access_token: data.session.access_token,
    refresh_token: data.session.refresh_token,
  });
}

Deno.serve(async (req, info) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const ip = clientIp(info);

  try {
    const path = new URL(req.url).pathname;
    // /signin performs the credential check; the bare route only advises on
    // non-password providers (Google), where the token exchange happens on the
    // client and there is no password for us to verify.
    return path.endsWith("/signin")
      ? await handleSignIn(req, ip)
      : await handleGuard(req, ip);
  } catch {
    // Never leak internals to an unauthenticated caller. Note this fails
    // CLOSED for /signin — an unexpected error there must not be read as a
    // successful sign-in — while the advisory route's own failure is handled
    // by the client treating an unreachable guard as permissive.
    return json({ ok: false, code: "server_error", message: "Something went wrong. Please retry." }, 500);
  }
});
