import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";
import { z } from "zod";

export interface MilestoneCard {
  id: string;
  name: string;
  description: string;
  icon: string;
  xp_reward: number;
  unlocked_at: string | null;
  /** Numeric threshold used for progress rendering. */
  threshold: number;
  metric: "hours" | "sessions" | "streak";
}

const METRIC_OF = (id: string): MilestoneCard["metric"] =>
  id.startsWith("ms_hours") ? "hours" : id.startsWith("ms_sessions") ? "sessions" : "streak";

const THRESHOLD_OF = (id: string): number => Number(id.split("_").pop() ?? 0);

export interface MilestoneShelf {
  earned: MilestoneCard[];
  next: { card: MilestoneCard; current: number } | null;
  totalHours: number;
  totalSessions: number;
  bestStreak: number;
}

export const getMilestones = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ userId: z.string().uuid().optional() }).parse(d ?? {}))
  .handler(async ({ data, context }): Promise<MilestoneShelf> => {
    const target = data.userId ?? context.userId;
    const { supabase } = context;

    const { data: defs } = await supabase
      .from("achievements")
      .select("id, name, description, icon, xp_reward")
      .eq("tier", "milestone")
      .order("sort_order", { ascending: true });

    const { data: unlocks } = await supabase
      .from("user_achievements")
      .select("achievement_id, unlocked_at")
      .eq("user_id", target);

    const unlockMap = new Map(
      (unlocks ?? []).map((u) => [u.achievement_id as string, u.unlocked_at as string]),
    );

    const { data: prof } = await supabase
      .from("profiles")
      .select("total_focus_seconds, best_streak")
      .eq("id", target)
      .maybeSingle();

    const totalHours = Math.floor(((prof?.total_focus_seconds as number) ?? 0) / 3600);
    const bestStreak = (prof?.best_streak as number) ?? 0;

    // focus_history is own-scoped; only meaningful for the signed-in user.
    let totalSessions = 0;
    if (target === context.userId) {
      const { count } = await supabase
        .from("focus_history")
        .select("id", { count: "exact", head: true })
        .eq("profile_id", target);
      totalSessions = count ?? 0;
    }

    const cards: MilestoneCard[] = (defs ?? []).map((d) => ({
      id: d.id as string,
      name: d.name as string,
      description: d.description as string,
      icon: d.icon as string,
      xp_reward: d.xp_reward as number,
      unlocked_at: unlockMap.get(d.id as string) ?? null,
      threshold: THRESHOLD_OF(d.id as string),
      metric: METRIC_OF(d.id as string),
    }));

    const earned = cards
      .filter((c) => c.unlocked_at)
      .sort((a, b) => (a.unlocked_at! < b.unlocked_at! ? 1 : -1));

    const currentFor = (m: MilestoneCard["metric"]) =>
      m === "hours" ? totalHours : m === "sessions" ? totalSessions : bestStreak;

    const upcoming = cards
      .filter((c) => !c.unlocked_at)
      .map((c) => ({ card: c, current: currentFor(c.metric) }))
      .sort(
        (a, b) =>
          b.current / Math.max(1, b.card.threshold) - a.current / Math.max(1, a.card.threshold),
      );

    return {
      earned,
      next: upcoming[0] ?? null,
      totalHours,
      totalSessions,
      bestStreak,
    };
  });
