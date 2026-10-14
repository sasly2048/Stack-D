import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useMemo, useState } from "react";
import { supabase } from "@/integrations/supabase/client";
import { Nav } from "@/components/nav";
import { MyRoomsPanel } from "@/components/my-rooms-panel";
import { formatDuration, formatHours } from "@/lib/room";
import { tierForScore, TIERS, type Tier } from "@/lib/focus-score";
import { CsvExportButton } from "@/components/csv-export-button";
import { DailyRewardCard } from "@/components/rewards/daily-reward-card";
import { StackArt, EmberRing } from "@/components/stack-art";
import { UpgradeCard } from "@/components/premium/upgrade-card";
import { useXpSync } from "@/lib/xp-sync";
import { AtlasWhisper } from "@/components/atlas-whisper";
import { PrestigeCeremony } from "@/components/profile/prestige-ceremony";
import { EmptyState, Skeleton, LoadingAnnouncer } from "@/components/query-states";
import { BadgeHint } from "@/components/ui/badge-hint";
import { Button } from "@/components/ui/button";

export const Route = createFileRoute("/_authenticated/dashboard")({
  head: () => ({
    meta: [
      { title: "Analytics — Stack'd" },
      {
        name: "description",
        content:
          "Your Stack'd analytics: focus history, session scores, breaches, streaks and XP earned over time.",
      },
      { property: "og:title", content: "Analytics — Stack'd" },
      {
        property: "og:description",
        content: "Focus history, session scores, streaks and XP earned on Stack'd.",
      },
      { name: "robots", content: "noindex" },
    ],
  }),
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
  const [me, setMe] = useState<{
    id: string;
    name: string;
    lifetime_xp: number;
    streak: number;
    freezes: number;
    lastDay: string | null;
  } | null>(null);
  const refreshProgress = async () => {
    const { data: u } = await supabase.auth.getUser();
    if (!u.user) return;
    const { data: p } = await supabase
      .from("profiles")
      .select("display_name, lifetime_xp, current_focus_streak, streak_freezes, streak_last_day")
      .eq("id", u.user.id)
      .maybeSingle();
    setMe((prev) =>
      prev
        ? {
            ...prev,
            name: p?.display_name ?? prev.name,
            lifetime_xp: p?.lifetime_xp ?? prev.lifetime_xp,
            streak: p?.current_focus_streak ?? prev.streak,
            freezes: p?.streak_freezes ?? prev.freezes,
            lastDay: p?.streak_last_day ?? prev.lastDay,
          }
        : prev,
    );
  };

  useXpSync(() => {
    void refreshProgress();
  });

  useEffect(() => {
    let mounted = true;
    (async () => {
      const { data: u } = await supabase.auth.getUser();
      if (!u.user) return;

      const [{ data: p }, { data: hist }, { data: parts }] = await Promise.all([
        supabase
          .from("profiles")
          .select(
            "display_name, lifetime_xp, current_focus_streak, streak_freezes, streak_last_day",
          )
          .eq("id", u.user.id)
          .maybeSingle(),
        supabase
          .from("focus_history")
          .select(
            "id, score, xp_earned, duration_seconds, breaches_count, tier, created_at, room:rooms(id, code, status, started_at)",
          )
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
        freezes: p?.streak_freezes ?? 0,
        lastDay: p?.streak_last_day ?? null,
      });
      setHistory((hist ?? []) as unknown as HistoryRow[]);
      const liveRows: LiveRow[] = [];
      for (const row of (parts ?? []) as Array<{
        rooms: {
          id: string;
          code: string;
          status: string;
          started_at: string | null;
          target_duration_seconds: number;
        };
      }>) {
        const r = row.rooms;
        if (r && r.started_at) {
          liveRows.push({
            id: r.id,
            code: r.code,
            started_at: r.started_at,
            target_duration_seconds: r.target_duration_seconds,
          });
        }
      }
      setLive(liveRows);
      setLoading(false);
    })();

    return () => {
      mounted = false;
    };
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
    <div className="min-h-dvh bg-obsidian text-silver">
      <Nav />
      <main className="app-page max-w-7xl">
        <Launcher
          name={me?.name ?? ""}
          history={history}
          streak={me?.streak ?? 0}
          freezes={me?.freezes ?? 0}
          lastDay={me?.lastDay ?? null}
        />
        <div className="mb-8">
          <DailyRewardCard />
        </div>
        <div className="mb-8">
          <AtlasWhisper context="dashboard" />
        </div>
        <div className="mb-10 grid grid-cols-1 items-end gap-5 lg:grid-cols-[minmax(0,1fr)_auto] lg:gap-8 lg:mb-12">
          <div className="min-w-0">
            <div className="ritual-label mb-2 text-muted-foreground">
              ANALYTICS / {me?.name?.toUpperCase()}
            </div>
            <h2 className="font-serif text-3xl text-silver">Your discipline.</h2>
          </div>
          <div className="hidden shrink-0 items-center gap-3 sm:flex">
            <CsvExportButton />
          </div>
        </div>

        <div className="mb-4">
          <UpgradeCard />
        </div>

        <div className="grid md:grid-cols-2 gap-4 mb-8">
          <PrestigeCeremony />
        </div>

        {loading ? (
          // Sized to the real grid below, so the page doesn't jump a full
          // screen-height when the data lands.
          <>
            <LoadingAnnouncer label="Loading your analytics" />
            <div className="grid grid-cols-1 md:grid-cols-4 lg:grid-cols-6 gap-6">
              <Skeleton className="md:col-span-4 lg:col-span-4 h-[22rem]" />
              <div className="md:col-span-2 lg:col-span-2 space-y-6">
                <Skeleton className="h-40" />
                <Skeleton className="h-[7.5rem]" />
              </div>
              <Skeleton className="md:col-span-6 lg:col-span-3 h-80" />
              <Skeleton className="md:col-span-6 lg:col-span-3 h-80" />
              <Skeleton className="md:col-span-6 h-64" />
            </div>
          </>
        ) : (
          <>
            <div className="grid grid-cols-1 md:grid-cols-4 lg:grid-cols-6 gap-6">
              {/* A wall of zeros looks like the app is broken rather than new.
                  Before the first session the honest widget is the invitation. */}
              {history.length === 0 ? (
                <div className="md:col-span-6">
                  <EmptyState
                    title="Your first session writes the first line"
                    description="Nothing has been measured yet. Open a room, stack your phone and the ledger starts filling itself."
                    action={
                      <Link
                        to="/start"
                        className="bg-silver text-obsidian px-6 py-3 rounded-lg font-mono text-xs uppercase tracking-widest font-bold hover:invert active:scale-[0.99] transition-all duration-200 ease-[var(--ease-ritual)] inline-flex focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember focus-visible:ring-offset-2 focus-visible:ring-offset-obsidian"
                      >
                        Start your first session →
                      </Link>
                    }
                  />
                </div>
              ) : (
                <>
                  <div className="panel flex flex-col justify-between p-5 transition-colors hover:border-white/20 hover:bg-white/[0.07] sm:p-8 lg:p-10 md:col-span-4 lg:col-span-4">
                    <div>
                      <h3 className="font-mono text-[10px] tracking-[0.3em] text-muted-foreground uppercase mb-12">
                        LIFETIME_PRESENCE
                      </h3>
                      <div className="flex flex-wrap items-baseline gap-x-3 text-5xl font-extrabold leading-none sm:text-7xl md:text-8xl">
                        {formatHours(totalSeconds).replace(/[a-z]/g, "")}
                        <span className="font-mono text-base font-normal uppercase text-muted-foreground sm:text-2xl">
                          Hours
                        </span>
                      </div>
                    </div>
                    <div className="mt-10 grid grid-cols-[auto_minmax(0,1fr)] items-end gap-4 sm:gap-6">
                      <div>
                        <div className="font-mono text-[10px] tracking-[0.3em] text-muted-foreground uppercase mb-2">
                          LIFETIME_XP
                        </div>
                        <div className="text-3xl font-bold font-mono">
                          {me?.lifetime_xp.toLocaleString() ?? 0}
                        </div>
                      </div>
                      <div className="flex gap-2 flex-1 max-w-md">
                        {[0.1, 0.25, 0.45, 0.7, 1].map((step, i) => (
                          <div
                            key={i}
                            className="h-12 flex-1 rounded-sm"
                            style={{
                              background: `rgba(226,226,226,${history.length >= (i + 1) * 2 ? step : 0.05})`,
                            }}
                          />
                        ))}
                      </div>
                    </div>
                  </div>

                  <div className="md:col-span-2 lg:col-span-2 space-y-6">
                    <div className="p-8 bg-white/5 border border-white/10 rounded-2xl hover:bg-white/[0.07] hover:border-white/20 transition-all duration-200 ease-[var(--ease-ritual)]">
                      <h3 className="font-mono text-[10px] tracking-[0.3em] text-muted-foreground uppercase mb-4">
                        CURRENT_STREAK
                      </h3>
                      <div className="text-4xl font-bold tabular-nums">
                        {me?.streak ?? 0}{" "}
                        <span className="text-base font-mono text-muted-foreground">
                          {me?.streak === 1 ? "Day" : "Days"}
                        </span>
                      </div>
                      <StreakNote
                        streak={me?.streak ?? 0}
                        freezes={me?.freezes ?? 0}
                        lastDay={me?.lastDay ?? null}
                      />
                    </div>
                    <div className="p-8 border border-white/5 rounded-2xl flex items-center justify-between hover:bg-white/[0.04] hover:border-white/15 transition-all duration-200 ease-[var(--ease-ritual)]">
                      <div>
                        <h3 className="font-mono text-[10px] tracking-[0.3em] text-muted-foreground uppercase mb-1">
                          AVG_SCORE
                        </h3>
                        <div className="text-2xl font-bold">
                          {avgScore}
                          <span className="text-sm text-muted-foreground">/100</span>
                        </div>
                      </div>
                      <div
                        className="size-12 rounded-full border-4 flex items-center justify-center font-mono text-[10px]"
                        style={{
                          borderColor: tierForScore(avgScore).hex,
                          color: tierForScore(avgScore).hex,
                        }}
                      >
                        {avgScore >= 95 ? "A+" : avgScore >= 80 ? "A" : avgScore >= 60 ? "B" : "C"}
                      </div>
                    </div>
                  </div>
                </>
              )}

              {live.length > 0 && (
                <div className="md:col-span-6 p-8 bg-white/2 border border-white/10 rounded-2xl">
                  <h3 className="font-mono text-[10px] tracking-[0.3em] text-muted-foreground uppercase mb-6">
                    LIVE_NOW
                  </h3>
                  <div className="space-y-3">
                    {live.map((l) => (
                      <LiveSessionRow key={l.id} row={l} />
                    ))}
                  </div>
                </div>
              )}

              <MyRoomsPanel />

              <div className="md:col-span-6 p-5 sm:p-8 bg-white/2 border border-white/5 rounded-2xl">
                <h3 className="font-mono text-[10px] tracking-[0.3em] text-muted-foreground uppercase mb-8">
                  SESSION_HISTORY
                </h3>
                {history.length === 0 ? (
                  <p className="font-mono text-xs text-muted-foreground uppercase tracking-widest py-8 text-center">
                    No completed sessions yet.{" "}
                    <Link to="/start" className="text-silver underline">
                      Start one.
                    </Link>
                  </p>
                ) : (
                  <div className="space-y-0.5">
                    <div className="hidden sm:grid grid-cols-6 py-4 border-b border-white/5 text-[11px] font-mono text-muted-foreground uppercase tracking-widest">
                      <span>Date</span>
                      <span>Code</span>
                      <span>Duration</span>
                      <span>Score</span>
                      <span>XP</span>
                      <span className="text-right">Tier</span>
                    </div>
                    {history.map((h) => {
                      const tier =
                        TIERS[
                          (h.tier as Tier) in TIERS ? (h.tier as Tier) : tierForScore(h.score).key
                        ];
                      const code = h.room?.code ?? "—";
                      const inner = (
                        <>
                          <span className="text-sm">
                            {new Date(h.created_at).toLocaleDateString()}
                          </span>
                          <span className="text-sm font-mono text-muted-foreground max-sm:text-right">
                            {code}
                          </span>
                          <span className="text-sm font-mono tabular-nums">
                            {formatDuration(h.duration_seconds)}
                          </span>
                          <span
                            className="text-sm font-mono tabular-nums max-sm:text-right"
                            style={{ color: tier.hex }}
                          >
                            {h.score}
                          </span>
                          <span className="text-sm font-mono tabular-nums text-silver">
                            +{h.xp_earned}
                          </span>
                          <span
                            className="flex items-center justify-end gap-2 text-right text-[10px] font-mono uppercase tracking-widest"
                            style={{ color: tier.hex }}
                          >
                            {tier.label}
                            {/* Real earn time, straight off the row — no
                                achievement table is loaded here, so this is the
                                only "recently earned" the dashboard can honestly
                                claim. */}
                            {Date.now() - new Date(h.created_at).getTime() < 86_400_000 && (
                              <BadgeHint tone="positive" title="Earned in the last 24 hours">
                                New
                              </BadgeHint>
                            )}
                          </span>
                        </>
                      );
                      return h.room?.code ? (
                        <Link
                          to="/room/$code"
                          params={{ code: h.room.code }}
                          key={h.id}
                          className="grid grid-cols-2 gap-y-1 border-b border-white/5 py-3 items-center last:border-0 sm:grid-cols-6 sm:gap-y-0 sm:border-0 sm:py-4 hover:bg-white/5 rounded transition-colors"
                        >
                          {inner}
                        </Link>
                      ) : (
                        <div
                          key={h.id}
                          className="grid grid-cols-2 gap-y-1 border-b border-white/5 py-3 items-center last:border-0 sm:grid-cols-6 sm:gap-y-0 sm:border-0 sm:py-4"
                        >
                          {inner}
                        </div>
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

/**
 * The day after a miss is designed (daily streak, earned freeze): shows a held
 * freeze, or after 5pm that a live streak ends at midnight if nothing clean
 * lands today. Mirrors Android's Home streak line.
 */
function StreakNote({
  streak,
  freezes,
  lastDay,
}: {
  streak: number;
  freezes: number;
  lastDay: string | null;
}) {
  const now = new Date();
  const yesterday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1);
  const ymd = `${yesterday.getFullYear()}-${String(yesterday.getMonth() + 1).padStart(2, "0")}-${String(yesterday.getDate()).padStart(2, "0")}`;
  const atRisk = streak > 0 && lastDay === ymd && now.getHours() >= 17;
  if (atRisk)
    return <p className="mt-3 text-sm text-red-400">Ends at midnight. One clean stack keeps it.</p>;
  if (streak > 0 && freezes > 0)
    return (
      <p className="mt-3 text-sm text-muted-foreground">
        1 freeze held: a missed day won't break it.
      </p>
    );
  return (
    <p className="mt-3 text-sm text-muted-foreground">3 clean stacks in a week earn a freeze.</p>
  );
}

const DAILY_GOAL_MIN = 60;

/**
 * Home as a launcher, not a letter (Android parity): one greeting line,
 * today's goal as an ember ring around the breathing stack, and the one
 * action the app exists for.
 */
function Launcher({
  name,
  history,
  streak,
  freezes,
  lastDay,
}: {
  name: string;
  history: HistoryRow[];
  streak: number;
  freezes: number;
  lastDay: string | null;
}) {
  const now = new Date();
  const h = now.getHours();
  const part =
    h < 5 ? "Late night" : h < 12 ? "Morning" : h < 17 ? "Afternoon" : h < 21 ? "Evening" : "Night";
  const first = name.split(" ")[0] || "you";
  const todayMin = Math.round(
    history
      .filter((r) => new Date(r.created_at).toDateString() === now.toDateString())
      .reduce((a, r) => a + (r.duration_seconds ?? 0), 0) / 60,
  );
  const y = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1);
  const yKey = `${y.getFullYear()}-${String(y.getMonth() + 1).padStart(2, "0")}-${String(y.getDate()).padStart(2, "0")}`;
  const atRisk = streak > 0 && lastDay === yKey && h >= 17;
  const streakLine = atRisk
    ? `${streak}-day streak ends at midnight`
    : streak > 0 && freezes > 0
      ? `${streak}-day streak · 1 freeze`
      : streak > 0
        ? `${streak}-day streak`
        : "Day one";
  return (
    <section className="mb-10">
      <p className="text-sm text-muted-foreground">
        {now.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })} ·{" "}
        {now.toLocaleDateString([], { weekday: "long" })}
      </p>
      <h1 className="mt-1 font-serif text-4xl text-silver sm:text-5xl">
        {part}, {first}.
      </h1>
      <div className="relative mx-auto my-8 grid size-[260px] place-items-center">
        <div
          aria-hidden="true"
          className="absolute inset-0 rounded-full"
          style={{ background: "radial-gradient(circle, rgba(240,169,104,0.16), transparent 70%)" }}
        />
        <EmberRing progress={todayMin / DAILY_GOAL_MIN} size={240} />
        <StackArt phones={1} size={130} breathing />
      </div>
      <div className="flex flex-wrap items-end justify-between gap-3">
        <p className="text-muted-foreground">
          <span className="font-serif text-5xl tabular-nums text-silver">{todayMin}</span> of{" "}
          {DAILY_GOAL_MIN} min today
        </p>
        <p className={`pb-2 text-sm ${atRisk ? "text-red-400" : "text-muted-foreground"}`}>
          {streakLine}
        </p>
      </div>
      <Link
        to="/start"
        className="mt-6 flex w-full items-center justify-between rounded-full bg-silver py-3 pl-7 pr-3 text-lg font-semibold text-obsidian transition-transform duration-200 ease-[var(--ease-ritual)] active:scale-[0.98] sm:max-w-md"
      >
        Start a Stack
        <span className="grid size-11 place-items-center rounded-full bg-gradient-to-br from-[#FFC48A] to-[#F0A968]">
          ▶
        </span>
      </Link>
    </section>
  );
}
