import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { getForecast, type ForecastPayload } from "@/lib/forecast.functions";

export function GoalForecast() {
  const load = useServerFn(getForecast);
  const [data, setData] = useState<ForecastPayload | null>(null);

  useEffect(() => {
    load().then(setData).catch(() => undefined);
  }, []);

  if (!data) return null;

  return (
    <section className="border border-white/10 rounded-md p-6 bg-black/30">
      <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Forecast</p>
      <h2 className="mt-3 text-2xl font-serif text-silver">You'll hit these next</h2>
      <div className="mt-3 grid grid-cols-3 gap-3 text-xs text-silver-dim">
        <div>Avg / day · <span className="text-silver">{data.avgDailyMinutes}m</span></div>
        <div>Weekly · <span className="text-silver">{Math.round(data.weeklyForecastMinutes / 60)}h</span></div>
        <div>Monthly · <span className="text-silver">{Math.round(data.monthlyForecastMinutes / 60)}h</span></div>
      </div>
      {data.projections.length === 0 ? (
        <p className="mt-6 text-sm text-silver-dim">
          Focus consistently for 30 days to unlock forecasts.
        </p>
      ) : (
        <div className="mt-6 space-y-3">
          {data.projections.map((p) => (
            <div key={p.targetXp} className="flex items-center justify-between border-b border-white/5 pb-3">
              <div>
                <p className="font-serif text-lg text-silver">{p.label}</p>
                <p className="text-[11px] font-mono text-silver-dim uppercase tracking-wider">
                  ETA · {p.etaDate} · {p.daysNeeded} days
                </p>
              </div>
              <div className="text-right">
                <p className="font-mono text-xs text-ember">
                  {Math.max(0, p.targetXp - data.currentXp).toLocaleString()} XP to go
                </p>
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
