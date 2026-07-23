import { createServerFn } from "@tanstack/react-start";
import { z } from "zod";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";

export type PublicProfile = {
  id: string;
  display_name: string | null;
  avatar_url: string | null;
  bio: string | null;
  created_at: string;
  lifetime_xp: number;
  current_focus_streak: number;
  best_streak: number;
  total_focus_seconds: number;
  session_count: number;
  achievements: Array<{ id: string; name: string; tier: string; icon: string; unlocked_at: string }>;
  friendship?: { id: string; status: string; direction: "incoming" | "outgoing" | "friend" } | null;
};

/** Fetch a profile (self or other). Includes achievement unlocks + session count. */
export const getProfile = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ userId: z.string().uuid().optional() }).parse(d))
  .handler(async ({ data, context }): Promise<PublicProfile> => {
    const { supabase, userId } = context;
    const targetId = data.userId ?? userId;

    const [{ data: p, error: pErr }, { count }, { data: unlocks }, { data: fs }] = await Promise.all([
      supabase.from("profiles").select("*").eq("id", targetId).maybeSingle(),
      supabase
        .from("focus_history")
        .select("id", { count: "exact", head: true })
        .eq("profile_id", targetId),
      supabase
        .from("user_achievements")
        .select("achievement_id, unlocked_at, achievements(name, tier, icon)")
        .eq("user_id", targetId)
        .order("unlocked_at", { ascending: false }),
      targetId !== userId
        ? supabase
            .from("friendships")
            .select("id, requester_id, addressee_id, status")
            .or(
              `and(requester_id.eq.${userId},addressee_id.eq.${targetId}),and(requester_id.eq.${targetId},addressee_id.eq.${userId})`,
            )
            .maybeSingle()
        : Promise.resolve({ data: null }),
    ]);
    if (pErr) throw new Error(pErr.message);
    if (!p) throw new Error("not_found");

    const achievements = (unlocks ?? []).map((u) => {
      const a = u.achievements as unknown as { name: string; tier: string; icon: string } | null;
      return {
        id: u.achievement_id,
        name: a?.name ?? u.achievement_id,
        tier: a?.tier ?? "bronze",
        icon: a?.icon ?? "trophy",
        unlocked_at: u.unlocked_at,
      };
    });

    let friendship: PublicProfile["friendship"] = null;
    if (fs && "requester_id" in (fs as object)) {
      const row = fs as { id: string; requester_id: string; addressee_id: string; status: string };
      friendship = {
        id: row.id,
        status: row.status,
        direction:
          row.status === "accepted"
            ? "friend"
            : row.requester_id === userId
              ? "outgoing"
              : "incoming",
      };
    }

    return {
      id: p.id,
      display_name: p.display_name,
      avatar_url: p.avatar_url,
      bio: (p as { bio?: string | null }).bio ?? null,
      created_at: p.created_at,
      lifetime_xp: p.lifetime_xp ?? 0,
      current_focus_streak: p.current_focus_streak ?? 0,
      best_streak: (p as { best_streak?: number }).best_streak ?? 0,
      total_focus_seconds: (p as { total_focus_seconds?: number }).total_focus_seconds ?? 0,
      session_count: count ?? 0,
      achievements,
      friendship,
    };
  });

export const updateMyProfile = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) =>
    z
      .object({
        display_name: z.string().trim().min(1).max(40).optional(),
        bio: z.string().trim().max(280).optional(),
        avatar_url: z.string().url().max(500).optional().or(z.literal("")),
      })
      .parse(d),
  )
  .handler(async ({ data, context }) => {
    const patch: { display_name?: string; bio?: string | null; avatar_url?: string | null } = {};
    if (data.display_name !== undefined) patch.display_name = data.display_name;
    if (data.bio !== undefined) patch.bio = data.bio;
    if (data.avatar_url !== undefined) patch.avatar_url = data.avatar_url || null;
    const { error } = await context.supabase.from("profiles").update(patch).eq("id", context.userId);
    if (error) throw new Error(error.message);
    return { ok: true };
  });
