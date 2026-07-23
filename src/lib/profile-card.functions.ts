import { createServerFn } from "@tanstack/react-start";
import { z } from "zod";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";

export type ProfileCardData = {
  id: string;
  display_name: string | null;
  avatar_url: string | null;
  bio: string | null;
  lifetime_xp: number;
  current_focus_streak: number;
  best_streak: number;
  total_focus_seconds: number;
  today_focus_seconds: number;
  last_active_at: string | null;
  recent_achievements: Array<{ id: string; name: string; icon: string; tier: string; unlocked_at: string }>;
  mutual_friend_count: number;
  friendship: {
    id: string;
    status: "pending" | "accepted" | "blocked";
    direction: "incoming" | "outgoing" | "friend";
  } | null;
  is_self: boolean;
};

/** One round trip to render a rich hover card for any profile. */
export const getProfileCard = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ profileId: z.string().uuid() }).parse(d))
  .handler(async ({ data, context }): Promise<ProfileCardData> => {
    const { supabase, userId } = context;
    const targetId = data.profileId;
    const isSelf = targetId === userId;

    const startOfDay = new Date();
    startOfDay.setUTCHours(0, 0, 0, 0);

    const [{ data: p, error: pErr }, { data: today }, { data: unlocks }, { data: fs }, { data: myFriends }, { data: theirFriends }] = await Promise.all([
      supabase.from("profiles").select("*").eq("id", targetId).maybeSingle(),
      supabase
        .from("focus_history")
        .select("duration_seconds")
        .eq("profile_id", targetId)
        .gte("created_at", startOfDay.toISOString()),
      supabase
        .from("user_achievements")
        .select("achievement_id, unlocked_at, achievements(name, tier, icon)")
        .eq("user_id", targetId)
        .order("unlocked_at", { ascending: false })
        .limit(3),
      isSelf
        ? Promise.resolve({ data: null })
        : supabase
            .from("friendships")
            .select("id, requester_id, addressee_id, status")
            .or(
              `and(requester_id.eq.${userId},addressee_id.eq.${targetId}),and(requester_id.eq.${targetId},addressee_id.eq.${userId})`,
            )
            .maybeSingle(),
      isSelf
        ? Promise.resolve({ data: [] })
        : supabase
            .from("friendships")
            .select("requester_id, addressee_id")
            .eq("status", "accepted")
            .or(`requester_id.eq.${userId},addressee_id.eq.${userId}`),
      isSelf
        ? Promise.resolve({ data: [] })
        : supabase
            .from("friendships")
            .select("requester_id, addressee_id")
            .eq("status", "accepted")
            .or(`requester_id.eq.${targetId},addressee_id.eq.${targetId}`),
    ]);

    if (pErr) throw new Error(pErr.message);
    if (!p) throw new Error("not_found");

    const todaySeconds = (today ?? []).reduce(
      (a, r) => a + Number((r as { duration_seconds: number }).duration_seconds ?? 0),
      0,
    );

    const recent = (unlocks ?? []).map((u) => {
      const a = u.achievements as unknown as { name: string; tier: string; icon: string } | null;
      return {
        id: u.achievement_id as string,
        name: a?.name ?? (u.achievement_id as string),
        tier: a?.tier ?? "bronze",
        icon: a?.icon ?? "trophy",
        unlocked_at: u.unlocked_at as string,
      };
    });

    // Mutual friends: intersect friend-ids.
    const idOf = (r: { requester_id: string; addressee_id: string }, me: string) =>
      r.requester_id === me ? r.addressee_id : r.requester_id;
    const mineSet = new Set((myFriends ?? []).map((r) => idOf(r as { requester_id: string; addressee_id: string }, userId)));
    const theirs = (theirFriends ?? []).map((r) => idOf(r as { requester_id: string; addressee_id: string }, targetId));
    const mutuals = theirs.filter((id) => mineSet.has(id) && id !== targetId && id !== userId).length;

    let friendship: ProfileCardData["friendship"] = null;
    if (fs && typeof fs === "object" && "requester_id" in (fs as object)) {
      const row = fs as { id: string; requester_id: string; addressee_id: string; status: "pending" | "accepted" | "blocked" };
      friendship = {
        id: row.id,
        status: row.status,
        direction:
          row.status === "accepted" ? "friend" : row.requester_id === userId ? "outgoing" : "incoming",
      };
    }

    return {
      id: p.id,
      display_name: p.display_name,
      avatar_url: p.avatar_url,
      bio: (p as { bio?: string | null }).bio ?? null,
      lifetime_xp: p.lifetime_xp ?? 0,
      current_focus_streak: p.current_focus_streak ?? 0,
      best_streak: (p as { best_streak?: number }).best_streak ?? 0,
      total_focus_seconds: (p as { total_focus_seconds?: number }).total_focus_seconds ?? 0,
      today_focus_seconds: todaySeconds,
      last_active_at: (p as { last_active_at?: string | null }).last_active_at ?? null,
      recent_achievements: recent,
      mutual_friend_count: mutuals,
      friendship,
      is_self: isSelf,
    };
  });
