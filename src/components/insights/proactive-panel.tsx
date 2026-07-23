import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { getProactiveInsights, type ProactiveInsight } from "@/lib/proactive-ai.functions";

export function ProactivePanel() {
  const load = useServerFn(getProactiveInsights);
  const [data, setData] = useState<ProactiveInsight | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    load()
      .then((d) => alive && setData(d))
      .catch(() => {})
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [load]);

  if (loading) {
    return (
      <section className="glass rounded-md p-6">
        <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Proactive AI</p>
        <p className="mt-4 text-xs text-silver-dim">Reading your signal…</p>
      </section>
    );
  }
  if (!data) return null;

  const riskColor =
    data.burnout.risk === "high"
      ? "text-breach"
      : data.burnout.risk === "medium"
        ? "text-ember"
        : "text-pulse";

  return (
    <section className="glass rounded-md p-6 space-y-6">
      <div className="flex items-baseline justify-between">
        <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Proactive AI</p>
        <p className="font-mono text-[9px] tracking-[0.2em] uppercase text-silver-dim/60">
          {new Date(data.generatedAt).toLocaleTimeString()}
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-3">
        <Card
          label="Smart Schedule"
          headline={
            data.smartSchedule
              ? data.smartSchedule.label
              : "Not enough data yet"
          }
          body={
            data.smartSchedule?.rationale ??
            "Log a few more sessions and the model will find your peak window."
          }
        />
        <Card
          label="Focus Prediction"
          headline={`${data.focusPrediction.nextScore} · ${data.focusPrediction.confidence}`}
          body={data.focusPrediction.note}
        />
        <Card
          label="Burnout Signal"
          headline={
            <span className={riskColor + " uppercase tracking-widest font-mono text-sm"}>
              {data.burnout.risk}
            </span>
          }
          body={data.burnout.recommendation}
          extra={
            data.burnout.signals.length > 0 && (
              <ul className="mt-3 space-y-1">
                {data.burnout.signals.map((s) => (
                  <li key={s} className="text-[10px] text-silver-dim/80 font-mono">
                    · {s}
                  </li>
                ))}
              </ul>
            )
          }
        />
      </div>
    </section>
  );
}

function Card({
  label,
  headline,
  body,
  extra,
}: {
  label: string;
  headline: React.ReactNode;
  body: string;
  extra?: React.ReactNode;
}) {
  return (
    <div className="border border-white/5 rounded-md p-4 bg-white/[0.02]">
      <p className="font-mono text-[9px] tracking-[0.25em] uppercase text-silver-dim">{label}</p>
      <div className="mt-2 text-lg font-serif text-silver">{headline}</div>
      <p className="mt-2 text-xs text-silver-dim leading-relaxed">{body}</p>
      {extra}
    </div>
  );
}
