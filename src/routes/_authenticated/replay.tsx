import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useMemo, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { Nav } from "@/components/nav";
import { getDayReplay, type ReplayEvent } from "@/lib/replay.functions";
import { useSwipe } from "@/hooks/use-swipe";
import { haptic } from "@/lib/haptics";

export const Route = createFileRoute("/_authenticated/replay")({
  head: () => ({
    meta: [
      { title: "Replay — Stack'd" },
      { name: "description", content: "Play back any day of focus, minute by minute." },
    ],
  }),
  component: ReplayPage,
});

function toISODate(d: Date) {
  return d.toISOString().slice(0, 10);
}

function ReplayPage() {
  const load = useServerFn(getDayReplay);
  const [date, setDate] = useState(toISODate(new Date()));
  const [events, setEvents] = useState<ReplayEvent[]>([]);
  const [playing, setPlaying] = useState(false);
  const [cursor, setCursor] = useState(0);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    setCursor(0);
    load({ data: { date } })
      .then((rows) => setEvents(rows as ReplayEvent[]))
      .finally(() => setLoading(false));
  }, [date]);

  useEffect(() => {
    if (!playing) return;
    const id = setInterval(() => {
      setCursor((c) => {
        if (c >= events.length) {
          setPlaying(false);
          return c;
        }
        haptic("tap");
        return c + 1;
      });
    }, 900);
    return () => clearInterval(id);
  }, [playing, events.length]);

  const shift = (days: number) => {
    const d = new Date(date + "T00:00:00Z");
    d.setUTCDate(d.getUTCDate() + days);
    setDate(toISODate(d));
  };

  const swipeRef = useSwipe<HTMLDivElement>({
    onSwipeLeft: () => shift(1),
    onSwipeRight: () => shift(-1),
  });

  const visible = useMemo(() => events.slice(0, cursor || events.length), [events, cursor]);
  const progress = events.length ? (cursor / events.length) * 100 : 0;

  return (
    <div className="min-h-screen bg-obsidian text-silver">
      <Nav />
      <div ref={swipeRef} className="pt-24 max-w-3xl mx-auto px-6 pb-24">
        <h1 className="text-3xl font-serif mb-2">Focus Replay</h1>
        <p className="text-sm text-muted-foreground mb-6">
          Scrub through any day. Swipe left/right on mobile to change days.
        </p>

        <div className="flex items-center gap-3 mb-6">
          <button onClick={() => shift(-1)} className="text-xs font-mono px-3 py-2 border border-white/10 rounded-md hover:border-ember/40">← Prev</button>
          <input
            type="date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            className="bg-black/40 border border-white/10 rounded-md px-3 py-2 text-sm"
          />
          <button onClick={() => shift(1)} className="text-xs font-mono px-3 py-2 border border-white/10 rounded-md hover:border-ember/40">Next →</button>
          <button
            onClick={() => { setPlaying((p) => !p); if (!playing && cursor >= events.length) setCursor(0); }}
            disabled={events.length === 0}
            className="ml-auto btn-ember px-4 py-2 border border-silver/20 rounded-full text-silver text-xs font-mono uppercase tracking-widest disabled:opacity-50"
          >
            {playing ? "Pause" : cursor >= events.length ? "Replay" : "Play"}
          </button>
        </div>

        <div className="h-1 bg-white/5 rounded-full overflow-hidden mb-4">
          <div className="h-full bg-ember transition-all duration-500" style={{ width: `${progress}%` }} />
        </div>

        {/* Per-hour focus heat strip */}
        <div className="mb-8">
          <div className="text-[10px] font-mono uppercase tracking-widest text-muted-foreground mb-2">
            Hourly heat
          </div>
          <div className="grid grid-cols-24 gap-[2px]" style={{ gridTemplateColumns: "repeat(24, minmax(0, 1fr))" }}>
            {Array.from({ length: 24 }).map((_, h) => {
              const inHour = events.filter((e) => new Date(e.t).getHours() === h);
              const focusSec = inHour
                .filter((e) => e.kind === "session")
                .reduce((s, e) => s + (e.duration ?? 0), 0);
              const intensity = Math.min(1, focusSec / 3600);
              return (
                <div
                  key={h}
                  title={`${String(h).padStart(2, "0")}:00 · ${Math.round(focusSec / 60)}m · ${inHour.length} event${inHour.length === 1 ? "" : "s"}`}
                  className="h-6 rounded-[2px] border border-white/5"
                  style={{
                    backgroundColor:
                      intensity === 0
                        ? "rgba(255,255,255,0.03)"
                        : `rgba(240, 169, 104, ${0.15 + intensity * 0.75})`,
                  }}
                />
              );
            })}
          </div>
          <div className="flex justify-between text-[9px] font-mono text-muted-foreground/60 mt-1">
            <span>00</span><span>06</span><span>12</span><span>18</span><span>24</span>
          </div>
        </div>


        {loading ? (
          <div className="text-sm text-muted-foreground text-center py-16">Loading…</div>
        ) : events.length === 0 ? (
          <div className="glass rounded-xl p-12 text-center">
            <div className="text-sm text-muted-foreground">No focus activity on this day.</div>
          </div>
        ) : (
          <ol className="relative border-l border-white/10 ml-3 space-y-4">
            {visible.map((ev, i) => (
              <li key={i} className="ml-6 animate-in fade-in slide-in-from-left-2">
                <span className={`absolute -left-1.5 mt-1.5 size-3 rounded-full ${
                  ev.kind === "session" ? "bg-ember" : ev.kind === "achievement" ? "bg-yellow-400" : "bg-white/40"
                }`} />
                <div className="text-[10px] font-mono uppercase tracking-widest text-muted-foreground">
                  {new Date(ev.t).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })} · {ev.kind}
                </div>
                <div className="text-sm mt-0.5">{ev.label}</div>
              </li>
            ))}
          </ol>
        )}
      </div>
    </div>
  );
}
