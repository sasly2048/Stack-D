import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { Nav } from "@/components/nav";
import { getAnalytics, type AnalyticsPayload } from "@/lib/analytics.functions";
import { ProactivePanel } from "@/components/insights/proactive-panel";
import { GoalForecast } from "@/components/insights/goal-forecast";
import { WeeklyNarrativeCard } from "@/components/insights/weekly-narrative-card";
import { FocusRadar } from "@/components/analytics/focus-radar";
import { AtlasWhisper } from "@/components/atlas-whisper";

export const Route = createFileRoute("/_authenticated/insights")({
  head: () => ({
    meta: [
      { title: "Insights — Stack'd" },
      { name: "description", content: "Trends, tags, and the hours you focus best." },
    ],
  }),
  component: InsightsPage,
});

const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

function InsightsPage() {
  const load = useServerFn(getAnalytics);
  const [data, setData] = useState<AnalyticsPayload | null>(null);

  useEffect(() => {
    load().then(setData);
  }, []);

  if (!data) {
    return (
      <div className="min-h-screen bg-obsidian text-silver">
        <Nav />
        <div className="pt-32 text-center font-mono text-xs text-silver-dim tracking-[0.3em] uppercase">Loading…</div>
      </div>
    );
  }

  const maxHour = Math.max(1, ...data.hourBuckets.map((h) => h.seconds));
  const maxHeat = Math.max(1, ...data.heatmap.map((c) => c.seconds));
  const totalTagSec = data.tagDistribution.reduce((s, t) => s + t.seconds, 0);

  return (
    <div className="min-h-screen bg-obsidian text-silver">
      <Nav />
      <main className="max-w-6xl mx-auto px-6 pt-28 pb-24 space-y-16">
        <header>
          <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Depth</p>
          <h1 className="mt-3 text-4xl md:text-5xl font-serif">Insights</h1>
          <p className="mt-3 text-silver-dim max-w-lg">The shape of your last 120 days.</p>
        </header>

        <section className="grid grid-cols-2 md:grid-cols-4 gap-3">
          <Stat label="Sessions" value={data.totals.sessions.toString()} />
          <Stat label="Hours held" value={data.totals.hours.toString()} />
          <Stat label="Avg score" value={data.totals.avg_score.toString()} />
          <Stat label="XP earned" value={data.totals.xp.toLocaleString()} />
        </section>

        <AtlasWhisper context="insights" />
        <WeeklyNarrativeCard />
        <GoalForecast />
        <ProactivePanel />

        <section>
          <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Focus Radar</p>
          <div className="mt-4 flex justify-center">
            <FocusRadar
              data={{
                consistency: Math.min(100, (data.totals.sessions / 60) * 100),
                deepWork: data.totals.avg_score,
                streak: Math.min(100, data.totals.hours * 2),
                breaks: Math.max(0, 100 - Math.min(100, (data.totals.sessions ? 20 : 0))),
                duration: Math.min(100, (data.totals.hours / 120) * 100),
              }}
              size={260}
            />
          </div>
        </section>


        {(data.best?.hour !== null || data.best?.weekday !== null) && (
          <section className="border border-ember/30 rounded-md p-6 bg-ember/[0.04]">
            <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Signal</p>
            <p className="mt-3 text-lg text-silver max-w-2xl leading-relaxed">
              {data.best?.hour !== null && (
                <>
                  You focus sharpest around{" "}
                  <span className="text-ember font-serif">{formatHour(data.best!.hour!)}</span>.{" "}
                </>
              )}
              {data.best?.weekday !== null && (
                <>
                  Your strongest weekday is{" "}
                  <span className="text-ember font-serif">{WEEKDAYS[data.best!.weekday!]}</span>.
                </>
              )}
            </p>
          </section>
        )}

        <section className="space-y-4">
          <h2 className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">Heatmap · 120 days</h2>
          <div className="grid grid-flow-col grid-rows-7 gap-[3px] w-full overflow-x-auto">
            {data.heatmap.map((c) => {
              const intensity = c.seconds / maxHeat;
              const bg =
                intensity === 0
                  ? "rgba(255,255,255,0.03)"
                  : `color-mix(in oklab, var(--ember) ${Math.max(15, intensity * 100)}%, transparent)`;
              return (
                <div
                  key={c.date}
                  title={`${c.date} · ${Math.round(c.seconds / 60)} min · ${c.sessions} sessions`}
                  className="size-[10px] md:size-[12px] rounded-[2px]"
                  style={{ background: bg }}
                />
              );
            })}
          </div>
        </section>

        <section className="space-y-4">
          <h2 className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">
            Focus by hour
          </h2>
          <div className="flex items-end gap-1 h-40 border-b border-white/5">
            {data.hourBuckets.map((h) => {
              const pct = (h.seconds / maxHour) * 100;
              return (
                <div
                  key={h.hour}
                  className="flex-1 bg-ember/70 hover:bg-ember transition-colors rounded-t-sm"
                  style={{ height: `${Math.max(pct, h.seconds ? 2 : 0)}%` }}
                  title={`${formatHour(h.hour)} · ${Math.round(h.seconds / 60)} min`}
                />
              );
            })}
          </div>
          <div className="flex justify-between font-mono text-[9px] tracking-[0.2em] uppercase text-silver-dim/60">
            <span>00</span>
            <span>06</span>
            <span>12</span>
            <span>18</span>
            <span>24</span>
          </div>
        </section>

        {data.tagDistribution.length > 0 && (
          <section className="space-y-4">
            <h2 className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">
              Distribution by tag
            </h2>
            <ul className="space-y-2">
              {data.tagDistribution.map((t) => {
                const pct = Math.round((t.seconds / totalTagSec) * 100);
                return (
                  <li key={t.tag} className="space-y-1">
                    <div className="flex justify-between text-sm">
                      <span className="text-silver">#{t.tag}</span>
                      <span className="font-mono text-[10px] tracking-[0.2em] uppercase text-silver-dim">
                        {pct}% · {Math.round(t.seconds / 60)} min
                      </span>
                    </div>
                    <div className="h-1 bg-white/5 rounded-full overflow-hidden">
                      <div className="h-full bg-ember" style={{ width: `${pct}%` }} />
                    </div>
                  </li>
                );
              })}
            </ul>
          </section>
        )}
      </main>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="glass rounded-md px-4 py-4">
      <p className="font-mono text-[9px] tracking-[0.25em] uppercase text-silver-dim">{label}</p>
      <p className="mt-1 text-2xl font-serif text-silver">{value}</p>
    </div>
  );
}

function formatHour(h: number) {
  return `${h.toString().padStart(2, "0")}:00`;
}
