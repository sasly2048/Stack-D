import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";
import { publicDbError } from "@/lib/db-error";

export interface PrestigeStatus {
  level: number;
  lifetimeXp: number;
  neededXp: number;
  canPrestige: boolean;
}

export const getPrestigeStatus = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<PrestigeStatus> => {
    const { data } = await context.supabase
      .from("profiles")
      .select("lifetime_xp,prestige_level")
      .eq("id", context.userId)
      .single();
    const level = data?.prestige_level ?? 0;
    const lifetimeXp = data?.lifetime_xp ?? 0;
    const neededXp = 100000 * (level + 1);
    return { level, lifetimeXp, neededXp, canPrestige: lifetimeXp >= neededXp };
  });

export const prestigeUp = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<{ newPrestige: number }> => {
    const { data, error } = await context.supabase.rpc("prestige_up");
    if (error) throw publicDbError(error, "db_write_failed");
    const row = Array.isArray(data) ? data[0] : data;
    return { newPrestige: row.new_prestige };
  });
