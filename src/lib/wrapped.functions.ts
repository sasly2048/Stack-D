import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";

export interface WrappedStats {
  year: number;
  rolling: boolean;
  totalHours: number;
  totalSessions: number;
  totalXp: number;
  longestSessionMinutes: number;
  bestStreak: number;
  topWeekday: string;
  peakHour: number;
  perfectSessions: number;
  flowSessions: number;
  personality: string | null;
  percentile: number;
  topCollaborator: { name: string; sessions: number } | null;
  monthly: { month: string; hours: number }[];
  displayName: string;
}

const WEEKDAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

export const getWrapped = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<WrappedStats> => {
    const { supabase, userId } = context;
    const now = new Date();
    const year = now.getUTCFullYear();
    const jan1 = new Date(Date.UTC(year, 0, 1));
    // Early in the year, fall back to a rolling 12-month window.
    const rolling = now.getTime() - jan1.getTime() < 90 * 24 * 3600 * 1000;
    const since = rolling
      ? new Date(now.getTime() - 365 * 24 * 3600 * 1000).toISOString()
      : jan1.toISOString();

    const { data: rows } = await supabase
      .from("focus_history")
      .select("duration_seconds, xp_earned, score, breaches_count, created_at, room_id")
      .eq("profile_id", userId)
      .gte("created_at", since)
      .limit(2000);

    const hist = rows ?? [];
    const totalSeconds = hist.reduce((s, r) => s + (r.duration_seconds as number), 0);
    const totalXp = hist.reduce((s, r) => s + (r.xp_earned as number), 0);
    const longest = hist.reduce((m, r) => Math.max(m, r.duration_seconds as number), 0);

    const weekdays = new Array(7).fill(0);
    const hours = new Array(24).fill(0);
    const monthly = new Array(12).fill(0);
    for (const r of hist) {
      const d = new Date(r.created_at as string);
      weekdays[d.getUTCDay()] += r.duration_seconds as number;
      hours[d.getUTCHours()] += 1;
      monthly[d.getUTCMonth()] += (r.duration_seconds as number) / 3600;
    }

    const { data: prof } = await supabase
      .from("profiles")
      .select("display_name, lifetime_xp, best_streak, productivity_dna")
      .eq("id", userId)
      .maybeSingle();

    const lifetimeXp = (prof?.lifetime_xp as number) ?? 0;
    const { count: total } = await supabase
      .from("profiles")
      .select("id", { count: "exact", head: true });
    const { count: below } = await supabase
      .from("profiles")
      .select("id", { count: "exact", head: true })
      .lt("lifetime_xp", lifetimeXp);
    const percentile = total ? Math.round(((below ?? 0) / total) * 100) : 0;

    // Top collaborator: most co-attended rooms this period.
    const roomIds = hist.map((r) => r.room_id as string | null).filter(Boolean) as string[];
    let topCollaborator: WrappedStats["topCollaborator"] = null;
    if (roomIds.length) {
      const { data: mates } = await supabase
        .from("participants")
        .select("user_id, display_name, room_id")
        .in("room_id", roomIds.slice(0, 200))
        .neq("user_id", userId)
        .limit(1000);
      const tally = new Map<string, { name: string; n: number }>();
      for (const m of mates ?? []) {
        const key = m.user_id as string;
        const prev = tally.get(key);
        tally.set(key, { name: (m.display_name as string) ?? "Anon", n: (prev?.n ?? 0) + 1 });
      }
      const best = [...tally.values()].sort((a, b) => b.n - a.n)[0];
      if (best) topCollaborator = { name: best.name, sessions: best.n };
    }

    return {
      year,
      rolling,
      totalHours: Math.round((totalSeconds / 3600) * 10) / 10,
      totalSessions: hist.length,
      totalXp,
      longestSessionMinutes: Math.round(longest / 60),
      bestStreak: (prof?.best_streak as number) ?? 0,
      topWeekday: WEEKDAYS[weekdays.indexOf(Math.max(...weekdays))] ?? "Monday",
      peakHour: hours.indexOf(Math.max(...hours)),
      perfectSessions: hist.filter((r) => (r.breaches_count as number) === 0).length,
      flowSessions: hist.filter((r) => (r.score as number) >= 95).length,
      personality: (prof?.productivity_dna as string) ?? null,
      percentile,
      topCollaborator,
      monthly: monthly.map((h, i) => ({ month: MONTHS[i], hours: Math.round(h * 10) / 10 })),
      displayName: (prof?.display_name as string) ?? "Anon",
    };
  });
