import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";
import { requireFeature } from "@/lib/require-tier";

export interface ForecastPayload {
  /** Kept = the 30-calendar-day average, for backwards compatibility. */
  avgDailyMinutes: number;
  avgDailyXp: number;
  /** Distinct averaging bases so the UI can be honest about the number. */
  avg7DayMinutes: number;
  avg7DayXp: number;
  avg30DayMinutes: number;
  avg30DayXp: number;
  /** Averaged over days actually practised, not idle calendar days. */
  avgActiveDayMinutes: number;
  avgActiveDayXp: number;
  activeDays: number;
  currentXp: number;
  projections: Array<{ label: string; targetXp: number; daysNeeded: number; etaDate: string }>;
  weeklyForecastMinutes: number;
  monthlyForecastMinutes: number;
}

const MILESTONES = [10_000, 25_000, 50_000, 100_000, 250_000, 500_000, 1_000_000];

export const getForecast = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<ForecastPayload> => {
    await requireFeature(context.supabase, "focus_forecast");
    const since = new Date(Date.now() - 30 * 86400_000).toISOString();
    const [{ data: hist }, { data: prof }] = await Promise.all([
      context.supabase
        .from("focus_history")
        .select("duration_seconds,xp_earned,created_at")
        .eq("profile_id", context.userId)
        .gte("created_at", since),
      context.supabase.from("profiles").select("lifetime_xp").eq("id", context.userId).single(),
    ]);
    const rows = hist ?? [];
    const currentXp = prof?.lifetime_xp ?? 0;
    const empty: ForecastPayload = {
      avgDailyMinutes: 0,
      avgDailyXp: 0,
      avg7DayMinutes: 0,
      avg7DayXp: 0,
      avg30DayMinutes: 0,
      avg30DayXp: 0,
      avgActiveDayMinutes: 0,
      avgActiveDayXp: 0,
      activeDays: 0,
      currentXp,
      projections: [],
      weeklyForecastMinutes: 0,
      monthlyForecastMinutes: 0,
    };
    if (rows.length === 0) return empty;

    const cutoff7 = Date.now() - 7 * 86400_000;
    const sum = (rs: typeof rows) => ({
      sec: rs.reduce((s, r) => s + (r.duration_seconds ?? 0), 0),
      xp: rs.reduce((s, r) => s + (r.xp_earned ?? 0), 0),
    });
    const all30 = sum(rows);
    const last7 = sum(rows.filter((r) => new Date(r.created_at as string).getTime() >= cutoff7));
    // Distinct days that actually have a session (in UTC — day-bucketing only).
    const activeDays = new Set(rows.map((r) => (r.created_at as string).slice(0, 10))).size || 1;

    const avg30DayMinutes = Math.round(all30.sec / 60 / 30);
    const avg30DayXp = Math.round(all30.xp / 30);
    const avg7DayMinutes = Math.round(last7.sec / 60 / 7);
    const avg7DayXp = Math.round(last7.xp / 7);
    const avgActiveDayMinutes = Math.round(all30.sec / 60 / activeDays);
    const avgActiveDayXp = Math.round(all30.xp / activeDays);

    // Project from the ACTIVE-day rate: a user who practises 3 days a week
    // shouldn't have their ETA computed as if they idle the other 4. This is the
    // honest "at your current pace, when you actually show up" number.
    const rateXp = avgActiveDayXp;
    const projections = MILESTONES.filter((m) => m > currentXp)
      .slice(0, 3)
      .map((target) => {
        const daysNeeded = rateXp > 0 ? Math.ceil((target - currentXp) / rateXp) : 9999;
        const eta = new Date(Date.now() + daysNeeded * 86400_000);
        return {
          label: `${(target / 1000).toFixed(0)}k XP`,
          targetXp: target,
          daysNeeded,
          etaDate: eta.toISOString().slice(0, 10),
        };
      });

    return {
      avgDailyMinutes: avg30DayMinutes,
      avgDailyXp: avg30DayXp,
      avg7DayMinutes,
      avg7DayXp,
      avg30DayMinutes,
      avg30DayXp,
      avgActiveDayMinutes,
      avgActiveDayXp,
      activeDays,
      currentXp,
      projections,
      weeklyForecastMinutes: avg7DayMinutes * 7,
      monthlyForecastMinutes: avg30DayMinutes * 30,
    };
  });
