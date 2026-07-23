import { createFileRoute, Link, useRouter } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { Virtuoso } from "react-virtuoso";
import { Nav } from "@/components/nav";
import { listTimeline, type TimelineSession } from "@/lib/session-interactions.functions";
import { SessionReactionBar } from "@/components/session-reaction-bar";
import { getProactiveInsights, type ProactiveInsight } from "@/lib/proactive-ai.functions";
import { Button } from "@/components/ui/button";

export const Route = createFileRoute("/_authenticated/timeline")({
  head: () => ({
    meta: [
      { title: "Timeline · Stack'd" },
      { name: "description", content: "Chronological record of your focus sessions with reactions from friends." },
      { property: "og:title", content: "Timeline · Stack'd" },
      { property: "og:description", content: "Chronological record of your focus sessions with reactions from friends." },
    ],
  }),
  errorComponent: ({ reset }) => {
    const r = useRouter();
    return (
      <div className="min-h-screen bg-obsidian text-silver flex items-center justify-center">
        <div className="text-center space-y-4">
          <p className="text-sm text-muted-foreground">Couldn&rsquo;t load timeline.</p>
          <Button onClick={() => { r.invalidate(); reset(); }}>Retry</Button>
        </div>
      </div>
    );
  },
  notFoundComponent: () => <div className="p-8 text-silver">Not found.</div>,
  component: TimelinePage,
});

function fmtDuration(sec: number) {
  const m = Math.floor(sec / 60);
  if (m < 60) return `${m}m`;
  return `${Math.floor(m / 60)}h ${m % 60}m`;
}

function fmtDate(iso: string) {
  return new Date(iso).toLocaleString(undefined, {
    month: "short", day: "numeric", hour: "numeric", minute: "2-digit",
  });
}

const TIER_COLOR: Record<string, string> = {
  flow: "text-ember border-ember/40",
  pristine: "text-silver border-white/20",
  steady: "text-silver-dim border-white/10",
  fragmented: "text-yellow-500/70 border-yellow-500/20",
  compromised: "text-breach border-breach/30",
};

function TimelinePage() {
  const [items, setItems] = useState<TimelineSession[]>([]);
  const [loading, setLoading] = useState(true);
  const [hasMore, setHasMore] = useState(true);
  const [ai, setAi] = useState<ProactiveInsight | null>(null);
  const fetchList = useServerFn(listTimeline);
  const fetchAI = useServerFn(getProactiveInsights);

  useEffect(() => {
    let alive = true;
    Promise.all([fetchList({ data: { limit: 20 } }), fetchAI()])
      .then(([rows, insight]) => {
        if (!alive) return;
        setItems(rows);
        setAi(insight);
        setHasMore(rows.length === 20);
      })
      .catch(() => {})
      .finally(() => alive && setLoading(false));
    return () => { alive = false; };
  }, [fetchList, fetchAI]);

  async function loadMore() {
    if (!items.length) return;
    const before = items[items.length - 1].created_at;
    const more = await fetchList({ data: { limit: 20, before } });
    setItems((prev) => [...prev, ...more]);
    setHasMore(more.length === 20);
  }

  return (
    <div className="min-h-screen bg-obsidian text-silver">
      <Nav />
      <main className="max-w-3xl mx-auto px-6 pt-24 pb-16 space-y-8">
        <div>
          <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground">
            RECORD / SESSIONS
          </div>
          <h1 className="text-4xl font-bold tracking-tight mt-1">Timeline</h1>
        </div>

        {ai && <ProactiveCard ai={ai} />}

        {loading && <div className="text-sm text-muted-foreground">Loading…</div>}
        {!loading && items.length === 0 && (
          <div className="text-sm text-muted-foreground">No sessions yet. Start one to write your first line.</div>
        )}
        {!loading && items.length > 0 && (
          <Virtuoso
            useWindowScroll
            data={items}
            endReached={() => { if (hasMore) loadMore(); }}
            itemContent={(_, s) => (
              <div className="relative border-l border-white/10 pl-6 pb-6">
                <span className={`absolute -left-[5px] top-2 size-2 rounded-full ${s.tier === "flow" ? "bg-ember" : s.tier === "compromised" ? "bg-breach" : "bg-white/30"}`} />
                <div className={`border rounded-lg p-4 space-y-3 bg-white/[0.02] ${TIER_COLOR[s.tier] ?? "border-white/10"}`}>
                  <div className="flex items-baseline justify-between gap-3">
                    <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground">{fmtDate(s.created_at)}</div>
                    <div className={`font-mono text-[10px] tracking-widest uppercase ${TIER_COLOR[s.tier]?.split(" ")[0] ?? ""}`}>{s.tier}</div>
                  </div>
                  <div className="flex flex-wrap items-baseline gap-x-4 gap-y-1 text-sm">
                    <span className="text-silver font-mono">{s.score}<span className="text-muted-foreground">/100</span></span>
                    <span className="text-silver-dim font-mono">{fmtDuration(s.duration_seconds)}</span>
                    <span className="text-silver-dim font-mono">+{s.xp_earned} XP</span>
                    {s.breaches_count > 0 && (
                      <span className="text-breach font-mono">{s.breaches_count} breach{s.breaches_count > 1 ? "es" : ""}</span>
                    )}
                  </div>
                  {s.notes && <p className="text-xs text-silver-dim italic">&ldquo;{s.notes}&rdquo;</p>}
                  {s.tags && s.tags.length > 0 && (
                    <div className="flex flex-wrap gap-1">
                      {s.tags.map((t) => (
                        <span key={t} className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-white/5 border border-white/10 text-silver-dim">
                          #{t}
                        </span>
                      ))}
                    </div>
                  )}
                  <SessionReactionBar sessionId={s.id} reactions={s.reactions} />
                </div>
              </div>
            )}
          />
        )}

        {hasMore && !loading && items.length > 0 && (
          <div className="text-center">
            <Button onClick={loadMore} variant="outline" className="border-white/10">
              Load older
            </Button>
          </div>
        )}

        <div className="text-center pt-4">
          <Link to="/dashboard" className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground hover:text-ember">
            ← ANALYTICS
          </Link>
        </div>
      </main>
    </div>
  );
}

function ProactiveCard({ ai }: { ai: ProactiveInsight }) {
  const riskColor =
    ai.burnout.risk === "high" ? "text-breach border-breach/40"
    : ai.burnout.risk === "medium" ? "text-yellow-500 border-yellow-500/30"
    : "text-ember border-ember/30";

  return (
    <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
      <div className="border border-white/10 rounded-lg p-4">
        <div className="font-mono text-[9px] tracking-[0.3em] uppercase text-muted-foreground mb-2">
          SMART SCHEDULE
        </div>
        {ai.smartSchedule ? (
          <>
            <div className="text-2xl font-bold text-ember font-mono">{ai.smartSchedule.label}</div>
            <p className="text-[11px] text-silver-dim mt-1">{ai.smartSchedule.rationale}</p>
          </>
        ) : (
          <p className="text-xs text-muted-foreground">Need more sessions to pattern-match.</p>
        )}
      </div>
      <div className="border border-white/10 rounded-lg p-4">
        <div className="font-mono text-[9px] tracking-[0.3em] uppercase text-muted-foreground mb-2">
          FOCUS PREDICTION
        </div>
        <div className="text-2xl font-bold text-silver font-mono">
          {ai.focusPrediction.nextScore}
          <span className="text-xs text-muted-foreground ml-1">/100</span>
        </div>
        <p className="text-[11px] text-silver-dim mt-1">{ai.focusPrediction.note}</p>
        <p className="text-[9px] font-mono uppercase tracking-widest text-muted-foreground mt-2">
          confidence · {ai.focusPrediction.confidence}
        </p>
      </div>
      <div className={`border rounded-lg p-4 ${riskColor}`}>
        <div className="font-mono text-[9px] tracking-[0.3em] uppercase text-muted-foreground mb-2">
          BURNOUT · {ai.burnout.risk}
        </div>
        <p className="text-[11px] text-silver-dim">{ai.burnout.recommendation}</p>
        {ai.burnout.signals.length > 0 && (
          <ul className="mt-2 space-y-0.5 text-[10px] font-mono text-muted-foreground">
            {ai.burnout.signals.map((s) => <li key={s}>· {s}</li>)}
          </ul>
        )}
      </div>
    </div>
  );
}
