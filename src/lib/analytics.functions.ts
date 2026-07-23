import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";

export type DayCell = { date: string; sessions: number; seconds: number; score_avg: number };

export type AnalyticsPayload = {
  heatmap: DayCell[]; // last 120 days
  hourBuckets: Array<{ hour: number; seconds: number; sessions: number; score_avg: number }>;
  tagDistribution: Array<{ tag: string; seconds: number; sessions: number }>;
  breachByTier: Array<{ tier: string; breaches: number; sessions: number }>;
  best: { hour: number | null; weekday: number | null } | null;
  totals: { sessions: number; hours: number; xp: number; avg_score: number };
};

const DAYS = 120;

export const getAnalytics = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<AnalyticsPayload> => {
    const { supabase, userId } = context;
    const since = new Date(Date.now() - DAYS * 24 * 60 * 60 * 1000).toISOString();

    const { data, error } = await supabase
      .from("focus_history")
      .select("score, xp_earned, duration_seconds, breaches_count, tier, tags, created_at")
      .eq("profile_id", userId)
      .gte("created_at", since)
      .order("created_at", { ascending: true });
    if (error) throw new Error(error.message);

    const rows = data ?? [];
    // Heatmap
    const map = new Map<string, { seconds: number; sessions: number; score_sum: number }>();
    for (let i = 0; i < DAYS; i++) {
      const d = new Date();
      d.setUTCDate(d.getUTCDate() - (DAYS - 1 - i));
      map.set(d.toISOString().slice(0, 10), { seconds: 0, sessions: 0, score_sum: 0 });
    }
    for (const r of rows) {
      const key = r.created_at.slice(0, 10);
      const cell = map.get(key);
      if (!cell) continue;
      cell.seconds += r.duration_seconds;
      cell.sessions += 1;
      cell.score_sum += r.score;
    }
    const heatmap: DayCell[] = Array.from(map.entries()).map(([date, v]) => ({
      date,
      sessions: v.sessions,
      seconds: v.seconds,
      score_avg: v.sessions ? Math.round(v.score_sum / v.sessions) : 0,
    }));

    // Hour buckets
    const hourAgg: Record<number, { seconds: number; sessions: number; score_sum: number }> = {};
    const dayAgg: Record<number, { sessions: number; score_sum: number }> = {};
    for (let h = 0; h < 24; h++) hourAgg[h] = { seconds: 0, sessions: 0, score_sum: 0 };
    for (let d = 0; d < 7; d++) dayAgg[d] = { sessions: 0, score_sum: 0 };
    for (const r of rows) {
      const dt = new Date(r.created_at);
      const h = dt.getUTCHours();
      const wd = dt.getUTCDay();
      hourAgg[h].seconds += r.duration_seconds;
      hourAgg[h].sessions += 1;
      hourAgg[h].score_sum += r.score;
      dayAgg[wd].sessions += 1;
      dayAgg[wd].score_sum += r.score;
    }
    const hourBuckets = Object.entries(hourAgg).map(([hour, v]) => ({
      hour: Number(hour),
      seconds: v.seconds,
      sessions: v.sessions,
      score_avg: v.sessions ? Math.round(v.score_sum / v.sessions) : 0,
    }));

    // Tag distribution
    const tagAgg = new Map<string, { seconds: number; sessions: number }>();
    for (const r of rows) {
      for (const t of r.tags ?? []) {
        const cur = tagAgg.get(t) ?? { seconds: 0, sessions: 0 };
        cur.seconds += r.duration_seconds;
        cur.sessions += 1;
        tagAgg.set(t, cur);
      }
    }
    const tagDistribution = Array.from(tagAgg.entries())
      .map(([tag, v]) => ({ tag, ...v }))
      .sort((a, b) => b.seconds - a.seconds)
      .slice(0, 8);

    // Breach by tier
    const tierAgg = new Map<string, { breaches: number; sessions: number }>();
    for (const r of rows) {
      const cur = tierAgg.get(r.tier) ?? { breaches: 0, sessions: 0 };
      cur.breaches += r.breaches_count;
      cur.sessions += 1;
      tierAgg.set(r.tier, cur);
    }

    // Best hour / weekday by avg score, min 3 sessions
    let bestHour: number | null = null;
    let bestHourScore = -1;
    for (const h of hourBuckets) {
      if (h.sessions >= 3 && h.score_avg > bestHourScore) {
        bestHour = h.hour;
        bestHourScore = h.score_avg;
      }
    }
    let bestDay: number | null = null;
    let bestDayScore = -1;
    for (const [wd, v] of Object.entries(dayAgg)) {
      if (v.sessions >= 3) {
        const avg = v.score_sum / v.sessions;
        if (avg > bestDayScore) {
          bestDay = Number(wd);
          bestDayScore = avg;
        }
      }
    }

    const totalSeconds = rows.reduce((s, r) => s + r.duration_seconds, 0);
    const totalXp = rows.reduce((s, r) => s + r.xp_earned, 0);
    const avgScore = rows.length ? Math.round(rows.reduce((s, r) => s + r.score, 0) / rows.length) : 0;

    return {
      heatmap,
      hourBuckets,
      tagDistribution,
      breachByTier: Array.from(tierAgg.entries()).map(([tier, v]) => ({ tier, ...v })),
      best: { hour: bestHour, weekday: bestDay },
      totals: {
        sessions: rows.length,
        hours: Math.round((totalSeconds / 3600) * 10) / 10,
        xp: totalXp,
        avg_score: avgScore,
      },
    };
  });
