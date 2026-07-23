import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";

export const fileReport = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((input: { targetUserId?: string; targetRoomId?: string; kind: string; reason?: string }) => input)
  .handler(async ({ data, context }): Promise<{ id: string }> => {
    if (!data.targetUserId && !data.targetRoomId) throw new Error("no_target");
    const { data: row, error } = await context.supabase
      .from("user_reports")
      .insert({
        reporter_id: context.userId,
        target_user_id: data.targetUserId ?? null,
        target_room_id: data.targetRoomId ?? null,
        kind: data.kind.slice(0, 40),
        reason: data.reason?.slice(0, 500) ?? null,
      })
      .select("id")
      .single();
    if (error) throw new Error(error.message);
    return { id: row!.id };
  });

export const blockUser = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((input: { userId: string }) => input)
  .handler(async ({ data, context }): Promise<{ ok: true }> => {
    if (data.userId === context.userId) throw new Error("self");
    await context.supabase
      .from("user_blocks")
      .insert({ blocker_id: context.userId, blocked_id: data.userId });
    return { ok: true };
  });

export const unblockUser = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((input: { userId: string }) => input)
  .handler(async ({ data, context }): Promise<{ ok: true }> => {
    await context.supabase
      .from("user_blocks")
      .delete()
      .eq("blocker_id", context.userId)
      .eq("blocked_id", data.userId);
    return { ok: true };
  });

export const listBlocks = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<{ rows: Array<{ id: string; display_name: string | null; created_at: string }> }> => {
    const { data: blocks } = await context.supabase
      .from("user_blocks")
      .select("blocked_id,created_at")
      .eq("blocker_id", context.userId);
    const ids = (blocks ?? []).map((b) => b.blocked_id);
    if (ids.length === 0) return { rows: [] };
    const { data: profs } = await context.supabase
      .from("profiles")
      .select("id,display_name")
      .in("id", ids);
    const nameMap = new Map((profs ?? []).map((p) => [p.id, p.display_name]));
    return {
      rows: (blocks ?? []).map((b) => ({
        id: b.blocked_id,
        display_name: nameMap.get(b.blocked_id) ?? null,
        created_at: b.created_at,
      })),
    };
  });

export const listMyReports = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }) => {
    const { data } = await context.supabase
      .from("user_reports")
      .select("id,kind,reason,status,created_at,target_user_id,target_room_id")
      .eq("reporter_id", context.userId)
      .order("created_at", { ascending: false })
      .limit(50);
    return { rows: data ?? [] };
  });
