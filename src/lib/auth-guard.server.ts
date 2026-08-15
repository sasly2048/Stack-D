import { getRequestIP, getRequestHeader } from "@tanstack/react-start/server";

/** Shared constants — must not drift from src/lib/auth.functions.ts. */
export const SIGNIN_WINDOW_SEC = 60;
export const SIGNIN_MAX_HITS = 10;
export const FP_WINDOW_SEC = 60;
export const FP_MAX_HITS = 15;
export const EMAIL_FAILURE_WINDOW_SEC = 600;
export const EMAIL_LOCKOUT_THRESHOLD = 5;

export type GuardProvider = "email" | "google" | "apple";

export type GuardFailure = {
  ok: false;
  code: "rate_limited" | "locked_out" | "invalid_input" | "invalid_credentials";
  message: string;
};

export const CORS_HEADERS: Record<string, string> = {
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "POST, OPTIONS",
  "access-control-allow-headers": "content-type, apikey, authorization",
  "access-control-max-age": "86400",
};

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", ...CORS_HEADERS },
  });
}

export function normEmail(e: unknown): string | null {
  if (typeof e !== "string") return null;
  const t = e.normalize("NFKC").trim().toLowerCase();
  if (!t || t.length > 320) return null;
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(t)) return null;
  return t;
}

export function normFp(fp: unknown): string {
  if (typeof fp !== "string") return "nofp";
  const t = fp.replace(/[^a-zA-Z0-9_-]/g, "").slice(0, 64);
  return t || "nofp";
}

/**
 * Connection remote address only. X-Forwarded-For is client-controlled and
 * therefore trivially spoofable for a rate-limit key.
 */
export function getConnectionIp(): string {
  try {
    return getRequestIP() ?? "unknown";
  } catch {
    return "unknown";
  }
}

export function getUserAgent(): string {
  try {
    return (getRequestHeader("user-agent") ?? "").slice(0, 300);
  } catch {
    return "";
  }
}

type AdminClient = Awaited<
  typeof import("@/integrations/supabase/client.server")
>["supabaseAdmin"];

/** Fails open: an RPC error must never lock legitimate users out. */
async function hitLimited(
  admin: AdminClient,
  key: string,
  windowSeconds: number,
  maxHits: number,
): Promise<boolean> {
  try {
    const { data, error } = await admin.rpc("check_and_record_hit", {
      _key: key,
      _window_seconds: windowSeconds,
      _max_hits: maxHits,
    });
    if (error) return false;
    return !!data;
  } catch {
    return false;
  }
}

/** Fails open: returns 0 on any error. */
async function recentEmailFailures(
  admin: AdminClient,
  email: string,
  ip: string,
): Promise<number> {
  try {
    const { data, error } = await admin.rpc("recent_auth_failures", {
      _provider: "email",
      _email: email,
      _window_seconds: EMAIL_FAILURE_WINDOW_SEC,
      _ip: ip,
    } as never);
    if (error) return 0;
    return (data as number | null) ?? 0;
  } catch {
    return 0;
  }
}

/**
 * The three shared gates: IP throttle, IP+fingerprint throttle, and the
 * email-targeted lockout. No CAPTCHA step — Android has no widget.
 */
export async function runGuardGates(opts: {
  admin: AdminClient;
  provider: GuardProvider;
  email: string | null;
  fp: string;
  ip: string;
}): Promise<GuardFailure | null> {
  const { admin, provider, email, fp, ip } = opts;

  if (await hitLimited(admin, `signin:${provider}:${ip}`, SIGNIN_WINDOW_SEC, SIGNIN_MAX_HITS)) {
    return { ok: false, code: "rate_limited", message: "Too many attempts. Wait a minute and retry." };
  }

  if (await hitLimited(admin, `signin:${provider}:${ip}:${fp}`, FP_WINDOW_SEC, FP_MAX_HITS)) {
    return {
      ok: false,
      code: "rate_limited",
      message: "Too many attempts from this device. Wait a minute and retry.",
    };
  }

  if (provider === "email" && email) {
    const failures = await recentEmailFailures(admin, email, ip);
    if (failures >= EMAIL_LOCKOUT_THRESHOLD) {
      return {
        ok: false,
        code: "locked_out",
        message:
          "This account is temporarily locked after too many failed attempts. Try again in 10 minutes.",
      };
    }
  }

  return null;
}

export async function readJsonBody(request: Request): Promise<Record<string, unknown>> {
  try {
    const body = await request.json();
    return body && typeof body === "object" ? (body as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}
