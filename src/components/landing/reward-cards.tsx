import { NumberTicker } from "@/components/fx/number-ticker";

/**
 * Reward surfaces — streak, XP and achievement cards rendered with the exact
 * app chrome (glass panels, mono labels, ember accent) so the payoff scene
 * previews the real product rather than an illustration.
 */

export function StreakCard({ className = "" }: { className?: string }) {
  const days = ["M", "T", "W", "T", "F", "S", "S"];
  const held = [true, true, true, true, true, false, true];
  return (
    <div className={`glass flex h-full flex-col justify-between rounded-3xl p-8 ${className}`}>
      <div className="flex items-center justify-between">
        <span className="font-mono text-[10px] uppercase tracking-[0.3em] text-muted-foreground">
          Streak
        </span>
        <span className="size-[var(--dot-size-sm)] rounded-full bg-ember shadow-[var(--dot-glow)]" />
      </div>
      <div>
        <div className="text-[clamp(3rem,7vw,4.5rem)] font-extrabold leading-none tracking-tighter text-ember">
          <NumberTicker value={17} suffix="d" />
        </div>
        <p className="mt-3 text-sm leading-relaxed text-silver-dim">
          Seventeen consecutive days where the table chose silence first.
        </p>
      </div>
      <div className="flex gap-2" aria-hidden>
        {days.map((d, i) => (
          <span
            key={`${d}-${i}`}
            className={`flex h-9 flex-1 items-center justify-center rounded-lg border font-mono text-[10px] uppercase ${
              held[i]
                ? "border-ember/40 bg-ember/10 text-ember"
                : "border-white/8 bg-white/[0.02] text-muted-foreground"
            }`}
          >
            {d}
          </span>
        ))}
      </div>
    </div>
  );
}

export function XpCard({ className = "" }: { className?: string }) {
  return (
    <div className={`glass flex h-full flex-col justify-between rounded-3xl p-8 ${className}`}>
      <div className="flex items-center justify-between">
        <span className="font-mono text-[10px] uppercase tracking-[0.3em] text-muted-foreground">
          Progression
        </span>
        <span className="font-mono text-[10px] uppercase tracking-[0.3em] text-ember">Tier IV</span>
      </div>
      <div>
        <div className="text-[clamp(3rem,7vw,4.5rem)] font-extrabold leading-none tracking-tighter text-silver">
          <NumberTicker value={12480} suffix=" XP" />
        </div>
        <p className="mt-3 text-sm leading-relaxed text-silver-dim">
          Every held minute compounds. Presence becomes a number you can watch grow.
        </p>
      </div>
      <div>
        <div className="h-2 w-full overflow-hidden rounded-full bg-white/[0.06]" aria-hidden>
          <div
            className="h-full rounded-full bg-gradient-to-r from-ember to-ember-glow"
            style={{ width: "72%" }}
          />
        </div>
        <div className="mt-3 flex justify-between font-mono text-[10px] uppercase tracking-[0.3em] text-muted-foreground">
          <span>Tier IV</span>
          <span>3,520 to V</span>
        </div>
      </div>
    </div>
  );
}

const BADGES = [
  { t: "First Stack", s: "Held a full session" },
  { t: "Unbroken", s: "Zero breaches, 10 rooms" },
  { t: "Night Table", s: "Six dinners, phones down" },
];

export function AchievementCard({ className = "" }: { className?: string }) {
  return (
    <div className={`glass flex h-full flex-col justify-between rounded-3xl p-8 ${className}`}>
      <div className="flex items-center justify-between">
        <span className="font-mono text-[10px] uppercase tracking-[0.3em] text-muted-foreground">
          Achievements
        </span>
        <span className="font-mono text-[10px] uppercase tracking-[0.3em] text-ember">
          <NumberTicker value={24} suffix=" / 40" />
        </span>
      </div>
      <ul className="mt-8 space-y-3">
        {BADGES.map((b) => (
          <li
            key={b.t}
            className="flex items-center gap-4 rounded-xl border border-white/8 bg-white/[0.02] px-4 py-3"
          >
            <span
              aria-hidden
              className="grid size-9 shrink-0 place-items-center rounded-lg border border-ember/40 bg-ember/10 font-mono text-[10px] text-ember"
            >
              ✦
            </span>
            <span className="min-w-0">
              <span className="block truncate text-sm font-medium text-silver">{b.t}</span>
              <span className="block truncate font-mono text-[10px] uppercase tracking-[0.2em] text-muted-foreground">
                {b.s}
              </span>
            </span>
          </li>
        ))}
      </ul>
      <p className="mt-6 text-sm leading-relaxed text-silver-dim">
        The room witnesses it. That is what makes it stick.
      </p>
    </div>
  );
}
