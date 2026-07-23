import { useEffect, useState } from "react";
import { haptic } from "@/lib/haptics";
import { copy } from "@/lib/copy";

export interface CeremonyDetail {
  durationSeconds: number;
  xpEarned: number;
  score?: number;
  tier?: string;
  achievements?: string[];
  streak?: number;
}

/**
 * Cinematic post-session moment. Mount once in __root; dispatch from anywhere:
 *
 *   window.dispatchEvent(new CustomEvent("stackd:ceremony", { detail: {...} }))
 *
 * Respects prefers-reduced-motion by shortening durations, never blocks input
 * for more than 4s, and dismisses on tap/Escape.
 */
export function SessionCeremony() {
  const [detail, setDetail] = useState<CeremonyDetail | null>(null);
  const [xpDisplay, setXpDisplay] = useState(0);

  useEffect(() => {
    const handler = (e: Event) => {
      const d = (e as CustomEvent<CeremonyDetail>).detail;
      if (!d) return;
      setDetail(d);
      setXpDisplay(0);
      haptic("success");
    };
    window.addEventListener("stackd:ceremony", handler);
    return () => window.removeEventListener("stackd:ceremony", handler);
  }, []);

  useEffect(() => {
    if (!detail) return;
    const reduce = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;
    const dur = reduce ? 400 : 1600;
    const start = performance.now();
    let raf = 0;
    const tick = (t: number) => {
      const p = Math.min(1, (t - start) / dur);
      const eased = 1 - Math.pow(1 - p, 3);
      setXpDisplay(Math.round(detail.xpEarned * eased));
      if (p < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);

    const esc = (e: KeyboardEvent) => { if (e.key === "Escape") setDetail(null); };
    window.addEventListener("keydown", esc);
    const auto = window.setTimeout(() => setDetail(null), reduce ? 2200 : 5200);

    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener("keydown", esc);
      window.clearTimeout(auto);
    };
  }, [detail]);

  if (!detail) return null;
  const mins = Math.max(1, Math.round(detail.durationSeconds / 60));

  return (
    <div
      role="dialog"
      aria-live="polite"
      aria-label="Session complete"
      onClick={() => setDetail(null)}
      className="fixed inset-0 z-[100] flex items-center justify-center bg-obsidian/85 backdrop-blur-xl animate-fade-in cursor-pointer"
    >
      <div
        className="absolute inset-0 pointer-events-none"
        style={{
          background:
            "radial-gradient(ellipse at center, rgba(240,169,104,0.25) 0%, rgba(240,169,104,0.08) 30%, transparent 65%)",
          animation: "ceremony-glow 1.8s ease-out",
        }}
      />
      <div className="relative text-center px-6 max-w-md animate-scale-in">
        <p className="font-mono text-[10px] tracking-[0.35em] uppercase text-ember">Held</p>
        <p className="mt-4 font-serif text-5xl md:text-6xl text-silver leading-none">
          {copy.session.completedBody(mins)}
        </p>
        <div className="mt-8 flex items-baseline justify-center gap-2">
          <span className="font-serif text-6xl md:text-7xl text-ember tabular-nums">
            +{xpDisplay.toLocaleString()}
          </span>
          <span className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">XP</span>
        </div>
        {detail.tier && (
          <p className="mt-3 font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">
            Tier · <span className="text-silver">{detail.tier}</span>
          </p>
        )}
        {detail.streak ? (
          <p className="mt-2 font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">
            Streak · <span className="text-ember">{detail.streak}</span> days
          </p>
        ) : null}
        {detail.achievements && detail.achievements.length > 0 && (
          <div className="mt-6 space-y-1">
            {detail.achievements.slice(0, 3).map((a) => (
              <p
                key={a}
                className="font-mono text-xs tracking-widest uppercase text-ember animate-fade-in"
              >
                ◆ {a}
              </p>
            ))}
          </div>
        )}
        <p className="mt-8 font-mono text-[9px] tracking-[0.3em] uppercase text-silver-dim">
          Tap to close
        </p>
      </div>
    </div>
  );
}

/** Convenience for callers. */
export function celebrateSession(detail: CeremonyDetail) {
  window.dispatchEvent(new CustomEvent("stackd:ceremony", { detail }));
}
