/**
 * Brief §2 — Postgres-backed sprint invites.
 *
 * Previously we relied on ephemeral Realtime broadcasts: if a member's tab was
 * backgrounded the message was lost. Now the sprint state is written onto the
 * `focus_groups` row (`active_session_*` columns) and members subscribe via
 * `postgres_changes` so reconnecting / waking devices still discover live
 * sprints.
 */
import type { RealtimeChannel } from "@supabase/supabase-js";
import { supabase } from "@/integrations/supabase/client";

export interface RoomInvitePayload {
  groupId: string;
  groupName: string;
  roomCode: string;
  roomId: string;
  fromName: string;
  fromUserId: string;
  expiresAt: number; // epoch ms
}

interface GroupRowLite {
  id: string;
  name: string;
  active_session_id: string | null;
  active_session_code: string | null;
  active_session_started_at: string | null;
  active_session_expires_at: string | null;
  created_by: string | null;
}

/**
 * Host launching a sprint stamps the parent circle so every member's
 * subscription can pick it up — even if they were offline when it started.
 */
export async function publishGroupSprint(args: {
  groupId: string;
  roomId: string;
  roomCode: string;
  expiresAt: number;
}): Promise<{ error: Error | null; rateLimited?: boolean; retryAfterSeconds?: number }> {
  const { error } = await supabase.rpc("dispatch_group_sprint", {
    _group_id: args.groupId,
    _active_session_id: args.roomId,
    _active_session_code: args.roomCode,
    _started_at: new Date().toISOString(),
    _expires_at: new Date(args.expiresAt).toISOString(),
  });
  if (!error) return { error: null };
  const msg = (error.message || "").toLowerCase();
  if (msg.includes("rate_limited")) {
    // Server enforces 3/user/60s and 5/group/60s in dispatch_group_sprint.
    return { error: new Error("rate_limited"), rateLimited: true, retryAfterSeconds: 60 };
  }
  return { error: new Error(error.message) };
}

/**
 * Subscribe to sprint announcements for every circle the user belongs to.
 * Uses `postgres_changes` so backgrounded / reconnecting clients still resolve
 * to the active sprint on wake (we also seed once from the current rows).
 */
export function subscribeToGroupSprints(
  userId: string,
  handler: (p: RoomInvitePayload) => void,
): () => void {
  let channel: RealtimeChannel | null = null;
  let cancelled = false;
  const seen = new Set<string>(); // dedupe by active_session_id

  const emit = (g: GroupRowLite, fromName = "A circle leader") => {
    if (!g.active_session_id || !g.active_session_code) return;
    const exp = g.active_session_expires_at ? new Date(g.active_session_expires_at).getTime() : 0;
    if (exp && exp < Date.now()) return;
    if (g.created_by === userId) return; // don't self-invite the host
    const key = g.active_session_id;
    if (seen.has(key)) return;
    seen.add(key);
    handler({
      groupId: g.id,
      groupName: g.name,
      roomId: g.active_session_id,
      roomCode: g.active_session_code,
      fromName,
      fromUserId: g.created_by ?? "",
      expiresAt: exp || Date.now() + 5 * 60 * 1000,
    });
  };

  (async () => {
    // 1. Catch up on any sprint that started while we were offline.
    const { data: seed } = await supabase
      .from("focus_groups")
      .select("id, name, active_session_id, active_session_code, active_session_started_at, active_session_expires_at, created_by")
      .not("active_session_id", "is", null);
    if (cancelled) return;
    (seed ?? []).forEach((g) => emit(g as GroupRowLite));

    // 2. Live subscription. RLS already scopes the rows we receive to circles
    //    the user belongs to, so no client-side filter is needed.
    channel = supabase
      .channel(`group-sprints:${userId}`)
      .on(
        "postgres_changes",
        { event: "UPDATE", schema: "public", table: "focus_groups" },
        (payload) => {
          const next = payload.new as GroupRowLite | null;
          const prev = payload.old as GroupRowLite | null;
          if (!next?.active_session_id) return;
          // Only fire when the active session changed.
          if (prev?.active_session_id === next.active_session_id) return;
          emit(next);
        },
      )
      .subscribe();
  })();

  return () => {
    cancelled = true;
    if (channel) supabase.removeChannel(channel);
  };
}
