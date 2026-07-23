import { createFileRoute, useNavigate, Link } from "@tanstack/react-router";
import { useEffect, useRef, useState } from "react";
import { z } from "zod";
import { useServerFn } from "@tanstack/react-start";
import { supabase } from "@/integrations/supabase/client";
import { lovable } from "@/integrations/lovable";
import { useAuth } from "@/hooks/use-auth";
import { Logo } from "@/components/logo";
import { guardSignIn, logAuthAttempt } from "@/lib/auth.functions";
import { Turnstile, getDeviceFingerprint } from "@/components/turnstile";

const searchSchema = z.object({ next: z.string().optional() });

export const Route = createFileRoute("/auth")({
  validateSearch: searchSchema,
  head: () => ({ meta: [{ title: "Enter — Stack'd" }] }),
  component: Auth,
});

type ProviderKey = "apple" | "google" | "email";
const MAX_CONFIRM_ATTEMPTS = 3;

function Auth() {
  const { next } = Route.useSearch();
  const navigate = useNavigate();
  const { user, loading } = useAuth();
  const guard = useServerFn(guardSignIn);
  const log = useServerFn(logAuthAttempt);

  const [mode, setMode] = useState<"sign-in" | "sign-up">("sign-in");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [pending, setPending] = useState<ProviderKey | null>(null);
  const [errors, setErrors] = useState<Partial<Record<ProviderKey, string>>>({});
  const [confirmStep, setConfirmStep] = useState(false);
  const cooldown = useRef<Record<ProviderKey, number>>({ apple: 0, google: 0, email: 0 });
  const retryRefs = useRef<Partial<Record<ProviderKey, HTMLButtonElement | null>>>({});
  const emailRef = useRef<HTMLInputElement>(null);
  const [captchaToken, setCaptchaToken] = useState<string | null>(null);
  const [emailFailures, setEmailFailures] = useState(0);
  const fpRef = useRef<string>("nofp");
  useEffect(() => { fpRef.current = getDeviceFingerprint(); }, []);
  // CAPTCHA shown always on sign-up; on sign-in only after a failed attempt.
  const showCaptcha = mode === "sign-up" || emailFailures >= 1;

  useEffect(() => {
    if (!loading && user) setConfirmStep(true);
  }, [user, loading]);

  // Focus retry buttons when an inline error appears (a11y).
  useEffect(() => {
    (Object.keys(errors) as ProviderKey[]).forEach((k) => {
      if (errors[k] && retryRefs.current[k]) retryRefs.current[k]?.focus();
    });
  }, [errors]);

  const clientGuard = (k: ProviderKey) => {
    const now = Date.now();
    if (pending) return false;
    if (now - cooldown.current[k] < 1500) {
      setErr(k, "Slow down — try again in a moment.");
      return false;
    }
    cooldown.current[k] = now;
    return true;
  };

  const setErr = (k: ProviderKey, msg: string | null) =>
    setErrors((e) => ({ ...e, [k]: msg ?? undefined }));

  const onOAuth = async (provider: "google" | "apple") => {
    if (!clientGuard(provider)) return;
    setErr(provider, null);
    setPending(provider);

    // Server-side guard (persistent rate-limit + fingerprint throttle)
    const g = await guard({ data: { provider, fp: fpRef.current } }).catch(() => null);
    if (g && !g.ok) {
      setErr(provider, g.message);
      setPending(null);
      void log({ data: { provider, success: false, reason: g.code } });
      return;
    }

    try {
      const res = await lovable.auth.signInWithOAuth(provider, {
        redirect_uri: window.location.origin + "/auth" + (next ? `?next=${encodeURIComponent(next)}` : ""),
      });
      if (res.error) {
        const msg = `${provider === "apple" ? "Apple" : "Google"} sign-in failed. Please retry.`;
        setErr(provider, msg);
        setPending(null);
        void log({ data: { provider, success: false, reason: "provider_error" } });
        return;
      }
      // Redirected — let the browser navigate; useAuth handles return.
    } catch {
      setErr(provider, "Could not reach the provider. Check your connection and retry.");
      setPending(null);
      void log({ data: { provider, success: false, reason: "exception" } });
    }
  };

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!clientGuard("email")) return;
    setErr("email", null);
    setPending("email");

    const g = await guard({
      data: {
        provider: "email",
        email,
        fp: fpRef.current,
        captchaToken,
        requireCaptcha: mode === "sign-up" || showCaptcha,
      },
    }).catch(() => null);
    if (g && !g.ok) {
      setErr("email", g.message);
      setPending(null);
      void log({ data: { provider: "email", email, success: false, reason: g.code } });
      if (g.code === "captcha_required" || g.code === "captcha_failed") {
        setEmailFailures((n) => Math.max(n, 1));
        setCaptchaToken(null);
      }
      return;
    }

    try {
      if (mode === "sign-up") {
        const { error } = await supabase.auth.signUp({
          email, password,
          options: {
            emailRedirectTo: window.location.origin + "/auth" + (next ? `?next=${encodeURIComponent(next)}` : ""),
            data: { display_name: displayName || email.split("@")[0] },
          },
        });
        if (error) throw error;
        setErr("email", "Check your email to confirm your account.");
        void log({ data: { provider: "email", email, success: true, reason: "signup_email_sent" } });
      } else {
        const { error } = await supabase.auth.signInWithPassword({ email, password });
        if (error) throw error;
        void log({ data: { provider: "email", email, success: true } });
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Email sign-in failed. Please retry.";
      setErr("email", msg);
      setEmailFailures((n) => n + 1);
      setCaptchaToken(null);
      void log({ data: { provider: "email", email, success: false, reason: msg.slice(0, 120) } });
    } finally {
      setPending(null);
    }
  };

  if (confirmStep && user) {
    return (
      <VerifyStep
        user={user}
        next={next}
        onCancel={async () => {
          await supabase.auth.signOut();
          setConfirmStep(false);
          setTimeout(() => emailRef.current?.focus(), 50);
        }}
        onConfirm={() => navigate({ to: next ?? "/dashboard", replace: true })}
        log={log}
      />
    );
  }

  return (
    <div className="min-h-screen bg-obsidian text-silver flex flex-col">
      <a href="#auth-main" className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:bg-ember focus:text-obsidian focus:px-3 focus:py-2 focus:font-mono focus:text-xs focus:rounded">
        Skip to sign-in
      </a>
      <header className="px-6 py-6">
        <Link to="/" className="flex items-center gap-3">
          <Logo className="size-7" />
          <span className="font-mono text-xs tracking-[0.3em] uppercase">
            Stack&apos;d <span className="text-muted-foreground">/ Ritual.01</span>
          </span>
        </Link>
      </header>

      <main id="auth-main" className="flex-1 flex items-center justify-center px-6 py-12">
        <div className="w-full max-w-md animate-entrance">
          <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember mb-4">
            {mode === "sign-in" ? "AUTH / RETURN" : "AUTH / INITIATE"}
          </div>
          <h1 className="text-4xl font-extrabold tracking-tighter mb-10">
            {mode === "sign-in" ? "Re-enter the protocol." : "Claim your presence."}
          </h1>

          <div className="space-y-3" role="group" aria-label="Sign-in providers">
            <button
              type="button"
              onClick={() => onOAuth("apple")}
              disabled={!!pending}
              aria-busy={pending === "apple"}
              aria-describedby={errors.apple ? "apple-err" : undefined}
              aria-label="Continue with Apple"
              className="w-full bg-silver text-obsidian py-3.5 rounded-lg font-mono text-xs uppercase tracking-widest font-bold hover:bg-white transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-3 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember focus-visible:ring-offset-2 focus-visible:ring-offset-obsidian"
            >
              {pending === "apple" ? <Spinner className="text-obsidian" /> : <AppleIcon />}
              <span>{pending === "apple" ? "Connecting to Apple…" : "Continue with Apple"}</span>
            </button>
            <ProviderError
              id="apple-err"
              msg={errors.apple}
              onRetry={() => onOAuth("apple")}
              btnRef={(el) => { retryRefs.current.apple = el; }}
              providerLabel="Apple sign-in"
            />

            <button
              type="button"
              onClick={() => onOAuth("google")}
              disabled={!!pending}
              aria-busy={pending === "google"}
              aria-describedby={errors.google ? "google-err" : undefined}
              aria-label="Continue with Google"
              className="w-full bg-white/5 border border-white/15 text-silver py-3.5 rounded-lg font-mono text-xs uppercase tracking-widest font-bold hover:bg-white/10 transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-3 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember focus-visible:ring-offset-2 focus-visible:ring-offset-obsidian"
            >
              {pending === "google" ? <Spinner /> : <GoogleIcon />}
              <span>{pending === "google" ? "Connecting to Google…" : "Continue with Google"}</span>
            </button>
            <ProviderError
              id="google-err"
              msg={errors.google}
              onRetry={() => onOAuth("google")}
              btnRef={(el) => { retryRefs.current.google = el; }}
              providerLabel="Google sign-in"
            />
          </div>

          <div className="flex items-center gap-4 my-8">
            <div className="flex-1 h-px bg-white/10" />
            <span className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground">
              or continue with email
            </span>
            <div className="flex-1 h-px bg-white/10" />
          </div>

          <form onSubmit={onSubmit} className="space-y-4" aria-label="Email sign-in">
            {mode === "sign-up" && (
              <Field label="Display Name" htmlFor="auth-name">
                <input
                  id="auth-name" type="text" value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  className="auth-input" placeholder="Marcus A."
                  autoComplete="name"
                />
              </Field>
            )}
            <Field label="Email" htmlFor="auth-email">
              <input
                id="auth-email" ref={emailRef} type="email" required value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="auth-input" placeholder="you@domain.com"
                autoComplete="email" inputMode="email" spellCheck={false}
                aria-describedby={errors.email ? "email-err" : undefined}
              />
            </Field>
            <Field label="Password" htmlFor="auth-password">
              <input
                id="auth-password" type="password" required minLength={6} value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="auth-input" placeholder="••••••••"
                autoComplete={mode === "sign-in" ? "current-password" : "new-password"}
                aria-describedby={errors.email ? "email-err" : undefined}
              />
            </Field>
            {showCaptcha && (
              <div className="pt-1">
                <Turnstile action={mode === "sign-up" ? "signup" : "signin"} onToken={setCaptchaToken} />
              </div>
            )}
            <button
              type="submit"
              disabled={!!pending || (showCaptcha && !captchaToken)}
              aria-busy={pending === "email"}
              className="btn-ember w-full border border-silver/40 py-3.5 rounded-lg font-mono text-xs uppercase tracking-widest font-bold text-silver disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-3 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember focus-visible:ring-offset-2 focus-visible:ring-offset-obsidian"
            >
              {pending === "email" ? <Spinner /> : <MailIcon />}
              <span>{pending === "email"
                ? mode === "sign-in" ? "Signing in…" : "Creating account…"
                : mode === "sign-in" ? "Continue with Email" : "Create Account"}</span>
            </button>
            <ProviderError
              id="email-err"
              msg={errors.email}
              onRetry={() => onSubmit(new Event("submit") as unknown as React.FormEvent)}
              btnRef={(el) => { retryRefs.current.email = el; }}
              providerLabel="Email sign-in"
            />
          </form>

          <button
            type="button"
            onClick={() => setMode(mode === "sign-in" ? "sign-up" : "sign-in")}
            className="mt-8 w-full text-center font-mono text-[10px] uppercase tracking-widest text-muted-foreground hover:text-ember transition-colors focus-visible:outline-none focus-visible:text-ember"
          >
            {mode === "sign-in" ? "No protocol key? Initiate →" : "Already aligned? Return →"}
          </button>
        </div>
      </main>
      <style>{`.auth-input{width:100%;background:rgba(255,255,255,.05);border:1px solid rgba(255,255,255,.1);border-radius:.5rem;padding:.875rem 1rem;color:#E2E2E2;font-size:.875rem;outline:none;transition:border-color .25s, box-shadow .25s, background .25s}.auth-input:focus{border-color:rgba(201,135,74,.6);box-shadow:0 0 0 4px rgba(201,135,74,.08);background:rgba(255,255,255,.07)}`}</style>
    </div>
  );
}

function VerifyStep({
  user, next, onCancel, onConfirm, log,
}: {
  user: { id: string; email?: string | null };
  next?: string;
  onCancel: () => void | Promise<void>;
  onConfirm: () => void;
  log: (input: { data: { provider: "email" | "apple" | "google"; email?: string | null; success: boolean; reason?: string | null } }) => Promise<unknown>;
}) {
  const [busy, setBusy] = useState<"confirm" | "cancel" | null>(null);
  const [challenge, setChallenge] = useState("");
  const [attempts, setAttempts] = useState(0);
  const [autoSignedOut, setAutoSignedOut] = useState(false);
  const confirmRef = useRef<HTMLButtonElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const noEmail = !user.email;

  // 4-char challenge derived from the user id — deterministic across renders.
  const expected = user.id.replace(/-/g, "").slice(0, 4).toUpperCase();

  useEffect(() => {
    (noEmail ? inputRef.current : confirmRef.current)?.focus();
  }, [noEmail]);

  const handleConfirm = async () => {
    if (busy) return;
    if (noEmail) {
      if (challenge.trim().toUpperCase() !== expected) {
        const nextAttempts = attempts + 1;
        setAttempts(nextAttempts);
        void log({ data: { provider: "email", email: user.email, success: false, reason: "verify_challenge_failed" } });
        if (nextAttempts >= MAX_CONFIRM_ATTEMPTS) {
          setBusy("cancel");
          setAutoSignedOut(true);
          void log({ data: { provider: "email", email: user.email, success: false, reason: "auto_signout_max_attempts" } });
          await onCancel();
          setBusy(null);
        }
        return;
      }
    }
    setBusy("confirm");
    void log({ data: { provider: "email", email: user.email, success: true, reason: "verified" } });
    // Brief perceived loading so the state transition reads as intentional.
    setTimeout(onConfirm, 250);
  };

  const handleCancel = async () => {
    if (busy) return;
    setBusy("cancel");
    await onCancel();
    setBusy(null);
  };

  return (
    <div className="min-h-screen bg-obsidian text-silver flex flex-col">
      <header className="px-6 py-6">
        <Link to="/" className="flex items-center gap-3">
          <Logo className="size-7" />
          <span className="font-mono text-xs tracking-[0.3em] uppercase">
            Stack&apos;d <span className="text-muted-foreground">/ Verify</span>
          </span>
        </Link>
      </header>
      <main className="flex-1 flex items-center justify-center px-6 py-12">
        <div className="w-full max-w-md animate-entrance" role="region" aria-labelledby="verify-title">
          <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember mb-4">
            AUTH / STEP 02 — Confirm Identity
          </div>
          <h1 id="verify-title" className="text-4xl font-extrabold tracking-tighter mb-8">
            Is this you?
          </h1>

          <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-6 mb-6">
            <div className="font-mono text-[10px] uppercase tracking-widest text-muted-foreground mb-2">
              Signed-in as
            </div>
            {noEmail ? (
              <>
                <div className="text-lg font-medium text-silver">No email returned by provider</div>
                <div className="text-xs text-silver-dim mt-2 leading-relaxed">
                  Your provider (likely Apple with Hide My Email) didn&apos;t share an email.
                  Confirm the verification code below to continue, or sign out and try a
                  different provider.
                </div>
                <div className="mt-4 font-mono text-[10px] uppercase tracking-widest text-muted-foreground">
                  Account ID
                </div>
                <div className="font-mono text-xs break-all text-silver/80">{user.id}</div>
              </>
            ) : (
              <div className="text-lg font-medium break-all">{user.email}</div>
            )}
          </div>

          {noEmail && (
            <div className="mb-6">
              <label htmlFor="verify-challenge" className="block font-mono text-[10px] uppercase tracking-widest text-muted-foreground mb-2">
                Type these 4 characters to confirm:{" "}
                <span className="text-ember tracking-[0.4em]" aria-hidden="true">{expected}</span>
              </label>
              <input
                ref={inputRef}
                id="verify-challenge"
                type="text"
                value={challenge}
                onChange={(e) => setChallenge(e.target.value.toUpperCase().slice(0, 4))}
                onKeyDown={(e) => { if (e.key === "Enter") handleConfirm(); }}
                aria-invalid={attempts > 0 || undefined}
                aria-describedby="verify-attempts"
                className="auth-input font-mono tracking-[0.4em] text-center text-lg"
                maxLength={4}
                autoComplete="one-time-code"
                autoCapitalize="characters"
                spellCheck={false}
              />
              <div id="verify-attempts" aria-live="polite" className="font-mono text-[10px] uppercase tracking-widest text-breach mt-2 h-4">
                {autoSignedOut
                  ? "Auto signed out — too many attempts."
                  : attempts > 0
                    ? `Doesn't match. ${MAX_CONFIRM_ATTEMPTS - attempts} attempt${MAX_CONFIRM_ATTEMPTS - attempts === 1 ? "" : "s"} left.`
                    : ""}
              </div>
            </div>
          )}

          {!noEmail && (
            <p className="text-sm text-silver-dim leading-relaxed mb-8">
              For your safety we ask you to confirm before granting access to the protocol.
              If this isn&apos;t you, cancel and try again.
            </p>
          )}

          <button
            ref={confirmRef}
            type="button"
            onClick={handleConfirm}
            disabled={!!busy || autoSignedOut}
            aria-busy={busy === "confirm"}
            aria-label="Confirm identity and enter the app"
            className="btn-ember w-full bg-transparent text-silver py-4 rounded-lg font-mono text-xs uppercase tracking-widest font-bold border border-silver/40 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-3 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember focus-visible:ring-offset-2 focus-visible:ring-offset-obsidian"
          >
            {busy === "confirm" ? <Spinner /> : null}
            <span>{busy === "confirm" ? "Entering…" : "Confirm & Enter"}</span>
          </button>

          <button
            type="button"
            onClick={handleCancel}
            disabled={!!busy}
            aria-busy={busy === "cancel"}
            className="mt-4 w-full text-center font-mono text-[10px] uppercase tracking-widest text-muted-foreground hover:text-breach transition-colors disabled:opacity-50 focus-visible:outline-none focus-visible:text-breach inline-flex items-center justify-center gap-2"
          >
            {busy === "cancel" ? <Spinner className="text-muted-foreground" /> : null}
            <span>{busy === "cancel" ? "Signing out…" : "Not me — sign out"}</span>
          </button>

          {next && (
            <div className="mt-6 text-center font-mono text-[10px] uppercase tracking-widest text-muted-foreground/60">
              Will redirect to <span className="text-silver-dim">{next}</span>
            </div>
          )}
        </div>
      </main>
      <style>{`.auth-input{width:100%;background:rgba(255,255,255,.05);border:1px solid rgba(255,255,255,.1);border-radius:.5rem;padding:.875rem 1rem;color:#E2E2E2;font-size:.875rem;outline:none;transition:border-color .25s, box-shadow .25s, background .25s}.auth-input:focus{border-color:rgba(201,135,74,.6);box-shadow:0 0 0 4px rgba(201,135,74,.08);background:rgba(255,255,255,.07)}`}</style>
    </div>
  );
}

function ProviderError({
  id, msg, onRetry, btnRef, providerLabel,
}: {
  id: string;
  msg?: string;
  onRetry: () => void;
  btnRef: (el: HTMLButtonElement | null) => void;
  providerLabel: string;
}) {
  if (!msg) return null;
  return (
    <div
      id={id}
      role="alert"
      aria-live="assertive"
      className="flex items-start justify-between gap-3 rounded-lg border border-breach/40 bg-breach/5 px-3 py-2 text-[11px] text-breach animate-entrance"
    >
      <span className="leading-relaxed">{msg}</span>
      <button
        type="button"
        ref={btnRef}
        onClick={onRetry}
        aria-label={`Retry ${providerLabel}`}
        className="font-mono uppercase tracking-widest text-[10px] text-breach hover:text-silver transition-colors shrink-0 focus-visible:outline-none focus-visible:text-silver focus-visible:underline"
      >
        Retry →
      </button>
    </div>
  );
}

function Field({ label, htmlFor, children }: { label: string; htmlFor: string; children: React.ReactNode }) {
  return (
    <div>
      <label htmlFor={htmlFor} className="block font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground mb-2">
        {label}
      </label>
      {children}
    </div>
  );
}

function GoogleIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 48 48" aria-hidden="true"><path fill="#FFC107" d="M43.6 20.5h-1.9V20H24v8h11.3c-1.6 4.7-6.1 8-11.3 8-6.6 0-12-5.4-12-12s5.4-12 12-12c3 0 5.8 1.1 7.9 3l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.2-.1-2.3-.4-3.5z"/><path fill="#FF3D00" d="M6.3 14.7l6.6 4.8C14.7 16 19 13 24 13c3 0 5.8 1.1 7.9 3l5.7-5.7C34 6.1 29.3 4 24 4 16.1 4 9.3 8.5 6.3 14.7z"/><path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2c-2 1.5-4.5 2.4-7.2 2.4-5.2 0-9.6-3.3-11.3-8l-6.5 5C9.2 39.4 16 44 24 44z"/><path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.3-2.3 4.3-4.1 5.7l6.2 5.2C41 35.2 44 30 44 24c0-1.2-.1-2.3-.4-3.5z"/></svg>
  );
}
function AppleIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M16.365 1.43c0 1.14-.48 2.23-1.26 3.02-.84.84-2.22 1.49-3.36 1.4-.13-1.1.45-2.27 1.23-3.04.84-.82 2.27-1.45 3.39-1.38zM20.5 17.21c-.55 1.27-.81 1.84-1.52 2.96-.99 1.55-2.38 3.49-4.11 3.5-1.53.02-1.93-.99-4.01-.98-2.08.01-2.52 1-4.06.97-1.73-.02-3.05-1.76-4.04-3.31C.06 17.04-.22 12.51 1.61 10.1c1.3-1.71 3.36-2.71 5.3-2.71 1.97 0 3.22 1.08 4.85 1.08 1.58 0 2.54-1.08 4.83-1.08 1.73 0 3.56.94 4.87 2.57-4.28 2.34-3.59 8.46-.96 9.05z" />
    </svg>
  );
}
function Spinner({ className = "" }: { className?: string }) {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" className={`animate-spin ${className}`} aria-hidden="true">
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeOpacity="0.25" strokeWidth="3" />
      <path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
    </svg>
  );
}
function MailIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="3" y="5" width="18" height="14" rx="2" />
      <path d="M3 7l9 6 9-6" />
    </svg>
  );
}
