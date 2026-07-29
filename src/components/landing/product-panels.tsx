import { NumberTicker } from "@/components/fx/number-ticker";

/**
 * Floating product surfaces used to compose premium, layered scenes on the
 * landing page. Each panel is a faithful slice of real app chrome (glass,
 * mono labels, ember accent) so the story shows the product, not an
 * illustration. All are decorative — callers provide the text alternative.
 */

/** Live break-detection alert, as it appears in-room the moment a phone lifts. */
export function BreachToast({ className = "" }: { className?: string }) {
  return (
    <div
      aria-hidden
      className={`glass flex items-center gap-3 rounded-2xl border-breach/40 px-4 py-3 shadow-[0_24px_60px_-24px_rgba(0,0,0,0.9)] ${className}`}
    >
      <span className="relative grid size-8 shrink-0 place-items-center rounded-lg border border-breach/40 bg-breach/10">
        <span className="size-[var(--dot-size-sm)] animate-pulse rounded-full bg-breach" />
      </span>
      <span className="min-w-0">
        <span className="block truncate text-sm font-medium text-silver">Mateo lifted</span>
        <span className="block font-mono text-[9px] uppercase tracking-[0.25em] text-breach">
          Break detected · 0.4s
        </span>
      </span>
    </div>
  );
}

/** Compact XP gain chip — the reward loop, floating over the scene. */
export function XpChip({ className = "" }: { className?: string }) {
  return (
    <div
      aria-hidden
      className={`glass flex items-center gap-3 rounded-2xl px-4 py-3 shadow-[0_24px_60px_-24px_rgba(0,0,0,0.9)] ${className}`}
    >
      <span className="grid size-8 shrink-0 place-items-center rounded-lg border border-ember/40 bg-ember/10 font-mono text-[10px] text-ember">
        ✦
      </span>
      <span>
        <span className="block font-mono text-sm font-bold tracking-tight text-ember">
          +<NumberTicker value={420} suffix=" XP" />
        </span>
        <span className="block font-mono text-[9px] uppercase tracking-[0.25em] text-muted-foreground">
          Session held
        </span>
      </span>
    </div>
  );
}

const RANKS = [
  { n: "Priya", v: "18h 20m" },
  { n: "Léa", v: "16h 05m" },
  { n: "Devon", v: "14h 44m" },
];

/** Miniature leaderboard window — the social pressure made visible. */
export function LeaderboardPanel({ className = "" }: { className?: string }) {
  return (
    <div
      aria-hidden
      className={`glass w-[248px] rounded-2xl p-5 shadow-[0_30px_80px_-30px_rgba(0,0,0,0.95)] ${className}`}
    >
      <div className="mb-4 flex items-center justify-between">
        <span className="font-mono text-[9px] uppercase tracking-[0.3em] text-muted-foreground">
          Leaderboard
        </span>
        <span className="font-mono text-[9px] uppercase tracking-[0.3em] text-ember">Week 12</span>
      </div>
      <ul className="space-y-2">
        {RANKS.map((r, i) => (
          <li key={r.n} className="flex items-center gap-3">
            <span
              className={`grid size-6 shrink-0 place-items-center rounded-md border font-mono text-[9px] ${
                i === 0
                  ? "border-ember/40 bg-ember/10 text-ember"
                  : "border-white/8 bg-white/[0.02] text-muted-foreground"
              }`}
            >
              {i + 1}
            </span>
            <span className="min-w-0 flex-1 truncate text-xs text-silver">{r.n}</span>
            <span className="font-mono text-[9px] tracking-widest text-muted-foreground">
              {r.v}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** Reaction rail — the room cheering without breaking the silence. */
export function ReactionRail({ className = "" }: { className?: string }) {
  const items = ["✦", "◆", "▲", "●"];
  return (
    <div
      aria-hidden
      className={`glass flex items-center gap-2 rounded-full px-3 py-2 shadow-[0_24px_60px_-24px_rgba(0,0,0,0.9)] ${className}`}
    >
      {items.map((s, i) => (
        <span
          key={s}
          className="grid size-8 place-items-center rounded-full border border-white/8 bg-white/[0.02] font-mono text-[11px] text-ember"
          style={{ animation: `float-soft ${4 + i * 0.6}s ease-in-out ${i * 0.3}s infinite` }}
        >
          {s}
        </span>
      ))}
      <span className="pl-1 pr-2 font-mono text-[9px] uppercase tracking-[0.25em] text-muted-foreground">
        12 sent
      </span>
    </div>
  );
}

/** Session-completion card — the culminating payoff surface. */
export function SessionCompleteCard({ className = "" }: { className?: string }) {
  return (
    <div
      aria-hidden
      className={`glass overflow-hidden rounded-3xl border-ember/25 shadow-[0_40px_120px_-40px_rgba(240,169,104,0.35)] ${className}`}
    >
      <div className="flex items-center justify-between border-b border-white/5 px-6 py-4">
        <span className="font-mono text-[10px] uppercase tracking-[0.3em] text-ember">
          Session complete
        </span>
        <span className="font-mono text-[10px] uppercase tracking-[0.3em] text-muted-foreground">
          Room / 86YSDS
        </span>
      </div>
      <div className="grid grid-cols-3 gap-px bg-white/5">
        {[
          { k: "Held", v: "60:00" },
          { k: "Breaks", v: "01" },
          { k: "Score", v: "94" },
        ].map((s) => (
          <div key={s.k} className="bg-obsidian px-5 py-6 text-center">
            <div className="font-mono text-xl font-bold tracking-tight text-silver">{s.v}</div>
            <div className="mt-1 font-mono text-[9px] uppercase tracking-[0.25em] text-muted-foreground">
              {s.k}
            </div>
          </div>
        ))}
      </div>
      <div className="px-6 py-5">
        <div className="h-1.5 w-full overflow-hidden rounded-full bg-white/[0.06]">
          <div className="h-full w-[94%] rounded-full bg-gradient-to-r from-ember to-ember-glow" />
        </div>
        <p className="mt-4 text-sm leading-relaxed text-silver-dim">
          Four phones down for a full hour. The table remembers this one.
        </p>
      </div>
    </div>
  );
}
