import { useEffect, useRef, useState } from "react";

const MEMBERS = [
  { n: "Léa", s: "holding" },
  { n: "Devon", s: "holding" },
  { n: "Priya", s: "holding" },
  { n: "Mateo", s: "lifted" },
] as const;

/**
 * RoomPreview — a realistic, non-interactive product preview of a live Stack'd
 * room. Reuses the exact app surfaces (glass panels, mono labels, ember accent,
 * pulse status dots) so the landing page reads as the product, not a mockup.
 *
 * Decorative only: the whole frame is aria-hidden and a text summary is
 * rendered for assistive tech by the caller.
 */
export function RoomPreview({ className = "" }: { className?: string }) {
  const [tick, setTick] = useState(0);
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (window.matchMedia?.("(prefers-reduced-motion: reduce)").matches) return;
    const el = rootRef.current;
    if (!el) return;

    let id: number | null = null;
    const start = () => {
      if (id !== null) return;
      id = window.setInterval(() => setTick((t) => t + 1), 1000);
    };
    const stop = () => {
      if (id === null) return;
      window.clearInterval(id);
      id = null;
    };

    // Only tick while the preview is actually on screen — no point paying
    // for a re-render every second once the user has scrolled past it, and
    // a hidden tab shouldn't keep the interval alive at all.
    const io = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && !document.hidden) start();
        else stop();
      },
      { threshold: 0 },
    );
    io.observe(el);

    const onVisibility = () => {
      if (document.hidden) stop();
      else if (el.getBoundingClientRect().top < window.innerHeight) start();
    };
    document.addEventListener("visibilitychange", onVisibility);

    return () => {
      io.disconnect();
      document.removeEventListener("visibilitychange", onVisibility);
      stop();
    };
  }, []);

  const total = 60 * 60;
  const elapsed = (2_147 + tick) % total;
  const remaining = total - elapsed;
  const mm = String(Math.floor(remaining / 60)).padStart(2, "0");
  const ss = String(remaining % 60).padStart(2, "0");
  const progress = elapsed / total;

  return (
    <div ref={rootRef} aria-hidden className={`relative ${className}`}>
      {/* Duplicated frames behind the primary panel — depth without noise. */}
      <div className="pointer-events-none absolute inset-0 -z-10 translate-x-6 translate-y-6 rounded-3xl border border-white/5 bg-white/[0.015] blur-[1px]" />
      <div className="pointer-events-none absolute inset-0 -z-20 translate-x-12 translate-y-12 rounded-3xl border border-white/5 bg-white/[0.01] blur-[3px]" />
      {/* Ambient light behind the stack. */}
      <div
        className="pointer-events-none absolute -inset-16 -z-30 opacity-70 blur-3xl"
        style={{
          background:
            "radial-gradient(60% 50% at 50% 40%, rgba(240,169,104,0.16) 0%, transparent 70%)",
        }}
      />

      <div className="glass overflow-hidden rounded-3xl">
        <div className="flex items-center justify-between border-b border-white/5 px-6 py-4">
          <span className="font-mono text-[10px] uppercase tracking-[0.3em] text-muted-foreground">
            Room / 86YSDS
          </span>
          <span className="inline-flex items-center gap-2 font-mono text-[10px] uppercase tracking-[0.3em] text-pulse">
            <span className="size-[var(--dot-size-sm)] rounded-full bg-pulse" />
            Live
          </span>
        </div>

        <div className="grid gap-6 px-6 py-7 sm:grid-cols-[auto_minmax(0,1fr)] sm:items-center">
          {/* Timer ring */}
          <div className="relative mx-auto size-32 shrink-0 sm:mx-0">
            <svg viewBox="0 0 100 100" className="size-full -rotate-90">
              <circle
                cx="50"
                cy="50"
                r="45"
                fill="none"
                stroke="rgba(255,255,255,0.08)"
                strokeWidth="2"
              />
              <circle
                cx="50"
                cy="50"
                r="45"
                fill="none"
                stroke="var(--color-ember)"
                strokeWidth="2"
                strokeLinecap="round"
                strokeDasharray={2 * Math.PI * 45}
                strokeDashoffset={2 * Math.PI * 45 * (1 - progress)}
              />
            </svg>
            <div className="absolute inset-0 flex flex-col items-center justify-center">
              <span className="font-mono text-2xl font-bold tracking-tight text-silver">
                {mm}:{ss}
              </span>
              <span className="font-mono text-[9px] uppercase tracking-[0.3em] text-muted-foreground">
                Held
              </span>
            </div>
          </div>

          {/* Roster */}
          <ul className="space-y-2">
            {MEMBERS.map((m) => (
              <li
                key={m.n}
                className={`flex items-center justify-between rounded-xl border px-4 py-2.5 ${
                  m.s === "lifted"
                    ? "border-breach/40 bg-breach/5"
                    : "border-white/8 bg-white/[0.02]"
                }`}
              >
                <span className="flex min-w-0 items-center gap-3">
                  <span
                    className={`size-[var(--dot-size-sm)] shrink-0 rounded-full ${
                      m.s === "lifted" ? "bg-breach" : "bg-pulse"
                    }`}
                  />
                  <span className="truncate text-sm text-silver">{m.n}</span>
                </span>
                <span
                  className={`font-mono text-[9px] uppercase tracking-[0.25em] ${
                    m.s === "lifted" ? "text-breach" : "text-muted-foreground"
                  }`}
                >
                  {m.s === "lifted" ? "Lifted" : "Holding"}
                </span>
              </li>
            ))}
          </ul>
        </div>

        <div className="flex items-center justify-between border-t border-white/5 px-6 py-4">
          <span className="font-mono text-[9px] uppercase tracking-[0.3em] text-muted-foreground">
            Tilt · Lift · Screen wake
          </span>
          <span className="font-mono text-[9px] uppercase tracking-[0.3em] text-ember">
            Synced 40ms
          </span>
        </div>
      </div>
    </div>
  );
}

/**
 * PhoneStack — four face-down phones stacked in perspective, tilting subtly
 * with pointer movement on devices that support hover.
 */
export function PhoneStack({ className = "" }: { className?: string }) {
  return (
    <div
      aria-hidden
      className={`group relative mx-auto aspect-[4/5] w-full max-w-sm [perspective:1200px] ${className}`}
    >
      <div className="absolute inset-0 flex items-center justify-center transition-transform duration-[900ms] ease-[cubic-bezier(0.22,1,0.36,1)] [transform-style:preserve-3d] group-hover:[transform:rotateX(10deg)_rotateY(-8deg)]">
        {[0, 1, 2, 3].map((i) => (
          <div
            key={i}
            className="absolute h-48 w-28 rounded-2xl border border-white/10 bg-gradient-to-b from-white/[0.08] to-white/[0.02] shadow-[0_30px_60px_-30px_rgba(0,0,0,0.9)]"
            style={{
              transform: `translateY(${i * -10}px) translateZ(${i * 18}px) rotate(${(i % 2 ? 1 : -1) * 1.6}deg)`,
            }}
          />
        ))}
      </div>
    </div>
  );
}
