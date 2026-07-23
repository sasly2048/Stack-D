import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";

export type Achievement = {
  id: string;
  name: string;
  description: string;
  icon: string;
  tier: "bronze" | "silver" | "gold" | "obsidian";
  xp_reward: number;
  sort_order: number;
  unlocked_at: string | null;
};

/** Full catalog joined with the caller's unlocks. */
export const listAchievements = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<{ rows: Achievement[]; unlocked: number; total: number }> => {
    const { supabase, userId } = context;
    const [{ data: catalog, error: cErr }, { data: unlocks, error: uErr }] = await Promise.all([
      supabase
        .from("achievements")
        .select("id, name, description, icon, tier, xp_reward, sort_order")
        .order("sort_order", { ascending: true }),
      supabase.from("user_achievements").select("achievement_id, unlocked_at").eq("user_id", userId),
    ]);
    if (cErr) throw new Error(cErr.message);
    if (uErr) throw new Error(uErr.message);

    const unlockMap = new Map((unlocks ?? []).map((u) => [u.achievement_id, u.unlocked_at]));
    const rows = (catalog ?? []).map((c) => ({
      ...(c as Omit<Achievement, "unlocked_at">),
      unlocked_at: unlockMap.get(c.id) ?? null,
    })) as Achievement[];
    return { rows, unlocked: unlockMap.size, total: rows.length };
  });
