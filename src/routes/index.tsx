import { createFileRoute, useNavigate, Link } from "@tanstack/react-router";
import { useEffect, useRef, useState, lazy, Suspense } from "react";
import { useServerFn } from "@tanstack/react-start";
import { toast } from "sonner";
import { Nav } from "@/components/nav";
import { Logo } from "@/components/logo";
import { CodeInput } from "@/components/code-input";
import { useAuth } from "@/hooks/use-auth";
import { validateRoomCode } from "@/lib/room.functions";
import { track } from "@/lib/observability";
import { MatrixText } from "@/components/fx/matrix-text";
import { ShinyText } from "@/components/fx/shiny-text";
import { Particles } from "@/components/fx/particles";
import { Marquee } from "@/components/fx/marquee";
import { OrbitingCircles } from "@/components/fx/orbiting-circles";
import { NumberTicker } from "@/components/fx/number-ticker";
import { TextReveal } from "@/components/fx/text-reveal";
import { ScrubText } from "@/components/fx/scrub-text";
import { DepthLayers } from "@/components/fx/depth-layers";
import { PinnedHorizontal } from "@/components/fx/pinned-horizontal";
import { useBrandProse } from "@/components/ai-prose";
import { useParallax } from "@/hooks/use-parallax";
import { useInView } from "@/hooks/use-in-view";
import { MapSkeleton, MeteorSkeleton } from "@/components/fx/skeleton";


// Heavy FX lazy-loaded so they don't ship in the initial hero bundle and
// don't run their render loops until the section approaches the viewport.
const DottedMap = lazy(() =>
  import("@/components/fx/dotted-map").then((m) => ({ default: m.DottedMap })),
);
const Meteors = lazy(() =>
  import("@/components/fx/meteors").then((m) => ({ default: m.Meteors })),
);



function Spinner({ className = "" }: { className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={`inline-block size-3 rounded-full border-[1.5px] border-current border-r-transparent animate-spin align-[-2px] ${className}`}
    />
  );
}

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Stack'd — Presence is the new luxury" },
      { name: "description", content: "Stack your phones with friends, hold the silence, and earn back the time." },
      { property: "og:title", content: "Stack'd — Presence is the new luxury" },
      { property: "og:description", content: "Stack your phones with friends, hold the silence, and earn back the time." },
    ],
  }),
  component: Landing,
});

const STATS: Array<{ n: number; suffix: string; l: string; decimals?: number }> = [
  { n: 4.2, suffix: "h", l: "Avg. daily screen time, 18–34", decimals: 1 },
  { n: 144, suffix: "×", l: "Phone unlocks per person, per day" },
  { n: 23, suffix: "s", l: "Median focus after a notification" },
  { n: 0, suffix: "", l: "Notifications during a focus block" },
];

const VOICES = [
  { q: "We stacked at dinner. Nobody touched their phone for ninety minutes. I forgot what that felt like.", n: "Léa — Paris" },
  { q: "The shared timer is the unlock. It stops being willpower and starts being a game.", n: "Devon — Brooklyn" },
  { q: "My team uses it before every review. The room is sharper. The arguments are better.", n: "Priya — Bangalore" },
  { q: "First Sunday brunch in a year where I remember what my sister actually said.", n: "Mateo — Mexico City" },
  { q: "We stack before every writers' room. The pitches got weirder, in a good way.", n: "Anika — Lagos" },
  { q: "Ninety silent minutes with the founders. Closed the round the same week.", n: "Yusuf — Istanbul" },
  { q: "Kids saw us put ours down first. Now the phones live in a basket during dinner.", n: "Hannah — Berlin" },
  { q: "Studio session, four producers, one stack. Best take we've cut all year.", n: "Kenji — Tokyo" },
  { q: "I stopped calling it a detox. It's just how we hang out now.", n: "Rowan — Melbourne" },
  { q: "Board meeting ran an hour shorter. Nobody scrolled. Nobody drifted.", n: "Ingrid — Stockholm" },
  { q: "Chai on the terrace, four friends, phones stacked. Felt like college again.", n: "Aarav — Mumbai" },
  { q: "Our design crit finally had silence in it. The critiques got braver.", n: "Meera — Delhi" },
  { q: "Coded for two hours straight without a single Slack peek. Shipped the migration.", n: "Rohan — Hyderabad" },
  { q: "Sunday lunch with amma and appa. Nobody reached for a phone once.", n: "Divya — Chennai" },
  { q: "Startup standup, six founders, one stack. Sharpest thirty minutes of the week.", n: "Karthik — Bengaluru" },
  { q: "Wedding rehearsal dinner. We stacked. The toasts actually meant something.", n: "Ishaan — Jaipur" },
];

type CodeError = null | "invalid_format" | "not_found" | "closed" | "rate_limited" | "server_error" | "network";

const ERROR_COPY: Record<Exclude<CodeError, null>, { msg: string; retry: string; loading: string; canRetry: boolean }> = {
  invalid_format: { msg: "Invalid code — need 6 characters", retry: "Check the code and try again", loading: "Re-checking format…", canRetry: false },
  not_found: { msg: "No room with that key", retry: "Double-check with your host", loading: "Looking again…", canRetry: false },
  closed: { msg: "That session has already ended", retry: "Ask for a fresh code", loading: "Re-verifying status…", canRetry: false },
  rate_limited: { msg: "Slow down — too many attempts", retry: "Wait a moment, then retry", loading: "Waiting on the rate limit…", canRetry: true },
  server_error: { msg: "Server hiccup on our end", retry: "Tap retry", loading: "Trying the protocol again…", canRetry: true },
  network: { msg: "Couldn't reach the protocol", retry: "Check your connection, then retry", loading: "Reconnecting…", canRetry: true },
};

function Landing() {
  const { data: prose } = useBrandProse();
  const navigate = useNavigate();
  const { user } = useAuth();
  const validate = useServerFn(validateRoomCode);
  const [code, setCode] = useState("");
  const [error, setError] = useState<CodeError>(null);
  const [lastError, setLastError] = useState<CodeError>(null); // remembered while loading
  const [submitting, setSubmitting] = useState(false);
  const [starting, setStarting] = useState(false);
  const [cooldown, setCooldown] = useState(0); // seconds remaining for rate_limited
  const lastSubmitRef = useRef(0);
  const startCooldownRef = useRef(0);
  const retryBtnRef = useRef<HTMLButtonElement>(null);
  const codeBoxRef = useRef<HTMLDivElement>(null);
  const returnFocusRef = useRef(false);

  // Parallax refs — heavier travel now that sections are full-viewport.
  // Each layer moves at a different rate to build depth.
  const philosophyPx = useParallax<HTMLDivElement, HTMLDivElement>(80);
  const fieldPx = useParallax<HTMLDivElement, HTMLDivElement>(70);
  const voicesPx = useParallax<HTMLDivElement, HTMLDivElement>(60);
  const ctaPx = useParallax<HTMLDivElement, HTMLDivElement>(110);
  const orbitPx = useParallax<HTMLDivElement, HTMLDivElement>(90);
  const [mapSlotRef, mapInView] = useInView<HTMLDivElement>("400px 0px");
  const [meteorSlotRef, meteorInView] = useInView<HTMLDivElement>("400px 0px");


  // Auto-focus retry when an error first appears (unless retry was the trigger)
  useEffect(() => {
    if (error && !returnFocusRef.current && retryBtnRef.current) {
      retryBtnRef.current.focus();
    }
  }, [error]);

  // Return focus to code input after a retry-initiated request completes
  // (success OR failure) so keyboard users never get stuck on a stale Retry.
  useEffect(() => {
    if (!submitting && returnFocusRef.current) {
      returnFocusRef.current = false;
      // wait a tick so any error-driven re-render settles first
      window.requestAnimationFrame(() => {
        const el = codeBoxRef.current?.querySelector<HTMLInputElement>("input");
        el?.focus();
      });
    }
  }, [submitting]);

  // Countdown for rate_limited
  useEffect(() => {
    if (cooldown <= 0) return;
    const t = window.setTimeout(() => setCooldown((s) => Math.max(0, s - 1)), 1000);
    return () => window.clearTimeout(t);
  }, [cooldown]);

  // Which error codes are transient — safe to auto-retry with backoff.
  // rate_limited has its own cooldown UI, so we let the user re-trigger.
  const isTransient = (c: CodeError) => c === "server_error" || c === "network";
  const BACKOFF_MS = [500, 1200, 2400]; // attempts 2, 3, 4

  const join = async (e: React.FormEvent) => {
    e.preventDefault();
    if (submitting) return;
    if (cooldown > 0) return;
    const now = Date.now();
    if (now - lastSubmitRef.current < 1200) {
      setError("rate_limited");
      setLastError("rate_limited");
      return;
    }
    lastSubmitRef.current = now;

    const trimmed = code.trim().toUpperCase();
    if (trimmed.length !== 6) {
      setError("invalid_format");
      setLastError("invalid_format");
      return;
    }

    setSubmitting(true);
    setError(null);

    const attempt = async (n: number): Promise<void> => {
      let outcome: CodeError = null;
      try {
        const res = await validate({ data: { code: trimmed } });
        if (res.ok) {
          setLastError(null);
          toast.success(`Room ${trimmed} verified — entering.`);
          track("room.joined", { code: trimmed, status: res.status, cached: !!res.cached });
          if (user) navigate({ to: "/room/$code", params: { code: trimmed } });
          else navigate({ to: "/auth", search: { next: `/room/${trimmed}` } });
          return;
        }
        outcome = res.code as CodeError;
        if (res.code === "rate_limited" && typeof res.retryAfter === "number") {
          setCooldown(res.retryAfter);
        }
      } catch {
        outcome = "network";
      }

      // Retry on transient failures only.
      const maxAttempts = BACKOFF_MS.length + 1;
      if (outcome && isTransient(outcome) && n < maxAttempts) {
        const delay = BACKOFF_MS[n - 1];
        toast.error(
          `${ERROR_COPY[outcome].msg} — retrying (${n + 1}/${maxAttempts}) in ${Math.round(delay / 100) / 10}s`,
        );
        await new Promise((r) => window.setTimeout(r, delay));
        return attempt(n + 1);
      }

      // Final failure — surface inline banner + toast.
      if (outcome) {
        setError(outcome);
        setLastError(outcome);
        const finalMsg = isTransient(outcome)
          ? `${ERROR_COPY[outcome].msg} — gave up after ${maxAttempts} attempts`
          : ERROR_COPY[outcome as Exclude<CodeError, null>].msg;
        toast.error(finalMsg);
      }
    };

    try {
      await attempt(1);
    } finally {
      setSubmitting(false);
    }
  };



  const start = () => {
    if (submitting || starting) return;
    const now = Date.now();
    if (now - startCooldownRef.current < 1200) return;
    startCooldownRef.current = now;
    setStarting(true);
    if (user) navigate({ to: "/start" });
    else navigate({ to: "/auth", search: { next: "/start" } });
    window.setTimeout(() => setStarting(false), 1500);
  };

  const malformed = code.length > 0 && code.length < 6;
  const errInfo = error ? ERROR_COPY[error] : null;
  const loadingInfo = submitting && lastError ? ERROR_COPY[lastError] : null;
  const statusText =
    submitting ? (loadingInfo?.loading ?? "Verifying…")
      : errInfo ? errInfo.msg
      : malformed ? `${code.length} / 6`
      : code.length === 6 ? "Ready"
      : "6-char code";

  const retryJoin = () => {
    if (cooldown > 0 || submitting) return;
    setError(null);
    lastSubmitRef.current = 0;
    returnFocusRef.current = true;
    if (code.length === 6) {
      void join({ preventDefault: () => {} } as React.FormEvent);
    }
  };

  return (
    <div className="min-h-screen bg-obsidian text-silver overflow-x-hidden">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:top-3 focus:left-3 focus:z-[100] focus:px-4 focus:py-2 focus:rounded-md focus:bg-ember focus:text-obsidian focus:font-mono focus:text-xs focus:uppercase focus:tracking-widest focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember/70"
      >
        Skip to content
      </a>
      <Nav />

      <section id="main" tabIndex={-1} className="min-h-screen flex items-center pt-24 pb-20 px-6 relative focus:outline-none overflow-hidden">
        <DepthLayers />
        <div className="max-w-7xl mx-auto w-full relative">

        <Particles className="opacity-80" count={110} />
        <div className="grid lg:grid-cols-[1.2fr_0.8fr] gap-16 lg:gap-20 items-start relative">

          <div className="animate-entrance">
            <div className="inline-flex items-center gap-2 mb-8 px-3 py-1.5 rounded-full border border-white/10 bg-white/[0.02]">
              <span className="size-1.5 rounded-full bg-pulse animate-pulse" />
              <ShinyText className="font-mono text-[10px] tracking-[0.3em] uppercase">
                {prose.landingKicker}
              </ShinyText>
            </div>
            <h1 className="text-[clamp(3.4rem,13vw,8rem)] font-extrabold leading-[0.85] tracking-tighter mb-16 text-balance">
              {/* Mobile: Presence is / the new / luxury. */}
              <span className="sm:hidden">
                Presence is<br />
                <span className="text-muted-foreground">the new</span><br />
                <MatrixText text="luxury." className="text-ember" />
              </span>
              {/* Desktop/tablet: Presence is the / new luxury. */}
              <span className="hidden sm:inline">
                Presence is the<br />
                <span className="text-muted-foreground">new <MatrixText text="luxury." className="text-ember" /></span>
              </span>
            </h1>
            <form onSubmit={join} className="flex flex-col gap-4 max-w-2xl relative">
              <div className="flex items-center justify-between">
                <label className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground" htmlFor="room-code-status">
                  Join a Session
                </label>
                <span
                  id="room-code-status"
                  className={`font-mono text-[10px] uppercase tracking-widest transition-colors ${
                    error ? "text-breach" : malformed ? "text-ember/80" : "text-muted-foreground/60"
                  }`}
                  aria-live="polite"
                  aria-atomic="true"
                >
                  {statusText}
                </span>
              </div>
              <div ref={codeBoxRef}>
                <CodeInput
                  value={code}
                  onChange={(v) => {
                    setCode(v);
                    if (error) setError(null);
                  }}
                  invalid={!!error}
                />
              </div>
              {errInfo && (
                <div
                  role="alert"
                  aria-live="assertive"
                  aria-atomic="true"
                  className="flex items-center justify-between gap-3 rounded-md border border-breach/40 bg-breach/5 px-3 py-2 font-mono text-[10px] uppercase tracking-widest text-breach focus-within:ring-1 focus-within:ring-breach/60"
                >
                  <span id="room-code-error-msg">
                    {errInfo.retry}
                    {error === "rate_limited" && cooldown > 0 ? ` · ${cooldown}s` : ""}
                  </span>
                  {errInfo.canRetry && (
                    <button
                      ref={retryBtnRef}
                      type="button"
                      onClick={retryJoin}
                      disabled={submitting || (error === "rate_limited" && cooldown > 0)}
                      aria-label={`Retry — ${errInfo.msg}`}
                      aria-describedby="room-code-error-msg room-code-status"
                      className="inline-flex items-center gap-1.5 text-silver hover:text-ember focus-visible:text-ember focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ember/70 rounded px-1 transition-colors underline-offset-2 hover:underline disabled:opacity-60 disabled:cursor-not-allowed"
                    >
                      {submitting ? <Spinner /> : null}
                      <span>
                        {submitting
                          ? "Retrying…"
                          : error === "rate_limited" && cooldown > 0
                          ? `Wait ${cooldown}s`
                          : "Retry →"}
                      </span>
                    </button>
                  )}
                </div>
              )}
              <div className="flex flex-col sm:flex-row gap-3 mt-2">
                <button
                  type="submit"
                  disabled={code.length !== 6 || submitting || starting}
                  aria-busy={submitting}
                  aria-describedby="room-code-status"
                  className="btn-silver-sweep flex-1 px-10 py-4 rounded-lg font-mono text-xs uppercase tracking-widest font-bold disabled:opacity-70 disabled:cursor-not-allowed disabled:text-[var(--join-disabled-text)] disabled:bg-[var(--join-disabled-bg)] disabled:border-[var(--join-disabled-border)] inline-flex items-center justify-center gap-2 focus-visible:ring-2 focus-visible:ring-[var(--join-focus-ring)]"
                >
                  {submitting ? <Spinner /> : null}
                  <span>{submitting ? "Verifying…" : "Join"}</span>
                </button>
                <button
                  type="button"
                  onClick={start}
                  disabled={submitting || starting}
                  aria-busy={starting}
                  className="btn-ember flex-1 bg-transparent text-silver px-10 py-4 rounded-lg font-mono text-xs uppercase tracking-widest font-bold border border-silver/40 disabled:opacity-60 disabled:cursor-not-allowed inline-flex items-center justify-center gap-2"
                >
                  {starting ? <Spinner /> : null}
                  <span>{starting ? "Opening…" : "Start Session"}</span>
                </button>

              </div>
            </form>
          </div>



          <div className="animate-entrance [animation-delay:200ms]">
            <div className="w-full aspect-[4/5] bg-neutral-900 rounded-2xl outline outline-1 outline-white/5 flex flex-col justify-between p-8 relative overflow-hidden">
              <div className="font-mono text-[10px] tracking-widest text-muted-foreground">
                FIG_01 / THE_STACK
              </div>
              <div className="flex flex-col items-center justify-center flex-1 py-12">
                <div className="relative">
                  {[0, 1, 2, 3].map((i) => (
                    <div
                      key={i}
                      className="w-28 h-44 bg-gradient-to-b from-white/8 to-white/2 border border-white/10 rounded-xl absolute left-1/2"
                      style={{
                        transform: `translateX(-50%) translateY(${i * -8}px) rotate(${(i % 2 ? 1 : -1) * 1.2}deg)`,
                        top: `${i * 4}px`,
                      }}
                    />
                  ))}
                </div>
              </div>
              <div className="font-mono text-[10px] tracking-widest text-muted-foreground border-t border-white/10 pt-4 flex justify-between">
                <span>REF / OFFERING</span>
                <span className="text-pulse">● LIVE</span>
              </div>
            </div>
          </div>
        </div>
        </div>
      </section>


      {/* Philosophy teaser — full viewport, subtle parallax on inner content */}
      <section
        id="philosophy"
        ref={philosophyPx.ref}
        className="min-h-screen flex items-center py-24 border-t border-white/5 bg-neutral-900/30 scroll-mt-20 relative overflow-hidden"
      >
        <div
          ref={philosophyPx.targetRef}
          className="max-w-6xl mx-auto px-6 w-full will-change-transform"
        >
          <div className="flex items-end justify-between mb-12 gap-6 flex-wrap">
            <div>
              <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember mb-3">Philosophy</div>
              <h2 className="text-3xl sm:text-4xl font-extrabold tracking-tight">Three pillars. One protocol.</h2>
            </div>
            <Link to="/philosophy" className="font-mono text-[10px] uppercase tracking-widest text-silver-dim hover:text-ember transition-colors">
              Read the manifesto →
            </Link>
          </div>
          <div className="grid md:grid-cols-3 gap-12">
            {[
              { k: "01", h: "Kinetic Verdict", p: "Multi-axis tilt, accelerometer spikes, screen-wake loss. Any movement breaks the stack." },
              { k: "02", h: "Atomic Sync", p: "Realtime room state across every device. No host machine, no lag, no spectator mode." },
              { k: "03", h: "Earned Time", p: "Lifetime presence accumulates. Every minute disconnected is a minute reclaimed." },
            ].map((f) => (
              <div key={f.k} className="border-t border-white/10 pt-8">
                <div className="font-mono text-[10px] tracking-[0.3em] text-muted-foreground mb-6">{f.k}</div>
                <h3 className="text-2xl font-bold tracking-tight mb-3">{f.h}</h3>
                <p className="text-sm text-silver-dim leading-relaxed">{f.p}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section
        ref={fieldPx.ref}
        className="min-h-screen flex items-center py-24 px-6 border-t border-white/5 relative overflow-hidden"
      >
        <div
          ref={fieldPx.targetRef}
          className="max-w-6xl mx-auto w-full will-change-transform"
        >
          <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember mb-6">
            Field Data / What we&apos;re up against
          </div>
          <TextReveal as="p" className="max-w-2xl text-base sm:text-lg text-silver-dim leading-relaxed mb-10 text-balance">
            {prose.landingLede}
          </TextReveal>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-px bg-white/5 border border-white/5 rounded-2xl mb-16">
            {STATS.map((s) => (
              <button
                type="button"
                key={s.l}
                aria-label={`${s.l}: ${s.n}${s.suffix ?? ""}`}
                className="field-card group relative bg-obsidian p-8 flex flex-col gap-3 overflow-hidden isolate text-left transform-gpu will-change-transform transition-[background-color,transform,box-shadow] duration-[600ms] ease-[cubic-bezier(0.22,1,0.36,1)] hover:bg-[#1a0f08] hover:scale-[1.04] hover:z-10 hover:shadow-[0_20px_60px_-20px_rgba(240,169,104,0.35)] focus-visible:bg-[#1a0f08] focus-visible:scale-[1.04] focus-visible:z-10 focus-visible:shadow-[0_20px_60px_-20px_rgba(240,169,104,0.35)] active:scale-[1.04] active:bg-[#1a0f08] active:z-10 active:shadow-[0_20px_60px_-20px_rgba(240,169,104,0.35)]"
              >
                <span
                  aria-hidden
                  className="pointer-events-none absolute inset-0 -z-10 bg-gradient-to-br from-ember/25 via-ember/10 to-transparent opacity-0 transition-opacity duration-[900ms] ease-[cubic-bezier(0.22,1,0.36,1)] group-hover:opacity-100 group-focus-visible:opacity-100 group-active:opacity-100"
                />
                <span
                  aria-hidden
                  className="pointer-events-none absolute inset-x-0 bottom-0 h-px bg-ember/60 origin-left scale-x-0 transition-transform duration-[900ms] ease-[cubic-bezier(0.22,1,0.36,1)] group-hover:scale-x-100 group-focus-visible:scale-x-100 group-active:scale-x-100"
                />
                <div className="text-4xl sm:text-5xl font-extrabold tracking-tighter text-ember transition-[text-shadow] duration-[900ms] ease-[cubic-bezier(0.22,1,0.36,1)] group-hover:[text-shadow:0_0_24px_rgba(240,169,104,0.45)] group-focus-visible:[text-shadow:0_0_24px_rgba(240,169,104,0.45)] group-active:[text-shadow:0_0_24px_rgba(240,169,104,0.45)]">
                  <NumberTicker value={s.n} suffix={s.suffix} decimals={s.decimals ?? 0} />
                </div>
                <div className="font-mono text-[10px] tracking-widest uppercase text-muted-foreground leading-relaxed transition-colors duration-[900ms] ease-[cubic-bezier(0.22,1,0.36,1)] group-hover:text-silver-dim group-focus-visible:text-silver-dim group-active:text-silver-dim">{s.l}</div>
              </button>
            ))}
          </div>

          {/* Dotted map — silent rooms on quiet nodes across the world */}
          <div className="grid md:grid-cols-[1.1fr_1fr] gap-10 items-center border-t border-white/5 pt-14">
            <div>
              <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground mb-4">Live Nodes</div>
              <h3 className="text-2xl sm:text-3xl font-bold tracking-tight mb-4">
                Rooms are holding <span className="text-ember">everywhere</span>.
              </h3>
              <TextReveal as="p" className="text-sm text-silver-dim leading-relaxed max-w-md">
                Silence isn&apos;t local. Every dot is a table somewhere on the map choosing presence over noise — anonymously, atomically, in sync.
              </TextReveal>
            </div>
            <div ref={mapSlotRef} data-visual="map" className="w-full aspect-[76/34]">
              {mapInView ? (
                <Suspense fallback={<MapSkeleton />}>
                  <DottedMap className="opacity-90" />
                </Suspense>
              ) : (
                <MapSkeleton />
              )}
            </div>
          </div>
        </div>
      </section>

      {/* Orbits — narrative around focus and time */}
      <section
        ref={orbitPx.ref}
        className="min-h-screen flex items-center py-20 sm:py-24 px-6 border-t border-white/5 relative overflow-hidden bg-black"
      >
        <DepthLayers />
        <div
          ref={orbitPx.targetRef}
          className="max-w-6xl mx-auto w-full grid lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)] gap-14 lg:gap-20 items-center relative will-change-transform"
        >
          <div>

            <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember mb-6">Orbit / Hold</div>
            <ScrubText as="h2" className="text-[clamp(2rem,7vw,3.25rem)] font-extrabold tracking-tighter mb-8 leading-[0.95] block">
              Time returns to those who hold the silence.
            </ScrubText>

            <TextReveal as="p" className="text-base text-silver-dim leading-relaxed max-w-md mb-6" delay={200}>
              Each session is a small orbit. Minutes fall into hours. Hours fall into weeks of attention you thought you&apos;d already lost.
            </TextReveal>
            <TextReveal as="p" className="text-base text-silver-dim leading-relaxed max-w-md" delay={400}>
              Stay in the ring. Watch it come back.
            </TextReveal>
          </div>
          <div data-visual="orbit" className="flex min-w-0 items-center justify-center w-full overflow-visible">
            <OrbitingCircles
              size="min(100%, 560px)"
              labelGutter="clamp(30px, 6vw, 42px)"
              className="max-w-full"
              center={
                <div className="size-[clamp(64px,16vw,96px)] rounded-full border border-ember/40 bg-obsidian flex items-center justify-center">
                  <span className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Now</span>
                </div>
              }
              orbits={[
                {
                  radius: 0.42,
                  duration: 22,
                  items: ["01s", "10s", "60s"].map((t) => (
                    <span
                      key={t}
                      className="inline-flex size-[clamp(30px,7.5vw,44px)] items-center justify-center rounded-full border border-silver/25 bg-obsidian font-mono text-[9px] sm:text-[10px] tracking-widest text-silver"
                    >
                      {t}
                    </span>
                  )),
                },
                {
                  radius: 0.7,
                  duration: 38,
                  reverse: true,
                  items: ["Minute", "Hour", "Day", "Week", "Month"].map((t) => (
                    <span
                      key={t}
                      className="inline-flex px-[clamp(5px,1.6vw,10px)] h-[clamp(22px,5.5vw,30px)] items-center justify-center rounded-full border border-ember/25 bg-obsidian/60 font-mono text-[8.5px] sm:text-[10px] uppercase tracking-widest text-ember whitespace-nowrap"
                    >
                      {t}
                    </span>
                  )),
                },
                {
                  radius: 1,
                  duration: 60,
                  items: ["Focus", "Breath", "Table", "Signal"].map((t) => (
                    <span
                      key={t}
                      className="inline-flex px-[clamp(6px,1.8vw,12px)] h-[clamp(24px,6vw,34px)] items-center justify-center rounded-full border border-white/15 bg-obsidian/80 font-mono text-[9px] sm:text-[10px] uppercase tracking-widest text-silver-dim whitespace-nowrap"
                    >
                      {t}
                    </span>
                  )),
                },

              ]}
            />
          </div>
        </div>
      </section>

      {/* Pinned horizontal chapters — signature scroll moment */}
      <section className="relative border-t border-white/5 bg-obsidian overflow-hidden">
        <DepthLayers ember={false} />
        <div className="max-w-6xl mx-auto px-6 pt-24 pb-8 relative">
          <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember mb-4">Chapters / Held Time</div>
          <ScrubText
            as="h2"
            className="text-[clamp(2rem,6vw,3.5rem)] font-extrabold tracking-tighter leading-[0.95] max-w-3xl block"
          >
            Scroll sideways through what a stacked hour becomes.
          </ScrubText>
        </div>
        <PinnedHorizontal
          trackClassName="gap-6 pl-6 pr-[30vw] items-stretch"
        >
          {[
            { k: "I", h: "The Stack", p: "Four phones face-down, screens dark. The table exhales.", tone: "silver" },
            { k: "II", h: "The Hold", p: "Sixty minutes locked. Every accelerometer twitch reported to the room.", tone: "ember" },
            { k: "III", h: "The Silence", p: "The pause where a real sentence can start. Nobody drifts. Nobody scrolls.", tone: "silver" },
            { k: "IV", h: "The Verdict", p: "Kinetic breach, atomic sync, earned time. The math doesn't flinch.", tone: "ember" },
            { k: "V", h: "The Ledger", p: "Minutes fall into hours. Hours fall into weeks you thought you'd lost.", tone: "silver" },
            { k: "VI", h: "The Return", p: "The room stands. Phones come back. Something in the air stayed different.", tone: "ember" },
          ].map((c) => (
            <article
              key={c.k}
              className={`w-[78vw] sm:w-[62vw] md:w-[46vw] lg:w-[36vw] xl:w-[30vw] shrink-0 h-[56vh] rounded-3xl border p-10 flex flex-col justify-between relative overflow-hidden ${
                c.tone === "ember"
                  ? "border-ember/25 bg-gradient-to-br from-[#1a0f08] via-obsidian to-obsidian"
                  : "border-white/10 bg-gradient-to-br from-neutral-900 via-obsidian to-obsidian"
              }`}
            >
              <div
                aria-hidden
                className="absolute -top-24 -right-24 size-64 rounded-full blur-3xl opacity-40"
                style={{
                  background:
                    c.tone === "ember"
                      ? "radial-gradient(circle,#F0A968 0%, transparent 70%)"
                      : "radial-gradient(circle,#E2E2E2 0%, transparent 70%)",
                }}
              />
              <div className="relative">
                <div
                  className={`font-mono text-[10px] tracking-[0.4em] uppercase mb-6 ${
                    c.tone === "ember" ? "text-ember" : "text-silver-dim"
                  }`}
                >
                  Chapter {c.k}
                </div>
                <h3
                  className={`text-[clamp(2rem,4vw,3rem)] font-extrabold tracking-tighter leading-[0.95] mb-6 ${
                    c.tone === "ember" ? "text-ember" : "text-silver"
                  }`}
                >
                  {c.h}
                </h3>
                <p className="text-base sm:text-lg text-silver-dim leading-relaxed max-w-md text-balance">
                  {c.p}
                </p>
              </div>
              <div className="relative flex items-end justify-between font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground">
                <span>Held / Protocol.01</span>
                <span className={c.tone === "ember" ? "text-ember" : "text-silver-dim"}>●</span>
              </div>
            </article>
          ))}
        </PinnedHorizontal>
      </section>




      <section
        ref={voicesPx.ref}
        className="min-h-screen flex flex-col justify-center py-24 border-t border-white/5 bg-neutral-900/30 relative overflow-hidden"
      >
        <div
          ref={voicesPx.targetRef}
          className="w-full will-change-transform"
        >
          <div className="max-w-6xl mx-auto px-6 mb-10 flex items-end justify-between gap-6 flex-wrap">
            <div>
              <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground mb-3">Field Reports</div>
              <TextReveal as="h2" className="text-3xl sm:text-4xl font-extrabold tracking-tight max-w-xl text-balance">
                The room speaks for itself.
              </TextReveal>
            </div>
            <span className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Voices / Ongoing</span>
          </div>

          <Marquee speedSeconds={55}>
            {[...VOICES, ...VOICES].map((v, i) => (
              <figure
                key={`${v.n}-${i}`}
                className="w-[380px] shrink-0 border border-white/10 rounded-2xl p-8 bg-obsidian flex flex-col justify-between gap-8 hover:border-ember/40 transition-colors"
              >
                <blockquote className="text-lg font-medium tracking-tight leading-snug text-balance">
                  &ldquo;{v.q}&rdquo;
                </blockquote>
                <figcaption className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">{v.n}</figcaption>
              </figure>
            ))}
          </Marquee>

          <div className="mt-6">
            <Marquee speedSeconds={70} reverse>
              {[...VOICES].reverse().concat(VOICES).map((v, i) => (
                <figure
                  key={`r-${v.n}-${i}`}
                  className="w-[340px] shrink-0 border border-white/10 rounded-2xl p-6 bg-obsidian/60 flex flex-col justify-between gap-6"
                >
                  <blockquote className="text-sm text-silver-dim leading-relaxed">&ldquo;{v.q}&rdquo;</blockquote>
                  <figcaption className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">{v.n}</figcaption>
                </figure>
              ))}
            </Marquee>
          </div>
        </div>
      </section>


      <section
        ref={ctaPx.ref}
        className="min-h-screen flex items-center py-32 px-6 border-t border-white/5 relative overflow-hidden"
      >
        <DepthLayers />
        <div ref={meteorSlotRef} aria-hidden className="absolute inset-0 pointer-events-none">
          {meteorInView ? (
            <Suspense fallback={<MeteorSkeleton />}>
              <Meteors count={24} />
            </Suspense>
          ) : (
            <MeteorSkeleton />
          )}
        </div>
        <div
          ref={ctaPx.targetRef}
          className="max-w-4xl mx-auto text-center w-full will-change-transform relative"
        >
          <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember mb-6">Begin / Protocol.01</div>
          <h2 className="text-[clamp(2.5rem,9vw,4.5rem)] font-extrabold tracking-tighter mb-10 text-balance leading-[0.9]">
            <ScrubText as="span" className="block">Put the phone down.</ScrubText>
            <span className="text-muted-foreground block">
              <ScrubText as="span">Pick the </ScrubText>
              <ScrubText as="span" className="text-ember">room</ScrubText>
              <ScrubText as="span"> up.</ScrubText>
            </span>
          </h2>
          <button onClick={start} className="btn-ember inline-block px-12 py-5 border border-silver/40 rounded-full font-mono text-xs uppercase tracking-widest text-silver">
            Activate Focus Protocol
          </button>
        </div>
      </section>



      <footer className="py-16 px-6 border-t border-white/5">
        <div className="max-w-7xl mx-auto grid md:grid-cols-4 gap-12">
          <div className="md:col-span-2 max-w-sm">
            <div className="flex items-center gap-3 mb-6">
              <Logo className="size-8" />
              <span className="font-mono text-xs tracking-[0.3em] uppercase">Stack&apos;d</span>
            </div>
            <p className="text-sm text-silver-dim leading-relaxed mb-6">
              Designed for those who value the space between notifications. A private protocol for shared, intentional offline time.
            </p>
            <p className="font-mono text-[10px] uppercase tracking-widest text-muted-foreground">
              © {new Date().getFullYear()} Stack&apos;d Protocol
            </p>
          </div>
          <div>
            <div className="font-mono text-[10px] uppercase tracking-widest text-muted-foreground mb-4">Protocol</div>
            <ul className="space-y-3 text-sm">
              <li><Link to="/philosophy" className="hover:text-ember transition-colors">Philosophy</Link></li>
              <li><Link to="/auth" className="hover:text-ember transition-colors">Enter</Link></li>
              <li><a href="#philosophy" className="hover:text-ember transition-colors">Pillars</a></li>
            </ul>
          </div>
          <div>
            <div className="font-mono text-[10px] uppercase tracking-widest text-muted-foreground mb-4">Signal</div>
            <ul className="space-y-3 text-sm">
              <li><a href="mailto:hello@stackd.app" className="hover:text-ember transition-colors">Press</a></li>
              <li><a href="mailto:hello@stackd.app" className="hover:text-ember transition-colors">Contact</a></li>
              <li><a href="#" className="hover:text-ember transition-colors">Privacy</a></li>
            </ul>
          </div>
        </div>
        <div className="max-w-7xl mx-auto mt-16 pt-8 border-t border-white/5 text-[10px] font-mono text-muted-foreground/40 uppercase tracking-[0.5em] text-center">
          Non-digital space is a human right.
        </div>
      </footer>
    </div>
  );
}
