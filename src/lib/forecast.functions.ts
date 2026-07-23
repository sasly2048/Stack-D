import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";

export interface ForecastPayload {
  avgDailyMinutes: number;
  avgDailyXp: number;
  currentXp: number;
  projections: Array<{ label: string; targetXp: number; daysNeeded: number; etaDate: string }>;
  weeklyForecastMinutes: number;
  monthlyForecastMinutes: number;
}

const MILESTONES = [10_000, 25_000, 50_000, 100_000, 250_000, 500_000, 1_000_000];

export const getForecast = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<ForecastPayload> => {
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
    if (rows.length === 0) {
      return {
        avgDailyMinutes: 0,
        avgDailyXp: 0,
        currentXp,
        projections: [],
        weeklyForecastMinutes: 0,
        monthlyForecastMinutes: 0,
      };
    }
    const totalSec = rows.reduce((s, r) => s + (r.duration_seconds ?? 0), 0);
    const totalXp = rows.reduce((s, r) => s + (r.xp_earned ?? 0), 0);
    const avgDailyMinutes = Math.round(totalSec / 60 / 30);
    const avgDailyXp = Math.round(totalXp / 30);
    const projections = MILESTONES.filter((m) => m > currentXp)
      .slice(0, 3)
      .map((target) => {
        const daysNeeded = avgDailyXp > 0 ? Math.ceil((target - currentXp) / avgDailyXp) : 9999;
        const eta = new Date(Date.now() + daysNeeded * 86400_000);
        return {
          label: `${(target / 1000).toFixed(0)}k XP`,
          targetXp: target,
          daysNeeded,
          etaDate: eta.toISOString().slice(0, 10),
        };
      });
    return {
      avgDailyMinutes,
      avgDailyXp,
      currentXp,
      projections,
      weeklyForecastMinutes: avgDailyMinutes * 7,
      monthlyForecastMinutes: avgDailyMinutes * 30,
    };
  });
