import { useEffect, useState } from "react";
import type { TierMeta } from "@/lib/focus-score";
import { formatDuration } from "@/lib/room";

interface Props {
  score: number;
  xp: number;
  tier: TierMeta;
  durationSeconds: number;
  breachesCount: number;
}

export function ResultsCard({ score, xp, tier, durationSeconds, breachesCount }: Props) {
  const [animated, setAnimated] = useState(0);
  useEffect(() => {
    const id = requestAnimationFrame(() => setAnimated(score));
    return () => cancelAnimationFrame(id);
  }, [score]);

  return (
    <div
      role="region"
      aria-label="Session results"
      className="border border-white/10 rounded-2xl p-6 sm:p-8 bg-white/[0.03]"
      style={{ boxShadow: `0 0 80px -40px ${tier.hex}` }}
    >
      <div className="flex items-center justify-between mb-6">
        <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground">
          Focus Score
        </div>
        <div
          className="font-mono text-[10px] tracking-[0.3em] uppercase"
          style={{ color: tier.hex }}
        >
          {tier.label}
        </div>
      </div>

      <div className="flex items-baseline gap-4 mb-4">
        <div className="text-7xl font-extrabold tracking-tighter" style={{ color: tier.hex }}>
          {score}
        </div>
        <div className="font-mono text-xs text-muted-foreground uppercase tracking-widest">
          / 100
        </div>
      </div>

      <div className="h-2 w-full bg-white/5 rounded-full overflow-hidden mb-8" aria-hidden>
        <div
          className="h-full rounded-full transition-[width] duration-[1200ms] ease-out"
          style={{ width: `${animated}%`, background: tier.hex, boxShadow: `0 0 24px ${tier.hex}` }}
        />
      </div>

      <dl className="grid grid-cols-3 gap-4 font-mono text-xs">
        <Metric label="Duration" value={formatDuration(durationSeconds)} />
        <Metric label="XP Earned" value={`+${xp}`} />
        <Metric label="Anomalies" value={String(breachesCount)} />
      </dl>
    </div>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-[10px] uppercase tracking-[0.3em] text-muted-foreground mb-1">{label}</dt>
      <dd className="text-lg text-silver">{value}</dd>
    </div>
  );
}
