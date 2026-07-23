import { createServerFn } from "@tanstack/react-start";
import { z } from "zod";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";

export type FeedItem = {
  id: string;
  user_id: string;
  display_name: string | null;
  avatar_url: string | null;
  kind: "session_complete" | "achievement_unlock" | "challenge_complete" | "friend_add";
  payload: Record<string, string | number | boolean | null>;
  created_at: string;
};

export const listFeed = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ limit: z.number().min(1).max(50).default(25) }).parse(d))
  .handler(async ({ data, context }): Promise<{ rows: FeedItem[] }> => {
    const { supabase, userId } = context;
    const { data: events, error } = await supabase
      .from("activity_events")
      .select("id, user_id, kind, payload, created_at")
      .order("created_at", { ascending: false })
      .limit(data.limit);
    if (error) throw new Error(error.message);

    const ids = Array.from(new Set((events ?? []).map((e) => e.user_id))).filter((x) => x !== userId);
    let profs: Record<string, { display_name: string | null; avatar_url: string | null }> = {};
    if (ids.length) {
      const { data: p } = await supabase.from("profiles").select("id, display_name, avatar_url").in("id", ids);
      profs = Object.fromEntries((p ?? []).map((r) => [r.id, r]));
    }
    const { data: me } = await supabase.from("profiles").select("display_name, avatar_url").eq("id", userId).maybeSingle();

    return {
      rows: (events ?? []).map((e) => ({
        id: e.id,
        user_id: e.user_id,
        display_name: e.user_id === userId ? me?.display_name ?? null : profs[e.user_id]?.display_name ?? null,
        avatar_url: e.user_id === userId ? me?.avatar_url ?? null : profs[e.user_id]?.avatar_url ?? null,
        kind: e.kind as FeedItem["kind"],
        payload: (e.payload ?? {}) as Record<string, string | number | boolean | null>,
        created_at: e.created_at,
      })),
    };
  });

/** Presence heartbeat. Called every ~60s while the app is focused. */
export const heartbeat = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }) => {
    const { error } = await context.supabase.rpc("presence_heartbeat");
    if (error) throw new Error(error.message);
    return { ok: true };
  });

export type PresenceStatus = "focusing" | "idle" | "offline";

/** Presence + basic activity summary for accepted friends. */
export const friendsPresence = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }) => {
    const { supabase, userId } = context;
    const { data: fs } = await supabase
      .from("friendships")
      .select("requester_id, addressee_id")
      .eq("status", "accepted")
      .or(`requester_id.eq.${userId},addressee_id.eq.${userId}`);
    const friendIds = (fs ?? []).map((f) => (f.requester_id === userId ? f.addressee_id : f.requester_id));
    if (!friendIds.length) return { rows: [] };

    const [{ data: profs }, { data: activeParts }] = await Promise.all([
      supabase
        .from("profiles")
        .select("id, display_name, avatar_url, lifetime_xp, current_focus_streak, last_active_at")
        .in("id", friendIds),
      supabase
        .from("participants")
        .select("user_id, room_id, rooms!inner(status)")
        .in("user_id", friendIds)
        .eq("rooms.status", "active"),
    ]);

    const focusingSet = new Set((activeParts ?? []).map((p) => p.user_id));
    const now = Date.now();
    const rows = (profs ?? []).map((p) => {
      const last = p.last_active_at ? new Date(p.last_active_at).getTime() : 0;
      const status: PresenceStatus = focusingSet.has(p.id)
        ? "focusing"
        : last && now - last < 5 * 60_000
          ? "idle"
          : "offline";
      return { ...p, status };
    });
    return { rows };
  });
