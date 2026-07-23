import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";
import { z } from "zod";

export interface Season {
  id: string;
  name: string;
  description: string | null;
  starts_at: string;
  ends_at: string;
  reward_title_id: string | null;
  xp_multiplier: number;
}

export interface SeasonStanding {
  user_id: string;
  display_name: string | null;
  avatar_url: string | null;
  xp: number;
  rank: number;
}

export const getActiveSeason = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<{ season: Season | null; myXp: number; myRank: number | null; top: SeasonStanding[] }> => {
    const now = new Date().toISOString();
    const { data: s } = await context.supabase
      .from("seasons")
      .select("id, name, description, starts_at, ends_at, reward_title_id, xp_multiplier")
      .lte("starts_at", now)
      .gte("ends_at", now)
      .order("starts_at", { ascending: false })
      .limit(1)
      .maybeSingle();

    if (!s) return { season: null, myXp: 0, myRank: null, top: [] };

    const { data: rows } = await context.supabase
      .from("season_participants")
      .select("user_id, xp")
      .eq("season_id", s.id as string)
      .order("xp", { ascending: false })
      .limit(50);

    const ids = (rows ?? []).map((r) => r.user_id as string);
    const { data: profs } = ids.length
      ? await context.supabase.from("profiles").select("id, display_name, avatar_url").in("id", ids)
      : { data: [] as { id: string; display_name: string | null; avatar_url: string | null }[] };
    const pmap = new Map((profs ?? []).map((p) => [p.id as string, p]));

    const top: SeasonStanding[] = (rows ?? []).map((r, i) => {
      const p = pmap.get(r.user_id as string);
      return {
        user_id: r.user_id as string,
        display_name: (p?.display_name as string) ?? null,
        avatar_url: (p?.avatar_url as string) ?? null,
        xp: r.xp as number,
        rank: i + 1,
      };
    });

    const mine = top.find((t) => t.user_id === context.userId);
    let myXp = mine?.xp ?? 0;
    let myRank: number | null = mine?.rank ?? null;
    if (!mine) {
      const { data: me } = await context.supabase
        .from("season_participants")
        .select("xp")
        .eq("season_id", s.id as string)
        .eq("user_id", context.userId)
        .maybeSingle();
      myXp = (me?.xp as number) ?? 0;
      myRank = null;
    }

    return { season: s as Season, myXp, myRank, top };
  });

export const joinSeason = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ seasonId: z.string().uuid() }).parse(d))
  .handler(async ({ data, context }) => {
    const { error } = await context.supabase
      .from("season_participants")
      .upsert({ season_id: data.seasonId, user_id: context.userId, xp: 0 }, { onConflict: "season_id,user_id" });
    if (error) throw new Error(error.message);
    return { ok: true };
  });
