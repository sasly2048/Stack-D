import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";
import { z } from "zod";

export interface CircleMember {
  user_id: string;
  display_name: string | null;
  avatar_url: string | null;
  weekly_xp: number;
  weekly_minutes: number;
  current_streak: number;
  is_online: boolean;
}

export interface CircleDetail {
  id: string;
  name: string;
  total_xp: number;
  member_count: number;
  members: CircleMember[];
}

export const listMyCircles = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }) => {
    const { data: mine } = await context.supabase
      .from("group_members")
      .select("group_id, focus_groups!inner(id, name, total_group_xp)")
      .eq("profile_id", context.userId);
    return (mine ?? []).map((r) => {
      const g = r.focus_groups as unknown as { id: string; name: string; total_group_xp: number };
      return { id: g.id, name: g.name, total_xp: g.total_group_xp };
    });
  });

export const getCircleDetail = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ id: z.string().uuid() }).parse(d))
  .handler(async ({ data, context }): Promise<CircleDetail | null> => {
    const { data: g } = await context.supabase
      .from("focus_groups")
      .select("id, name, total_group_xp")
      .eq("id", data.id)
      .maybeSingle();
    if (!g) return null;

    const { data: members } = await context.supabase
      .from("group_members")
      .select("profile_id")
      .eq("group_id", data.id);
    const ids = (members ?? []).map((m) => m.profile_id as string);
    if (!ids.length)
      return { id: g.id as string, name: g.name as string, total_xp: g.total_group_xp as number, member_count: 0, members: [] };

    const weekStart = new Date();
    weekStart.setUTCDate(weekStart.getUTCDate() - 7);

    const [{ data: profs }, { data: hist }] = await Promise.all([
      context.supabase
        .from("profiles")
        .select("id, display_name, avatar_url, current_focus_streak, last_active_at")
        .in("id", ids),
      context.supabase
        .from("focus_history")
        .select("profile_id, xp_earned, duration_seconds")
        .in("profile_id", ids)
        .gte("created_at", weekStart.toISOString()),
    ]);

    const agg = new Map<string, { xp: number; sec: number }>();
    for (const h of hist ?? []) {
      const cur = agg.get(h.profile_id as string) ?? { xp: 0, sec: 0 };
      cur.xp += (h.xp_earned as number) ?? 0;
      cur.sec += (h.duration_seconds as number) ?? 0;
      agg.set(h.profile_id as string, cur);
    }

    const now = Date.now();
    const rows: CircleMember[] = (profs ?? []).map((p) => {
      const a = agg.get(p.id as string) ?? { xp: 0, sec: 0 };
      const last = p.last_active_at ? new Date(p.last_active_at as string).getTime() : 0;
      return {
        user_id: p.id as string,
        display_name: (p.display_name as string) ?? null,
        avatar_url: (p.avatar_url as string) ?? null,
        weekly_xp: a.xp,
        weekly_minutes: Math.round(a.sec / 60),
        current_streak: (p.current_focus_streak as number) ?? 0,
        is_online: last > now - 5 * 60 * 1000,
      };
    });
    rows.sort((a, b) => b.weekly_xp - a.weekly_xp);

    return {
      id: g.id as string,
      name: g.name as string,
      total_xp: g.total_group_xp as number,
      member_count: rows.length,
      members: rows,
    };
  });
