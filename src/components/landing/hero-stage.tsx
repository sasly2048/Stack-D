import { useEffect, useRef, type ReactNode } from "react";
import { BreachToast, XpChip, LeaderboardPanel } from "@/components/landing/product-panels";

/**
 * HeroStage — the first-fold cinematic composition.
 *
 * A perspective "camera" that reacts to pointer movement, holding four
 * face-down phones mid-stack plus floating slices of real app chrome
 * (live room frame, break-detection alert, XP gain, leaderboard). The goal
 * is that the first screen says "multiplayer phone stacking" without a
 * single word of explanation.
 *
 * Fully decorative: aria-hidden, pointer-events off except nothing
 * interactive lives here. Respects prefers-reduced-motion (camera locks).
 */
function Layer({
  depth,
  className = "",
  children,
}: {
  /** 0 = far, 1 = near. Drives the pointer-parallax multiplier. */
  depth: number;
  className?: string;
  children: ReactNode;
}) {
  return (
    <div data-cam-depth={depth} className={`absolute will-change-transform ${className}`}>
      {children}
    </div>
  );
}

export function HeroStage({ className = "" }: { className?: string }) {
  const root = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = root.current;
    if (!el) return;
    if (window.matchMedia?.("(prefers-reduced-motion: reduce)").matches) return;
    if (!window.matchMedia?.("(hover: hover)").matches) return;

    const layers = Array.from(el.querySelectorAll<HTMLElement>("[data-cam-depth]"));
    let raf = 0;
    let running = false;
    let visible = false;
    let tx = 0;
    let ty = 0;
    let cx = 0;
    let cy = 0;

    const render = () => {
      cx += (tx - cx) * 0.08;
      cy += (ty - cy) * 0.08;
      el.style.transform = `rotateX(${(-cy * 5).toFixed(2)}deg) rotateY(${(cx * 7).toFixed(2)}deg)`;
      layers.forEach((l) => {
        const d = parseFloat(l.dataset.camDepth || "0.5");
        l.style.setProperty("--cam-x", `${(cx * 34 * d).toFixed(2)}px`);
        l.style.setProperty("--cam-y", `${(cy * 26 * d).toFixed(2)}px`);
      });
      raf = requestAnimationFrame(render);
    };

    // The camera only needs to run while the stage is actually on screen and
    // the tab is visible — otherwise it's a free-running RAF for a transform
    // nobody sees.
    const start = () => {
      if (running) return;
      running = true;
      raf = requestAnimationFrame(render);
    };
    const stop = () => {
      if (!running) return;
      running = false;
      cancelAnimationFrame(raf);
    };

    const io = new IntersectionObserver(
      ([entry]) => {
        visible = entry.isIntersecting;
        if (visible && !document.hidden) start();
        else stop();
      },
      { threshold: 0 },
    );
    io.observe(el);

    const onVisibility = () => {
      if (document.hidden) stop();
      else if (visible) start();
    };
    document.addEventListener("visibilitychange", onVisibility);

    const onMove = (e: PointerEvent) => {
      const r = el.getBoundingClientRect();
      tx = (e.clientX - (r.left + r.width / 2)) / (r.width / 2);
      ty = (e.clientY - (r.top + r.height / 2)) / (r.height / 2);
    };
    const onLeave = () => {
      tx = 0;
      ty = 0;
    };

    // Pointer tracking is scoped to the stage element itself, not the whole
    // window — the camera should only react while the cursor is actually
    // over the hero.
    el.addEventListener("pointermove", onMove, { passive: true });
    el.addEventListener("pointerleave", onLeave);
    return () => {
      el.removeEventListener("pointermove", onMove);
      el.removeEventListener("pointerleave", onLeave);
      document.removeEventListener("visibilitychange", onVisibility);
      io.disconnect();
      stop();
    };
  }, []);

  return (
    <div
      aria-hidden
      className={`relative mx-auto aspect-square w-full max-w-none origin-center scale-[0.88] [perspective:1400px] sm:aspect-[5/6] sm:max-w-[600px] sm:scale-100 lg:max-w-[640px] ${className}`}
    >
      {/* Ambient bloom behind the whole stage. */}
      <div
        className="pointer-events-none absolute -inset-12 -z-10 blur-3xl"
        style={{
          background:
            "radial-gradient(55% 45% at 50% 45%, rgba(240,169,104,0.20) 0%, transparent 72%)",
        }}
      />

      <div
        ref={root}
        className="absolute inset-0 transition-transform duration-500 ease-out [transform-style:preserve-3d]"
      >
        {/* Table plate — grounds the stack. */}
        <Layer
          depth={0.2}
          className="left-1/2 top-[62%] h-[46%] w-[78%] translate-x-[calc(-50%+var(--cam-x,0px))] translate-y-[var(--cam-y,0px)] rounded-[50%] border border-white/5 bg-white/[0.02] blur-[2px]"
        >
          <span className="sr-only" />
        </Layer>

        {/* Four stacked, face-down phones in perspective. */}
        <Layer
          depth={0.55}
          className="left-1/2 top-[44%] translate-x-[calc(-50%+var(--cam-x,0px))] translate-y-[calc(-50%+var(--cam-y,0px))]"
        >
          <div className="relative h-[330px] w-[208px] [transform-style:preserve-3d] sm:h-[350px] sm:w-[222px]">
            {[0, 1, 2, 3].map((i) => (
              <div
                key={i}
                className="absolute inset-0 rounded-[26px] border border-white/12 bg-gradient-to-b from-white/[0.10] to-white/[0.015] shadow-[0_50px_90px_-40px_rgba(0,0,0,0.95)]"
                style={{
                  transform: `translateY(${i * -16}px) translateZ(${i * 26}px) rotate(${(i % 2 ? 1 : -1) * 2.2}deg)`,
                  animation: `float-soft ${6 + i}s ease-in-out ${i * 0.35}s infinite`,
                }}
              >
                <span className="absolute left-1/2 top-4 h-1 w-10 -translate-x-1/2 rounded-full bg-white/10" />
                <span
                  className={`absolute bottom-4 left-1/2 h-1.5 w-1.5 -translate-x-1/2 rounded-full ${
                    i === 3 ? "bg-breach" : "bg-pulse"
                  }`}
                />
              </div>
            ))}
          </div>
        </Layer>

        {/*
          Handheld composition is deliberately sparser than desktop: three
          panels on a clear diagonal (room → break → payoff) instead of four
          overlapping ones, so the stack stays readable at 390px.
        */}

        {/* Live room frame — timer + roster, floating near-left. */}
        <Layer
          depth={0.85}
          className="left-0 top-[2%] z-20 w-[196px] translate-x-[var(--cam-x,0px)] translate-y-[var(--cam-y,0px)] sm:-left-[10%] sm:top-[8%] sm:w-[210px]"
        >
          <div className="glass rounded-2xl p-4 shadow-[0_34px_90px_-34px_rgba(0,0,0,0.95)]">
            <div className="mb-3 flex items-center justify-between">
              <span className="font-mono text-[9px] uppercase tracking-[0.3em] text-muted-foreground">
                Room / 86YSDS
              </span>
              <span className="size-[var(--dot-size-sm)] animate-pulse rounded-full bg-pulse" />
            </div>
            <div className="font-mono text-3xl font-bold tracking-tight text-silver">42:18</div>
            <div className="mt-1 font-mono text-[9px] uppercase tracking-[0.3em] text-ember">
              Held together
            </div>
            <div className="mt-4 flex -space-x-2">
              {["L", "D", "P", "M"].map((n, i) => (
                <span
                  key={n}
                  className={`grid size-7 place-items-center rounded-full border font-mono text-[9px] ${
                    i === 3
                      ? "border-breach/50 bg-breach/10 text-breach"
                      : "border-ember/40 bg-obsidian text-ember"
                  }`}
                >
                  {n}
                </span>
              ))}
            </div>
          </div>
        </Layer>

        {/* Break detection — near-right, the tension beat. */}
        <Layer
          depth={1.1}
          className="right-0 top-[38%] z-30 translate-x-[var(--cam-x,0px)] translate-y-[var(--cam-y,0px)] sm:-right-[8%] sm:top-[30%]"
        >
          <BreachToast className="w-[200px] sm:w-[214px]" />
        </Layer>

        {/* XP gain — nearest layer, the payoff beat. */}
        <Layer
          depth={1.3}
          className="bottom-[4%] left-0 z-30 translate-x-[var(--cam-x,0px)] translate-y-[var(--cam-y,0px)] sm:-left-[3%] sm:bottom-[16%]"
        >
          <XpChip className="w-[186px] sm:w-[190px]" />
        </Layer>

        {/* Leaderboard — desktop-only fourth beat; it would crowd handhelds. */}
        <Layer
          depth={0.7}
          className="-right-[10%] bottom-[-4%] z-10 hidden translate-x-[var(--cam-x,0px)] translate-y-[var(--cam-y,0px)] opacity-95 sm:block"
        >
          <LeaderboardPanel />
        </Layer>
      </div>
    </div>
  );
}
