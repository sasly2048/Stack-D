import { createServerFn } from "@tanstack/react-start";
import { getRequestIP, getRequestHeader } from "@tanstack/react-start/server";

export type AuthProvider = "apple" | "google" | "email";

const SIGNIN_WINDOW_SEC = 60;
const SIGNIN_MAX_HITS = 10; // per (provider, ip)
const FP_WINDOW_SEC = 60;
const FP_MAX_HITS = 15; // per (provider, ip+fp) — slightly higher; same IP, multiple tabs
const EMAIL_FAILURE_WINDOW_SEC = 600;
const EMAIL_LOCKOUT_THRESHOLD = 5;

export type GuardResult =
  | { ok: true }
  | { ok: false; code: "rate_limited" | "locked_out" | "invalid_input" | "captcha_required" | "captcha_failed"; message: string };

function normEmail(e: unknown): string | null {
  if (typeof e !== "string") return null;
  const t = e.normalize("NFKC").trim().toLowerCase();
  if (!t || t.length > 320) return null;
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(t)) return null;
  return t;
}

function normFp(fp: unknown): string {
  if (typeof fp !== "string") return "nofp";
  const t = fp.replace(/[^a-zA-Z0-9_-]/g, "").slice(0, 64);
  return t || "nofp";
}

function getIp(): string {
  try { return getRequestIP({ xForwardedFor: true }) ?? "unknown"; } catch { return "unknown"; }
}
function getUa(): string {
  try { return (getRequestHeader("user-agent") ?? "").slice(0, 300); } catch { return ""; }
}

export const guardSignIn = createServerFn({ method: "POST" })
  .inputValidator((d: { provider: AuthProvider; email?: string | null; fp?: string | null; captchaToken?: string | null; requireCaptcha?: boolean }) => ({
    provider: (d?.provider ?? "email") as AuthProvider,
    email: normEmail(d?.email ?? null),
    fp: normFp(d?.fp ?? null),
    captchaToken: typeof d?.captchaToken === "string" ? d.captchaToken : null,
    requireCaptcha: !!d?.requireCaptcha,
  }))
  .handler(async ({ data }): Promise<GuardResult> => {
    if (!["apple", "google", "email"].includes(data.provider)) {
      return { ok: false, code: "invalid_input", message: "Unknown provider." };
    }
    const { supabaseAdmin } = await import("@/integrations/supabase/client.server");
    const { verifyTurnstileToken } = await import("@/lib/security-alerts.server");
    const ip = getIp();

    // IP throttle
    const { data: ipLimited } = await supabaseAdmin.rpc("check_and_record_hit", {
      _key: `signin:${data.provider}:${ip}`,
      _window_seconds: SIGNIN_WINDOW_SEC,
      _max_hits: SIGNIN_MAX_HITS,
    });
    if (ipLimited) {
      return { ok: false, code: "rate_limited", message: "Too many attempts. Wait a minute and retry." };
    }

    // IP + device fingerprint throttle
    const { data: fpLimited } = await supabaseAdmin.rpc("check_and_record_hit", {
      _key: `signin:${data.provider}:${ip}:${data.fp}`,
      _window_seconds: FP_WINDOW_SEC,
      _max_hits: FP_MAX_HITS,
    });
    if (fpLimited) {
      return { ok: false, code: "rate_limited", message: "Too many attempts from this device. Wait a minute and retry." };
    }

    // Email-targeted lockout (per IP)
    if (data.provider === "email" && data.email) {
      const { data: failures } = await supabaseAdmin.rpc("recent_auth_failures", {
        _provider: "email",
        _email: data.email,
        _window_seconds: EMAIL_FAILURE_WINDOW_SEC,
        _ip: ip,
      } as never);
      if ((failures ?? 0) >= EMAIL_LOCKOUT_THRESHOLD) {
        return {
          ok: false,
          code: "locked_out",
          message: "This account is temporarily locked after too many failed attempts. Try again in 10 minutes.",
        };
      }
    }

    // CAPTCHA: required on signup + password reset (caller passes requireCaptcha).
    // Adaptive on sign-in: enforced once recent failures cross 2 for same email/IP.
    let needCaptcha = data.requireCaptcha;
    if (!needCaptcha && data.provider === "email" && data.email) {
      const { data: recent } = await supabaseAdmin.rpc("recent_auth_failures", {
        _provider: "email",
        _email: data.email,
        _window_seconds: EMAIL_FAILURE_WINDOW_SEC,
        _ip: ip,
      } as never);
      if ((recent ?? 0) >= 2) needCaptcha = true;
    }
    if (needCaptcha) {
      if (!data.captchaToken) {
        return { ok: false, code: "captcha_required", message: "Please complete the CAPTCHA challenge." };
      }
      const v = await verifyTurnstileToken(data.captchaToken, ip);
      if (!v.ok) {
        return { ok: false, code: "captcha_failed", message: "CAPTCHA verification failed. Try again." };
      }
    }

    return { ok: true };
  });

export const logAuthAttempt = createServerFn({ method: "POST" })
  .inputValidator((d: {
    provider: AuthProvider;
    email?: string | null;
    success: boolean;
    reason?: string | null;
  }) => ({
    provider: (d?.provider ?? "email") as AuthProvider,
    email: normEmail(d?.email ?? null),
    success: !!d?.success,
    reason: typeof d?.reason === "string" ? d.reason.slice(0, 200) : null,
  }))
  .handler(async ({ data }): Promise<{ logged: boolean; alerted?: boolean }> => {
    try {
      const { supabaseAdmin } = await import("@/integrations/supabase/client.server");
      const ip = getIp();
      const ua = getUa();

      const { data: limited } = await supabaseAdmin.rpc("check_and_record_hit", {
        _key: `logauth:${ip}`,
        _window_seconds: EMAIL_FAILURE_WINDOW_SEC,
        _max_hits: EMAIL_LOCKOUT_THRESHOLD - 1,
      });
      if (limited) return { logged: false };

      await supabaseAdmin.from("auth_attempts").insert({
        provider: data.provider,
        email: data.email,
        success: data.success,
        reason: data.reason,
        ip,
        user_agent: ua,
      });

      // Alert on suspicious patterns: per-email failure spike.
      let alerted = false;
      if (!data.success && data.provider === "email" && data.email) {
        const { FAILURE_SPIKE_WINDOW_SEC, FAILURE_SPIKE_THRESHOLD, maybeDispatchAuthAlert } =
          await import("@/lib/security-alerts.server");
        const { data: failuresAcrossIps } = await supabaseAdmin.rpc("recent_auth_failures", {
          _provider: "email",
          _email: data.email,
          _window_seconds: FAILURE_SPIKE_WINDOW_SEC,
        } as never);
        const count = failuresAcrossIps ?? 0;
        if (count >= FAILURE_SPIKE_THRESHOLD) {
          const res = await maybeDispatchAuthAlert({
            kind: "email_failure_spike",
            email: data.email,
            failureCount: count,
            ip,
            userAgent: ua,
          });
          alerted = res.dispatched;
        }
      }

      return { logged: true, alerted };
    } catch (e) {
      console.error("log_auth_attempt_failed", e);
      return { logged: false };
    }
  });
