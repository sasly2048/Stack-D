import { createServerFn } from "@tanstack/react-start";
import { z } from "zod";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";

export type FriendshipStatus = "pending" | "accepted" | "blocked";

export type FriendRow = {
  id: string;
  user_id: string;
  display_name: string | null;
  avatar_url: string | null;
  status: FriendshipStatus;
  direction: "incoming" | "outgoing" | "friend";
  since: string;
};

/** List all friendships (accepted + pending in either direction). */
export const listFriends = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }) => {
    const { supabase, userId } = context;
    const { data, error } = await supabase
      .from("friendships")
      .select("id, requester_id, addressee_id, status, created_at")
      .or(`requester_id.eq.${userId},addressee_id.eq.${userId}`)
      .order("created_at", { ascending: false });
    if (error) throw new Error(error.message);

    const otherIds = Array.from(
      new Set((data ?? []).map((r) => (r.requester_id === userId ? r.addressee_id : r.requester_id))),
    );
    let profiles: Record<string, { display_name: string | null; avatar_url: string | null }> = {};
    if (otherIds.length) {
      const { data: profs } = await supabase
        .from("profiles")
        .select("id, display_name, avatar_url")
        .in("id", otherIds);
      profiles = Object.fromEntries((profs ?? []).map((p) => [p.id, p]));
    }

    const rows: FriendRow[] = (data ?? []).map((r) => {
      const other = r.requester_id === userId ? r.addressee_id : r.requester_id;
      const direction: FriendRow["direction"] =
        r.status === "accepted" ? "friend" : r.requester_id === userId ? "outgoing" : "incoming";
      return {
        id: r.id,
        user_id: other,
        display_name: profiles[other]?.display_name ?? null,
        avatar_url: profiles[other]?.avatar_url ?? null,
        status: r.status as FriendshipStatus,
        direction,
        since: r.created_at,
      };
    });
    return { rows };
  });

/** Search users by display name (excluding self). */
export const searchPeople = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ q: z.string().trim().min(1).max(60) }).parse(d))
  .handler(async ({ data, context }) => {
    const { supabase, userId } = context;
    const { data: rows, error } = await supabase
      .from("profiles")
      .select("id, display_name, avatar_url")
      .ilike("display_name", `%${data.q}%`)
      .neq("id", userId)
      .limit(20);
    if (error) throw new Error(error.message);
    return { rows: rows ?? [] };
  });

export const sendFriendRequest = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ addresseeId: z.string().uuid() }).parse(d))
  .handler(async ({ data, context }) => {
    const { supabase, userId } = context;
    if (data.addresseeId === userId) throw new Error("self");
    const { error } = await supabase.from("friendships").insert({
      requester_id: userId,
      addressee_id: data.addresseeId,
      status: "pending",
    });
    if (error && !/duplicate/i.test(error.message)) throw new Error(error.message);
    return { ok: true };
  });

export const respondFriendRequest = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) =>
    z.object({ id: z.string().uuid(), accept: z.boolean() }).parse(d),
  )
  .handler(async ({ data, context }) => {
    const { supabase, userId } = context;
    if (data.accept) {
      const { error } = await supabase
        .from("friendships")
        .update({ status: "accepted" })
        .eq("id", data.id)
        .eq("addressee_id", userId);
      if (error) throw new Error(error.message);
    } else {
      const { error } = await supabase
        .from("friendships")
        .delete()
        .eq("id", data.id)
        .eq("addressee_id", userId);
      if (error) throw new Error(error.message);
    }
    return { ok: true };
  });

export const removeFriend = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ id: z.string().uuid() }).parse(d))
  .handler(async ({ data, context }) => {
    const { supabase, userId } = context;
    const { error } = await supabase
      .from("friendships")
      .delete()
      .eq("id", data.id)
      .or(`requester_id.eq.${userId},addressee_id.eq.${userId}`);
    if (error) throw new Error(error.message);
    return { ok: true };
  });
