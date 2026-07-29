import { createFileRoute, useNavigate, Link } from "@tanstack/react-router";
import { useEffect, useRef, useState, lazy, Suspense } from "react";
import { useServerFn } from "@tanstack/react-start";
import { toast } from "sonner";
import { Nav } from "@/components/nav";
import { Logo } from "@/components/logo";
import { CodeInput } from "@/components/code-input";
import { useAuth } from "@/hooks/use-auth";
import { validateRoomCode } from "@/lib/room.functions";
import { ERROR_COPY, type CodeError } from "@/lib/room-code";
import { track } from "@/lib/observability";
import { MatrixText } from "@/components/fx/matrix-text";
import { ShinyText } from "@/components/fx/shiny-text";
import { Particles } from "@/components/fx/particles";
import { Marquee } from "@/components/fx/marquee";
import { NumberTicker } from "@/components/fx/number-ticker";
import { TextReveal } from "@/components/fx/text-reveal";
import { ScrubText } from "@/components/fx/scrub-text";
import { DepthLayers } from "@/components/fx/depth-layers";
import { LightRays } from "@/components/fx/light-rays";
import { PinnedHorizontal } from "@/components/fx/pinned-horizontal";
import { Scene, SceneTitle, SceneLede } from "@/components/landing/scene";
import { RoomPreview } from "@/components/landing/room-preview";
import { HeroStage } from "@/components/landing/hero-stage";
import {
  BreachToast,
  XpChip,
  ReactionRail,
  LeaderboardPanel,
  SessionCompleteCard,
} from "@/components/landing/product-panels";

import { StreakCard, XpCard, AchievementCard } from "@/components/landing/reward-cards";
import { useBrandProse } from "@/components/ai-prose";
import { useParallax } from "@/hooks/use-parallax";
import { useInView } from "@/hooks/use-in-view";
import { MapSkeleton, MeteorSkeleton } from "@/components/fx/skeleton";

// Heavy FX lazy-loaded so they don't ship in the initial hero bundle and
// don't run their render loops until the section approaches the viewport.
const DottedMap = lazy(() =>
  import("@/components/fx/dotted-map").then((m) => ({ default: m.DottedMap })),
);
const Meteors = lazy(() => import("@/components/fx/meteors").then((m) => ({ default: m.Meteors })));

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
      {
        name: "description",
        content:
          "Stack'd isn't another focus timer — it's a shared commitment to being present. Stack your phones with friends and hold the silence together.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
      { property: "og:title", content: "Stack'd — Presence is the new luxury" },
      {
        property: "og:description",
        content:
          "A real-time, multiplayer phone-stacking room. Every tilt, lift and screen wake is shared instantly.",
      },
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

const SIGNALS = [
  { k: "Tilt", d: "Multi-axis accelerometer drift, reported the instant it happens." },
  { k: "Lift", d: "The phone leaves the stack. The room knows before you sit back down." },
  { k: "Wake", d: "Screen-on events break the hold — no quiet exits, no private cheats." },
];

const VOICES = [
  {
    q: "We stacked at dinner. Nobody touched their phone for ninety minutes. I forgot what that felt like.",
    n: "Léa — Paris",
  },
  {
    q: "The shared timer is the unlock. It stops being willpower and starts being a game.",
    n: "Devon — Brooklyn",
  },
  {
    q: "My team uses it before every review. The room is sharper. The arguments are better.",
    n: "Priya — Bangalore",
  },
  {
    q: "First Sunday brunch in a year where I remember what my sister actually said.",
    n: "Mateo — Mexico City",
  },
  {
    q: "Chai on the terrace, four friends, phones stacked. Felt like college again.",
    n: "Aarav — Mumbai",
  },
  { q: "Our design crit finally had silence in it. The critiques got braver.", n: "Meera — Delhi" },
  {
    q: "Coded for two hours straight without a single Slack peek. Shipped the migration.",
    n: "Rohan — Hyderabad",
  },
  { q: "Sunday lunch with amma and appa. Nobody reached for a phone once.", n: "Divya — Chennai" },
  {
    q: "Board meeting ran an hour shorter. Nobody scrolled. Nobody drifted.",
    n: "Ingrid — Stockholm",
  },
  { q: "I stopped calling it a detox. It's just how we hang out now.", n: "Rowan — Melbourne" },
];

// Error codes + user-facing copy live in `@/lib/room-code` so the server
// function and this UI can never drift apart (and so both are unit-testable).

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

  // Parallax refs — each scene drifts at its own rate to build depth.
  const heroPx = useParallax<HTMLDivElement, HTMLDivElement>(50);
  const insightPx = useParallax<HTMLDivElement, HTMLDivElement>(80);
  const solutionPx = useParallax<HTMLDivElement, HTMLDivElement>(70);
  const philosophyPx = useParallax<HTMLDivElement, HTMLDivElement>(90);
  const ctaPx = useParallax<HTMLDivElement, HTMLDivElement>(110);
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
  const statusText = submitting
    ? (loadingInfo?.loading ?? "Verifying…")
    : errInfo
      ? errInfo.msg
      : malformed
        ? `${code.length} / 6`
        : code.length === 6
          ? "Ready"
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

  // The room-entry console is the single interactive surface of the story —
  // rendered once, in the closing scene, so the CTA is the app itself.
  const console_ = (
    <form onSubmit={join} className="relative mx-auto flex w-full max-w-2xl flex-col gap-4">
      <div className="flex items-center justify-between">
        <label
          className="font-mono text-[10px] uppercase tracking-[0.3em] text-muted-foreground"
          htmlFor="room-code-status"
        >
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
              className="inline-flex items-center gap-1.5 rounded px-1 text-silver underline-offset-2 transition-colors hover:text-ember hover:underline focus-visible:text-ember focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ember/70 disabled:cursor-not-allowed disabled:opacity-60"
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
      <div className="mt-3 flex flex-col gap-3 sm:mt-2 sm:flex-row">
        <button
          type="button"
          onClick={start}
          disabled={submitting || starting}
          aria-busy={starting}
          className="btn-silver-sweep inline-flex min-h-[56px] flex-1 items-center justify-center gap-2 rounded-xl px-10 text-[0.8rem] font-mono text-xs font-bold uppercase tracking-widest focus-visible:ring-2 focus-visible:ring-[var(--join-focus-ring)] disabled:cursor-not-allowed disabled:border-[var(--join-disabled-border)] disabled:bg-[var(--join-disabled-bg)] disabled:text-[var(--join-disabled-text)] disabled:opacity-70"
        >
          {starting ? <Spinner /> : null}
          <span>{starting ? "Opening…" : "Stack Your First Room"}</span>
        </button>
        <button
          type="submit"
          disabled={code.length !== 6 || submitting || starting}
          aria-busy={submitting}
          aria-describedby="room-code-status"
          className="btn-ember inline-flex min-h-[56px] flex-1 items-center justify-center gap-2 rounded-xl border border-silver/40 bg-transparent px-10 font-mono text-xs font-bold uppercase tracking-widest text-silver disabled:cursor-not-allowed disabled:opacity-60"
        >
          {submitting ? <Spinner /> : null}
          <span>{submitting ? "Verifying…" : "Join with a code"}</span>
        </button>
      </div>
    </form>
  );

  return (
    <div className="min-h-screen overflow-x-hidden bg-obsidian text-silver">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:left-3 focus:top-3 focus:z-[100] focus:rounded-md focus:bg-ember focus:px-4 focus:py-2 focus:font-mono focus:text-xs focus:uppercase focus:tracking-widest focus:text-obsidian focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember/70"
      >
        Skip to content
      </a>
      <Nav />

      <main>
        {/* ═══ 01 · HOOK ═══════════════════════════════════════════ */}
        <Scene
          id="main"
          index="01"
          label="The Crisis"
          className="border-t-0 pb-20 pt-32 lg:pb-20 lg:pt-32"
          glow="top-right"
          handoff={false}
          background={
            <>
              <DepthLayers />
              <LightRays className="opacity-60" />
              <Particles className="opacity-80" count={110} />
            </>
          }
        >
          <div ref={heroPx.ref}>
            <div
              ref={heroPx.targetRef}
              className="flex flex-col gap-6 will-change-transform sm:gap-8 lg:grid lg:grid-cols-[1.15fr_0.85fr] lg:items-center lg:gap-20"
            >
              {/*
                Reading order: on handhelds and tablets the narrative comes
                first (headline → lede → CTA), and the interactive product
                stage follows. Read → understand → interact. On desktop the
                two columns sit side by side, copy left, product right.
              */}
              <div className="order-2 animate-entrance [animation-delay:120ms] lg:order-2">
                <HeroStage />
                <p className="sr-only">
                  Product preview: four phones stacked face-down on a table, a live room timer at
                  forty-two minutes with four members, a break-detection alert as one member lifts
                  their phone, an experience gain of four hundred and twenty points, and a weekly
                  leaderboard.
                </p>
              </div>

              <div className="order-1 animate-entrance lg:order-1">
                <div className="mb-4 inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/[0.02] px-3 py-1.5 sm:mb-6">
                  <span className="size-[var(--dot-size-sm)] animate-pulse rounded-full bg-pulse" />
                  <ShinyText className="font-mono text-[10px] uppercase tracking-[0.3em]">
                    {prose.landingKicker}
                  </ShinyText>
                </div>

                <SceneTitle as="h1" className="mb-5 sm:mb-6">
                  Every notification
                  <br />
                  <span className="text-muted-foreground">steals a little of your</span>
                  <br />
                  <MatrixText text="attention." className="text-ember" />
                </SceneTitle>

                <SceneLede className="mb-6 sm:mb-8">
                  It never arrives as a crisis. It arrives twenty-three seconds at a time, until an
                  evening with people you love is something you half-remember.
                </SceneLede>

                <div className="flex flex-wrap items-center gap-4">
                  <a
                    href="#begin"
                    className="btn-ember inline-flex min-h-[54px] w-full items-center justify-center gap-2 rounded-full border border-silver/40 px-10 font-mono text-xs uppercase tracking-widest text-silver sm:w-auto"
                  >
                    Read the story ↓
                  </a>
                  <span className="font-mono text-[10px] uppercase tracking-[0.3em] text-muted-foreground">
                    Six scenes · Two minutes
                  </span>
                </div>
              </div>
            </div>

            {/*
              Field data strip — the crisis, in numbers. Two columns on
              handhelds so the whole exhibit reads inside one viewport
              instead of stretching the hook across four extra screens.
            */}
            <div className="mt-14 grid grid-cols-2 gap-px overflow-hidden rounded-3xl border border-white/5 bg-white/5 sm:mt-12 md:grid-cols-4">
              {STATS.map((s, i) => (
                <div
                  key={s.l}
                  className={`field-card group relative isolate flex transform-gpu flex-col gap-2 overflow-hidden p-6 transition-[background-color,transform,box-shadow] duration-[600ms] ease-[cubic-bezier(0.22,1,0.36,1)] will-change-transform hover:z-10 hover:bg-[#1a0f08] hover:shadow-[0_20px_60px_-20px_rgba(240,169,104,0.35)] sm:gap-3 sm:p-6 ${
                    i % 2 ? "bg-white/[0.015]" : "bg-obsidian"
                  }`}
                >
                  <span
                    aria-hidden
                    className="pointer-events-none absolute inset-0 -z-10 bg-gradient-to-br from-ember/25 via-ember/10 to-transparent opacity-0 transition-opacity duration-[900ms] ease-[cubic-bezier(0.22,1,0.36,1)] group-hover:opacity-100"
                  />
                  <div className="text-3xl font-extrabold tracking-tighter text-ember sm:text-4xl">
                    <NumberTicker value={s.n} suffix={s.suffix} decimals={s.decimals ?? 0} />
                  </div>
                  <div className="font-mono text-[9px] uppercase leading-relaxed tracking-widest text-muted-foreground sm:text-[10px]">
                    {s.l}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </Scene>

        {/* ═══ 02 · INSIGHT ════════════════════════════════════════ */}
        <Scene
          id="begin"
          index="02"
          label="The Insight"
          tone="raised"
          glow="top-left"
          background={<DepthLayers ember={false} />}
        >
          <div ref={insightPx.ref}>
            <div ref={insightPx.targetRef} className="will-change-transform">
              <ScrubText
                as="h2"
                className="mb-6 block max-w-4xl text-balance text-[clamp(2.6rem,7.5vw,4rem)] font-extrabold leading-[0.9] tracking-tighter sm:mb-8"
              >
                Focus was never a willpower problem.
              </ScrubText>

              <div className="grid gap-10 md:grid-cols-[1fr_1fr] md:gap-16">
                <TextReveal as="div">
                  <SceneLede>
                    You already know what you should be doing. You have known all along. What you
                    have never had is someone in the room who notices the moment you drift.
                  </SceneLede>
                </TextReveal>

                {/*
                  Alone vs Together, staged as two overlapping product frames
                  with live chrome floating off their edges — depth instead of
                  a second stack of centered text blocks.
                */}
                <div className="relative isolate pr-3 sm:pb-8 [perspective:1200px]">
                  <div className="glass relative z-10 rotate-[-1.2deg] rounded-3xl p-7 transition-transform duration-[700ms] ease-[cubic-bezier(0.22,1,0.36,1)] hover:[transform:rotateY(-4deg)_translateZ(12px)] sm:p-8">
                    <div className="mb-3 font-mono text-[10px] uppercase tracking-[0.3em] text-muted-foreground">
                      Alone
                    </div>
                    <p className="text-lg font-medium tracking-tight text-silver-dim">
                      A timer you can silence. A promise nobody hears you break.
                    </p>
                    <span
                      aria-hidden
                      className="mt-5 block h-1 w-full overflow-hidden rounded-full bg-white/[0.06]"
                    >
                      <span className="block h-full w-[18%] rounded-full bg-white/20" />
                    </span>
                  </div>

                  <div className="glass relative z-20 -mt-3 ml-4 rotate-[0.9deg] rounded-3xl border-ember/25 p-7 shadow-[0_40px_90px_-45px_rgba(240,169,104,0.5)] transition-transform duration-[700ms] ease-[cubic-bezier(0.22,1,0.36,1)] hover:[transform:rotateY(4deg)_translateZ(12px)] sm:p-8">
                    <div className="mb-3 font-mono text-[10px] uppercase tracking-[0.3em] text-ember">
                      Together
                    </div>
                    <p className="text-lg font-medium tracking-tight text-silver">
                      Four people watching the same clock. Suddenly it holds.
                    </p>
                    <span
                      aria-hidden
                      className="mt-5 block h-1 w-full overflow-hidden rounded-full bg-white/[0.06]"
                    >
                      <span className="block h-full w-[94%] rounded-full bg-gradient-to-r from-ember to-ember-glow" />
                    </span>
                  </div>

                  {/*
                    Below `sm`, the two glass cards already fill the width, so
                    the floating toasts move into normal flow (side by side,
                    below the stack) instead of absolutely overlapping content
                    they no longer have room to clear.
                  */}
                  <div className="mt-5 flex gap-3 sm:hidden">
                    <BreachToast className="w-1/2 scale-90 origin-left" />
                    <XpChip className="w-1/2 scale-90 origin-right" />
                  </div>
                  <BreachToast className="absolute -left-8 top-[38%] z-30 hidden w-[188px] sm:block" />
                  <XpChip className="absolute -bottom-2 -right-6 z-30 hidden w-[170px] sm:block" />
                  <p className="sr-only">
                    Product preview: a solo session barely holding, a shared room holding at
                    ninety-four percent, a break-detection alert and an experience gain.
                  </p>
                </div>
              </div>

              <p className="mt-10 max-w-3xl text-balance sm:mt-10 text-[clamp(1.25rem,3.2vw,2rem)] font-bold leading-tight tracking-tight">
                It is an <span className="text-ember">accountability</span> problem.
              </p>
            </div>
          </div>
        </Scene>

        {/* ═══ 03 · SOLUTION ═══════════════════════════════════════ */}
        <Scene
          index="03"
          label="The Protocol"
          tone="void"
          glow="bottom-right"
          background={
            <>
              <DepthLayers />
              <LightRays className="opacity-40" />
            </>
          }
        >
          <div ref={solutionPx.ref}>
            <div
              ref={solutionPx.targetRef}
              className="flex flex-col gap-12 will-change-transform sm:gap-14 lg:grid lg:grid-cols-[0.95fr_1.05fr] lg:items-center lg:gap-16"
            >
              <div className="order-2 lg:order-1">
                <SceneTitle className="mb-6 sm:mb-7">
                  Phones down.
                  <br />
                  <span className="text-muted-foreground">Room </span>
                  <span className="text-ember">live.</span>
                </SceneTitle>
                <SceneLede className="mb-8 sm:mb-8">
                  Stack&apos;d is a real-time multiplayer room. Every tilt, lift and screen wake is
                  detected on-device and broadcast to everyone at the table the instant it happens.
                </SceneLede>

                <ul className="space-y-px overflow-hidden rounded-3xl border border-white/5 bg-white/5">
                  {SIGNALS.map((s) => (
                    <li key={s.k} className="bg-obsidian p-6 sm:p-5">
                      <div className="mb-1.5 flex items-center gap-3">
                        <span className="size-[var(--dot-size-sm)] rounded-full bg-ember shadow-[var(--dot-glow)]" />
                        <span className="font-mono text-[10px] uppercase tracking-[0.3em] text-ember">
                          {s.k}
                        </span>
                      </div>
                      <p className="pl-[calc(var(--dot-size-sm)+0.75rem)] text-[0.95rem] leading-[1.6] text-silver-dim">
                        {s.d}
                      </p>
                    </li>
                  ))}
                </ul>
              </div>

              <div className="relative order-1 -mx-4 mt-8 sm:mx-0 sm:mt-0 lg:order-2">
                {/*
                  Echo frames — two duplicated room shells behind the live one,
                  so the protocol reads as "many tables at once" rather than a
                  single flat screenshot.
                */}
                <span
                  aria-hidden
                  className="pointer-events-none absolute inset-x-6 -top-4 z-0 h-24 rotate-[-1.4deg] rounded-3xl border border-white/8 bg-white/[0.015]"
                />
                <span
                  aria-hidden
                  className="pointer-events-none absolute inset-x-10 -top-8 z-0 h-24 rotate-[1.2deg] rounded-3xl border border-white/5 bg-white/[0.01]"
                />
                <div className="relative z-10">
                  <RoomPreview />
                </div>
                {/* Overlapping product frames — the room, seen from outside it. */}
                <BreachToast className="absolute -right-1 -top-16 z-20 w-[168px] scale-90 origin-top-right sm:-right-8 sm:-top-10 sm:w-[210px] sm:scale-100 lg:-right-12" />
                <ReactionRail className="absolute -left-2 bottom-10 z-20 sm:-left-8" />
                <XpChip className="absolute -bottom-8 right-4 z-20 w-[150px] scale-90 origin-bottom-right sm:-bottom-10 sm:right-10 sm:w-[180px] sm:scale-100" />

                <p className="sr-only">
                  Product preview: a live Stack&apos;d room with a sixty-minute timer, four
                  participants — three holding, one lifted — and real-time tilt, lift and screen
                  wake detection.
                </p>
              </div>
            </div>
          </div>
        </Scene>

        {/* ═══ 04 · REWARD ═════════════════════════════════════════ */}
        <Scene index="04" label="The Payoff" tone="obsidian" glow="bottom-left" compact>
          <TextReveal
            as="h2"
            className="mb-5 block max-w-3xl text-balance text-[clamp(2.5rem,9vw,4rem)] font-extrabold leading-[0.92] tracking-tighter"
          >
            Sessions become streaks. Streaks become who you are.
          </TextReveal>
          <SceneLede>
            Hold the room, and the payoff compounds — experience, badges, and a table that expects
            you to show up present.
          </SceneLede>
        </Scene>

        <div className="relative overflow-hidden bg-obsidian">
          <DepthLayers ember={false} />
          <PinnedHorizontal trackClassName="gap-6 pl-6 pr-[30vw] items-stretch">
            <div className="h-[min(62vh,560px)] w-[86vw] shrink-0 sm:w-[58vw] md:w-[42vw] lg:w-[32vw]">
              <StreakCard />
            </div>
            <div className="h-[min(62vh,560px)] w-[86vw] shrink-0 sm:w-[58vw] md:w-[42vw] lg:w-[32vw]">
              <XpCard />
            </div>
            <div className="h-[min(62vh,560px)] w-[86vw] shrink-0 sm:w-[58vw] md:w-[42vw] lg:w-[32vw]">
              <AchievementCard />
            </div>
            <div className="flex h-[min(62vh,560px)] w-[86vw] shrink-0 flex-col justify-between rounded-3xl border border-ember/25 bg-gradient-to-br from-[#1a0f08] via-obsidian to-obsidian p-8 sm:w-[58vw] md:w-[42vw] lg:w-[32vw]">
              <span className="font-mono text-[10px] uppercase tracking-[0.3em] text-ember">
                The Return
              </span>
              <p className="text-balance text-[clamp(1.5rem,3.4vw,2.25rem)] font-extrabold leading-[1] tracking-tighter">
                The room stands. Phones come back. Something in the air stayed different.
              </p>
              <span className="font-mono text-[10px] uppercase tracking-[0.3em] text-muted-foreground">
                Held / Protocol.01
              </span>
            </div>
          </PinnedHorizontal>
        </div>

        {/* Voices — the payoff, in other people's words. Its own room: a
            darker floor, a soft top cut, and a single dominant marquee. */}
        <section className="relative overflow-hidden border-t border-white/5 bg-neutral-900/30 py-28 sm:py-24">
          {/* Single top-only cut-in, matching Scene — the next section (05)
              owns its own fade-in, so this boundary isn't double-darkened. */}
          <div
            aria-hidden
            className="pointer-events-none absolute inset-x-0 top-0 h-24 bg-gradient-to-b from-black to-transparent"
          />
          <div className="mx-auto mb-12 flex max-w-6xl flex-wrap items-end justify-between gap-6 px-6">
            <TextReveal
              as="h2"
              className="max-w-xl text-balance text-3xl font-extrabold tracking-tight sm:text-4xl"
            >
              The room speaks for itself.
            </TextReveal>
            <span className="font-mono text-[10px] uppercase tracking-[0.3em] text-ember">
              Field Reports / Ongoing
            </span>
          </div>
          <Marquee speedSeconds={55}>
            {[...VOICES, ...VOICES].map((v, i) => (
              <figure
                key={`${v.n}-${i}`}
                className="flex w-[340px] shrink-0 flex-col justify-between gap-8 rounded-2xl border border-white/10 bg-obsidian p-8 transition-colors hover:border-ember/40"
              >
                <blockquote className="text-balance text-lg font-medium leading-snug tracking-tight">
                  &ldquo;{v.q}&rdquo;
                </blockquote>
                <figcaption className="font-mono text-[10px] uppercase tracking-[0.3em] text-ember">
                  {v.n}
                </figcaption>
              </figure>
            ))}
          </Marquee>
        </section>

        {/* ═══ 05 · PHILOSOPHY ═════════════════════════════════════ */}
        <Scene
          id="philosophy"
          index="05"
          label="The Philosophy"
          tone="raised"
          glow="top-right"
          background={<DepthLayers />}
        >
          <div ref={philosophyPx.ref}>
            <div
              ref={philosophyPx.targetRef}
              className="flex flex-col gap-12 will-change-transform sm:gap-14 lg:grid lg:grid-cols-[1.1fr_0.9fr] lg:items-center lg:gap-20"
            >
              <div className="order-2 lg:order-1">
                <SceneTitle className="mb-7 sm:mb-10">
                  Presence is the
                  <br />
                  <span className="text-muted-foreground">new </span>
                  <MatrixText text="luxury." className="text-ember" />
                </SceneTitle>
                <SceneLede className="mb-8">
                  Stack&apos;d isn&apos;t another focus timer — it&apos;s a shared commitment to
                  being present. Non-digital space is a human right, and it is the one thing nobody
                  can sell back to you.
                </SceneLede>
                <Link
                  to="/philosophy"
                  className="font-mono text-[10px] uppercase tracking-widest text-silver-dim transition-colors hover:text-ember"
                >
                  Read the manifesto →
                </Link>
              </div>

              <div className="relative order-1 -mx-4 mt-8 sm:mx-0 sm:mt-0 lg:order-2">
                <div className="relative">
                  <div ref={mapSlotRef} data-visual="map" className="aspect-[76/34] w-full">
                    {mapInView ? (
                      <Suspense fallback={<MapSkeleton />}>
                        <DottedMap className="opacity-90" />
                      </Suspense>
                    ) : (
                      <MapSkeleton />
                    )}
                  </div>
                  {/* A live room floats over the atlas — the abstract made concrete. */}
                  <div className="pointer-events-none absolute -bottom-8 right-2 z-20 origin-bottom-right scale-[0.78] sm:right-4 sm:scale-90 lg:scale-100">
                    <LeaderboardPanel />
                  </div>
                </div>

                <p className="mt-12 max-w-[26ch] px-4 text-[0.95rem] leading-[1.6] text-silver-dim sm:mt-10 sm:max-w-md sm:px-0">
                  Every dot is a table somewhere choosing presence over noise — anonymously,
                  atomically, in sync.
                </p>
              </div>
            </div>
          </div>
        </Scene>

        {/* ═══ 06 · CTA ════════════════════════════════════════════ */}
        <Scene
          index="06"
          label="Begin"
          background={
            <>
              <DepthLayers />
              <div ref={meteorSlotRef} aria-hidden className="pointer-events-none absolute inset-0">
                {meteorInView ? (
                  <Suspense fallback={<MeteorSkeleton />}>
                    <Meteors count={24} />
                  </Suspense>
                ) : (
                  <MeteorSkeleton />
                )}
              </div>
            </>
          }
          contentClassName="max-w-3xl text-center"
          glow="center"
          handoff={false}
        >
          <div ref={ctaPx.ref}>
            <div ref={ctaPx.targetRef} className="will-change-transform">
              {/* The culmination — the last thing the story shows is the win,
                  stacked over two ghost frames of past sessions. */}
              <div className="relative mx-auto mb-8 mt-8 max-w-lg sm:mb-10 sm:mt-0">
                <span
                  aria-hidden
                  className="pointer-events-none absolute inset-x-8 -top-3 h-20 rotate-[1deg] rounded-3xl border border-white/8 bg-white/[0.015]"
                />
                <span
                  aria-hidden
                  className="pointer-events-none absolute inset-x-14 -top-7 h-20 rotate-[-1.2deg] rounded-3xl border border-white/5 bg-white/[0.01]"
                />
                <div className="relative">
                  <SessionCompleteCard />
                </div>
                <p className="sr-only">
                  Product preview: a completed session — sixty minutes held, one break, a focus
                  score of ninety-four.
                </p>
              </div>

              <SceneTitle className="mb-6">
                <ScrubText as="span" className="block">
                  Put the phone down.
                </ScrubText>
                <span className="block text-muted-foreground">
                  <ScrubText as="span">Pick the </ScrubText>
                  <ScrubText as="span" className="text-ember">
                    room
                  </ScrubText>
                  <ScrubText as="span"> up.</ScrubText>
                </span>
              </SceneTitle>
              <SceneLede className="mx-auto mb-8 text-center">
                One room. Six characters. The rest is silence you get to keep.
              </SceneLede>
              {console_}
            </div>
          </div>
        </Scene>
      </main>

      <footer className="border-t border-white/5 px-6 py-16">
        <div className="mx-auto grid max-w-7xl gap-12 md:grid-cols-4">
          <div className="max-w-sm md:col-span-2">
            <div className="mb-6 flex items-center gap-3">
              <Logo className="size-8" />
              <span className="font-mono text-xs uppercase tracking-[0.3em]">Stack&apos;d</span>
            </div>
            <p className="mb-6 text-sm leading-relaxed text-silver-dim">
              Designed for those who value the space between notifications. A private protocol for
              shared, intentional offline time.
            </p>
            <p className="font-mono text-[10px] uppercase tracking-widest text-muted-foreground">
              © {new Date().getFullYear()} Stack&apos;d Protocol
            </p>
          </div>
          <div>
            <div className="mb-4 font-mono text-[10px] uppercase tracking-widest text-muted-foreground">
              Protocol
            </div>
            <ul className="space-y-3 text-sm">
              <li>
                <Link to="/philosophy" className="transition-colors hover:text-ember">
                  Philosophy
                </Link>
              </li>
              <li>
                <Link to="/auth" className="transition-colors hover:text-ember">
                  Enter
                </Link>
              </li>
              <li>
                <a href="#philosophy" className="transition-colors hover:text-ember">
                  Pillars
                </a>
              </li>
            </ul>
          </div>
          <div>
            <div className="mb-4 font-mono text-[10px] uppercase tracking-widest text-muted-foreground">
              Signal
            </div>
            <ul className="space-y-3 text-sm">
              <li>
                <a href="mailto:hello@stackd.app" className="transition-colors hover:text-ember">
                  Press
                </a>
              </li>
              <li>
                <a href="mailto:hello@stackd.app" className="transition-colors hover:text-ember">
                  Contact
                </a>
              </li>
              <li>
                <a href="#" className="transition-colors hover:text-ember">
                  Privacy
                </a>
              </li>
            </ul>
          </div>
        </div>
        <div className="mx-auto mt-16 max-w-7xl border-t border-white/5 pt-8 text-center font-mono text-[10px] uppercase tracking-[0.5em] text-muted-foreground">
          Non-digital space is a human right.
        </div>
      </footer>
    </div>
  );
}

