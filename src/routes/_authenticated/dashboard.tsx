import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useMemo, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { supabase } from "@/integrations/supabase/client";
import { Nav } from "@/components/nav";
import { MyRoomsPanel } from "@/components/my-rooms-panel";
import { formatDuration, formatHours } from "@/lib/room";
import { tierForScore, TIERS, type Tier } from "@/lib/focus-score";
import {
  recommendNextSession,
  generateDashboardInsights,
  type SessionRecommendation,
  type DashboardInsights,
} from "@/lib/ai.functions";
import { DynamicGreeting } from "@/components/home/dynamic-greeting";
import { CsvExportButton } from "@/components/csv-export-button";
import { DailyRewardCard } from "@/components/rewards/daily-reward-card";
import { AtlasWhisper } from "@/components/atlas-whisper";
import { PrestigeCeremony } from "@/components/profile/prestige-ceremony";


export const Route = createFileRoute("/_authenticated/dashboard")({
  head: () => ({ meta: [{ title: "Analytics — Stack'd" }] }),
  component: Dashboard,
});

interface HistoryRow {
  id: string;
  score: number;
  xp_earned: number;
  duration_seconds: number;
  breaches_count: number;
  tier: string;
  created_at: string;
  room: { id: string; code: string; status: string; started_at: string | null } | null;
}

interface LiveRow {
  id: string;
  code: string;
  started_at: string;
  target_duration_seconds: number;
}

/**
 * Live row isolates its own per-second ticker so the dashboard's history,
 * stat tiles, and grid don't re-render every second alongside the timer.
 */
function LiveSessionRow({ row }: { row: LiveRow }) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(id);
  }, []);
  const elapsed = Math.max(0, Math.floor((now - new Date(row.started_at).getTime()) / 1000));
  const pct = Math.min(100, (elapsed / Math.max(1, row.target_duration_seconds)) * 100);
  return (
    <Link
      to="/room/$code"
      params={{ code: row.code }}
      className="flex items-center gap-4 p-4 rounded-lg border border-white/10 bg-black/30 hover:bg-white/5 transition-colors"
    >
      <span className="size-2 rounded-full bg-pulse animate-pulse" />
      <span className="font-mono text-sm text-muted-foreground">{row.code}</span>
      <div className="flex-1 h-1 bg-white/5 rounded-full overflow-hidden">
        <div className="h-full bg-pulse" style={{ width: `${pct}%` }} />
      </div>
      <span className="font-mono text-sm tabular-nums">{formatDuration(elapsed)}</span>
      <span className="font-mono text-[10px] uppercase tracking-widest text-pulse">LIVE</span>
    </Link>
  );
}

function Dashboard() {
  const [loading, setLoading] = useState(true);
  const [history, setHistory] = useState<HistoryRow[]>([]);
  const [live, setLive] = useState<LiveRow[]>([]);
  const [me, setMe] = useState<{ id: string; name: string; lifetime_xp: number; streak: number } | null>(null);
  const [rec, setRec] = useState<SessionRecommendation | null>(null);
  const [recLoading, setRecLoading] = useState(true);
  const [recError, setRecError] = useState(false);
  const fetchRec = useServerFn(recommendNextSession);

  const [insights, setInsights] = useState<DashboardInsights | null>(null);
  const [insightsLoading, setInsightsLoading] = useState(true);
  const [insightsError, setInsightsError] = useState(false);
  const fetchInsights = useServerFn(generateDashboardInsights);

  const loadRec = () => {
    setRecLoading(true);
    setRecError(false);
    fetchRec({ data: undefined })
      .then((r) => setRec(r))
      .catch(() => setRecError(true))
      .finally(() => setRecLoading(false));
  };

  const loadInsights = () => {
    setInsightsLoading(true);
    setInsightsError(false);
    fetchInsights({ data: undefined })
      .then((r) => setInsights(r))
      .catch(() => setInsightsError(true))
      .finally(() => setInsightsLoading(false));
  };


  useEffect(() => {
    let mounted = true;
    (async () => {
      const { data: u } = await supabase.auth.getUser();
      if (!u.user) return;

      const [{ data: p }, { data: hist }, { data: parts }] = await Promise.all([
        supabase
          .from("profiles")
          .select("display_name, lifetime_xp, current_focus_streak")
          .eq("id", u.user.id)
          .maybeSingle(),
        supabase
          .from("focus_history")
          .select("id, score, xp_earned, duration_seconds, breaches_count, tier, created_at, room:rooms(id, code, status, started_at)")
          .eq("profile_id", u.user.id)
          .order("created_at", { ascending: false })
          .limit(50),
        supabase
          .from("participants")
          .select("room_id, rooms!inner(id, code, status, started_at, target_duration_seconds)")
          .eq("user_id", u.user.id)
          .eq("rooms.status", "active"),
      ]);

      if (!mounted) return;
      setMe({
        id: u.user.id,
        name: p?.display_name ?? "You",
        lifetime_xp: p?.lifetime_xp ?? 0,
        streak: p?.current_focus_streak ?? 0,
      });
      setHistory((hist ?? []) as unknown as HistoryRow[]);
      const liveRows: LiveRow[] = [];
      for (const row of (parts ?? []) as Array<{ rooms: { id: string; code: string; status: string; started_at: string | null; target_duration_seconds: number } }>) {
        const r = row.rooms;
        if (r && r.started_at) {
          liveRows.push({ id: r.id, code: r.code, started_at: r.started_at, target_duration_seconds: r.target_duration_seconds });
        }
      }
      setLive(liveRows);
      setLoading(false);
    })();
    loadRec();
    loadInsights();

    return () => { mounted = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const totalSeconds = useMemo(
    () => history.reduce((acc, h) => acc + (h.duration_seconds ?? 0), 0),
    [history],
  );
  const avgScore = history.length
    ? Math.round(history.reduce((a, h) => a + h.score, 0) / history.length)
    : 0;

  return (
    <div className="min-h-screen bg-obsidian text-silver">
      <Nav />
      <main className="pt-32 pb-20 px-6 max-w-7xl mx-auto">
        <div className="mb-8"><DynamicGreeting /></div>
        <div className="mb-8"><AtlasWhisper context="dashboard" /></div>
        <div className="flex items-end justify-between mb-12">
          <div>
            <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground mb-2">
              ANALYTICS / {me?.name?.toUpperCase()}
            </div>
            <h1 className="text-5xl font-extrabold tracking-tighter">Your discipline.</h1>
          </div>
          <div className="hidden sm:flex gap-3 items-center">
            <CsvExportButton />
            <Link to="/leaderboard" className="px-6 py-3 rounded-lg font-mono text-xs uppercase tracking-widest font-bold border border-white/15 text-silver hover:bg-white/5 transition-all">
              Leaderboard
            </Link>
            <Link to="/start" className="bg-silver text-obsidian px-6 py-3 rounded-lg font-mono text-xs uppercase tracking-widest font-bold hover:invert transition-all">
              New Session
            </Link>
          </div>
        </div>

        <div className="grid md:grid-cols-2 gap-4 mb-8">
          <DailyRewardCard />
          <PrestigeCeremony />
        </div>

        {loading ? (
          <div className="font-mono text-xs text-muted-foreground uppercase tracking-widest">Loading…</div>
        ) : (
          <>
            <div className="grid grid-cols-1 md:grid-cols-4 lg:grid-cols-6 gap-6">
              <div className="md:col-span-4 lg:col-span-4 p-10 bg-white/5 border border-white/10 rounded-2xl flex flex-col justify-between">
                <div>
                  <h3 className="font-mono text-[10px] tracking-[0.3em] text-muted-foreground uppercase mb-12">LIFETIME_PRESENCE</h3>
                  <div className="text-7xl md:text-8xl font-extrabold tracking-tighter">
                    {formatHours(totalSeconds).replace(/[a-z]/g, "")}
                    <span className="text-2xl font-mono text-muted-foreground ml-4 tracking-normal font-normal uppercase">Hours</span>
                  </div>
                </div>
                <div className="mt-12 flex items-end justify-between gap-6">
                  <div>
                    <div className="font-mono text-[10px] tracking-[0.3em] text-muted-foreground uppercase mb-2">LIFETIME_XP</div>
                    <div className="text-3xl font-bold font-mono">{me?.lifetime_xp.toLocaleString() ?? 0}</div>
                  </div>
                  <div className="flex gap-2 flex-1 max-w-md">
                    {[0.1, 0.25, 0.45, 0.7, 1].map((step, i) => (
                      <div
                        key={i}
                        className="h-12 flex-1 rounded-sm"
                        style={{ background: `rgba(226,226,226,${history.length >= (i + 1) * 2 ? step : 0.05})` }}
                      />
                    ))}
                  </div>
                </div>
              </div>

              <div className="md:col-span-2 lg:col-span-2 space-y-6">
                <div className="p-8 bg-white/5 border border-white/10 rounded-2xl">
                  <h3 className="font-mono text-[10px] tracking-[0.3em] text-muted-foreground uppercase mb-4">CURRENT_STREAK</h3>
                  <div className="text-4xl font-bold">{me?.streak ?? 0} <span className="text-base font-mono text-muted-foreground">{me?.streak === 1 ? "Session" : "Sessions"}</span></div>
                </div>
                <div className="p-8 border border-white/5 rounded-2xl flex items-center justify-between">
                  <div>
                    <h3 className="font-mono text-[10px] tracking-[0.3em] text-muted-foreground uppercase mb-1">AVG_SCORE</h3>
                    <div className="text-2xl font-bold">{avgScore}<span className="text-sm text-muted-foreground">/100</span></div>
                  </div>
                  <div
                    className="size-12 rounded-full border-4 flex items-center justify-center font-mono text-[10px]"
                    style={{ borderColor: tierForScore(avgScore).hex, color: tierForScore(avgScore).hex }}
                  >
                    {avgScore >= 95 ? "A+" : avgScore >= 80 ? "A" : avgScore >= 60 ? "B" : "C"}
                  </div>
                </div>
              </div>

              {/* AI-recommended next session — based on focus_history */}
              <div className="md:col-span-6 lg:col-span-3 relative p-8 border border-ember/25 rounded-2xl bg-gradient-to-br from-ember/[0.06] via-transparent to-transparent overflow-hidden">
                <div className="flex items-center justify-between mb-6 flex-wrap gap-3">
                  <div className="flex items-center gap-3">
                    <span className="size-1.5 rounded-full bg-ember animate-pulse" />
                    <h3 className="font-mono text-[10px] tracking-[0.3em] text-ember uppercase">
                      AI / NEXT_SESSION
                    </h3>
                  </div>
                  <button
                    type="button"
                    onClick={loadRec}
                    disabled={recLoading}
                    className="font-mono text-[10px] uppercase tracking-widest text-silver-dim hover:text-ember transition-colors disabled:opacity-50"
                    aria-label="Regenerate recommendation"
                  >
                    {recLoading ? "Thinking…" : "Regenerate →"}
                  </button>
                </div>

                {recLoading && !rec ? (
                  <div className="space-y-4">
                    <div className="h-16 w-32 bg-white/5 rounded animate-pulse" />
                    <div className="h-4 bg-white/5 rounded animate-pulse" />
                    <div className="h-10 w-36 bg-white/5 rounded animate-pulse" />
                  </div>
                ) : recError ? (
                  <div className="font-mono text-xs text-muted-foreground uppercase tracking-widest">
                    Signal lost. <button type="button" onClick={loadRec} className="underline hover:text-ember">Retry</button>
                  </div>
                ) : rec ? (
                  <div className="space-y-5">
                    <div>
                      <div className="text-6xl md:text-7xl font-extrabold tracking-tighter text-silver leading-none">
                        {rec.durationMinutes}
                        <span className="text-2xl font-mono text-muted-foreground ml-2 tracking-normal font-normal">min</span>
                      </div>
                      <div className="font-mono text-[10px] tracking-[0.3em] text-ember mt-3 uppercase">
                        {rec.topic}
                      </div>
                    </div>
                    <p className="text-sm text-silver-dim leading-relaxed text-balance">
                      {rec.rationale}
                    </p>
                    <div className="flex items-center gap-4 font-mono text-[10px] uppercase tracking-widest text-muted-foreground/70">
                      <span>Confidence · <span className="text-silver">{rec.confidence}</span></span>
                      <span className="text-muted-foreground/40">|</span>
                      <span>Basis · <span className="text-silver">{rec.basedOnSessions} {rec.basedOnSessions === 1 ? "session" : "sessions"}</span></span>
                    </div>
                    <Link
                      to="/start"
                      className="btn-ember inline-flex items-center justify-center px-6 py-3 rounded-lg font-mono text-xs uppercase tracking-widest font-bold border border-ember/50 text-silver hover:text-ember whitespace-nowrap"
                    >
                      Start {rec.durationMinutes}m →
                    </Link>
                  </div>
                ) : null}
              </div>

              {/* AI-written personalized insights — sits beside the recommendation card */}
              <div className="md:col-span-6 lg:col-span-3 relative p-8 border border-white/10 rounded-2xl bg-white/[0.02] overflow-hidden">
                <div className="flex items-center justify-between mb-6 flex-wrap gap-3">
                  <div className="flex items-center gap-3">
                    <span className="size-1.5 rounded-full bg-silver animate-pulse" />
                    <h3 className="font-mono text-[10px] tracking-[0.3em] text-silver uppercase">
                      AI / LEDGER_READING
                    </h3>
                  </div>
                  <button
                    type="button"
                    onClick={loadInsights}
                    disabled={insightsLoading}
                    className="font-mono text-[10px] uppercase tracking-widest text-silver-dim hover:text-silver transition-colors disabled:opacity-50"
                    aria-label="Regenerate insights"
                  >
                    {insightsLoading ? "Reading…" : "Regenerate →"}
                  </button>
                </div>

                {insightsLoading && !insights ? (
                  <div className="space-y-3">
                    <div className="h-4 w-3/4 bg-white/5 rounded animate-pulse" />
                    <div className="h-3 bg-white/5 rounded animate-pulse" />
                    <div className="h-3 bg-white/5 rounded animate-pulse" />
                    <div className="h-3 w-5/6 bg-white/5 rounded animate-pulse" />
                    <div className="h-3 bg-white/5 rounded animate-pulse" />
                  </div>
                ) : insightsError ? (
                  <div className="font-mono text-xs text-muted-foreground uppercase tracking-widest">
                    Signal lost. <button type="button" onClick={loadInsights} className="underline hover:text-silver">Retry</button>
                  </div>
                ) : insights ? (
                  <div className="space-y-4">
                    <p className="text-xl font-bold tracking-tight text-silver text-balance">
                      {insights.headline}
                    </p>
                    <div className="space-y-3">
                      {insights.paragraphs.map((p, i) => (
                        <p key={i} className="text-sm text-silver-dim leading-relaxed text-balance">
                          {p}
                        </p>
                      ))}
                    </div>
                    <div className="font-mono text-[10px] uppercase tracking-widest text-muted-foreground/70 pt-2 border-t border-white/5">
                      Basis · <span className="text-silver">{insights.basedOnSessions} {insights.basedOnSessions === 1 ? "session" : "sessions"}</span>
                    </div>
                  </div>
                ) : null}
              </div>




              {live.length > 0 && (
                <div className="md:col-span-6 p-8 bg-white/2 border border-white/10 rounded-2xl">
                  <h3 className="font-mono text-[10px] tracking-[0.3em] text-muted-foreground uppercase mb-6">LIVE_NOW</h3>
                  <div className="space-y-3">
                    {live.map((l) => (
                      <LiveSessionRow key={l.id} row={l} />
                    ))}
                  </div>
                </div>
              )}

              <MyRoomsPanel />

              <div className="md:col-span-6 p-8 bg-white/2 border border-white/5 rounded-2xl">
                <h3 className="font-mono text-[10px] tracking-[0.3em] text-muted-foreground uppercase mb-8">SESSION_HISTORY</h3>
                {history.length === 0 ? (
                  <p className="font-mono text-xs text-muted-foreground uppercase tracking-widest py-8 text-center">
                    No completed sessions yet. <Link to="/start" className="text-silver underline">Start one.</Link>
                  </p>
                ) : (
                  <div className="space-y-0.5">
                    <div className="grid grid-cols-6 py-4 border-b border-white/5 text-[11px] font-mono text-muted-foreground uppercase tracking-widest">
                      <span>Date</span><span>Code</span><span>Duration</span><span>Score</span><span>XP</span><span className="text-right">Tier</span>
                    </div>
                    {history.map((h) => {
                      const tier = TIERS[(h.tier as Tier) in TIERS ? (h.tier as Tier) : tierForScore(h.score).key];
                      const code = h.room?.code ?? "—";
                      const inner = (
                        <>
                          <span className="text-sm">{new Date(h.created_at).toLocaleDateString()}</span>
                          <span className="text-sm font-mono text-muted-foreground">{code}</span>
                          <span className="text-sm font-mono tabular-nums">{formatDuration(h.duration_seconds)}</span>
                          <span className="text-sm font-mono tabular-nums" style={{ color: tier.hex }}>{h.score}</span>
                          <span className="text-sm font-mono tabular-nums text-silver">+{h.xp_earned}</span>
                          <span
                            className="text-right text-[10px] font-mono uppercase tracking-widest"
                            style={{ color: tier.hex }}
                          >
                            {tier.label}
                          </span>
                        </>
                      );
                      return h.room?.code ? (
                        <Link
                          to="/room/$code" params={{ code: h.room.code }}
                          key={h.id}
                          className="grid grid-cols-6 py-4 items-center hover:bg-white/5 rounded transition-colors"
                        >
                          {inner}
                        </Link>
                      ) : (
                        <div key={h.id} className="grid grid-cols-6 py-4 items-center">{inner}</div>
                      );
                    })}
                  </div>
                )}
              </div>
            </div>
          </>
        )}
      </main>
    </div>
  );
}
