import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";

import { getAiUsage, type AiUsage } from "@/lib/subscription.functions";

/**
 * Transparent AI-usage counter, e.g. "128 / 200 AI actions left this cycle".
 * Renders only when the user has a real allowance (Pro/Elite) — hidden for free
 * (no AI) and for admin/lifetime (unlimited). Read-only; consuming happens
 * server-side when an AI feature runs.
 */
export function AiUsageMeter({ className = "" }: { className?: string }) {
  const load = useServerFn(getAiUsage);
  const [usage, setUsage] = useState<AiUsage | null>(null);

  useEffect(() => {
    load()
      .then(setUsage)
      .catch(() => undefined);
  }, [load]);

  if (!usage || usage.unlimited || usage.allowance <= 0) return null;

  const pct = Math.min(100, Math.round((usage.used / usage.allowance) * 100));
  const low = usage.remaining <= Math.max(1, Math.round(usage.allowance * 0.1));

  return (
    <div className={`rounded-lg border border-border bg-white/[0.02] p-3 ${className}`}>
      <div className="flex items-center justify-between">
        <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">
          AI actions
        </p>
        <p className="font-mono text-[11px] text-silver">
          <span className={low ? "text-breach" : "text-ember"}>{usage.remaining}</span>
          <span className="text-silver-dim"> / {usage.allowance} left</span>
        </p>
      </div>
      <div className="mt-2 h-1 rounded-full bg-white/[0.06] overflow-hidden">
        <div
          className={`h-full transition-all ${low ? "bg-breach/70" : "bg-ember/70"}`}
          style={{ width: `${pct}%` }}
        />
      </div>
      <p className="mt-1.5 text-[10px] text-silver-dim">Resets when your subscription renews.</p>
    </div>
  );
}
