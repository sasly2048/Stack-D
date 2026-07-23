import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";
import { z } from "zod";

export interface HostReport {
  id: string;
  kind: string;
  reason: string | null;
  status: string;
  created_at: string;
  target_user_id: string | null;
  target_room_id: string | null;
  room_code: string | null;
  reporter_name: string | null;
  target_name: string | null;
}

export const listRoomReports = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<{ rows: HostReport[] }> => {
    // Rooms I host
    const { data: rooms } = await context.supabase
      .from("rooms")
      .select("id, code")
      .eq("host_id", context.userId);
    const ids = (rooms ?? []).map((r) => r.id);
    if (ids.length === 0) return { rows: [] };
    const roomMap = new Map((rooms ?? []).map((r) => [r.id, r.code]));

    const { data: reports } = await context.supabase
      .from("user_reports")
      .select("id, kind, reason, status, created_at, reporter_id, target_user_id, target_room_id")
      .in("target_room_id", ids)
      .order("created_at", { ascending: false })
      .limit(200);

    const profileIds = new Set<string>();
    for (const r of reports ?? []) {
      profileIds.add(r.reporter_id);
      if (r.target_user_id) profileIds.add(r.target_user_id);
    }
    const { data: profs } = profileIds.size
      ? await context.supabase.from("profiles").select("id, display_name").in("id", [...profileIds])
      : { data: [] };
    const nameMap = new Map((profs ?? []).map((p) => [p.id, p.display_name]));

    return {
      rows: (reports ?? []).map((r) => ({
        id: r.id,
        kind: r.kind,
        reason: r.reason,
        status: r.status,
        created_at: r.created_at,
        target_user_id: r.target_user_id,
        target_room_id: r.target_room_id,
        room_code: r.target_room_id ? (roomMap.get(r.target_room_id) ?? null) : null,
        reporter_name: nameMap.get(r.reporter_id) ?? null,
        target_name: r.target_user_id ? (nameMap.get(r.target_user_id) ?? null) : null,
      })),
    };
  });

export const resolveReport = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) =>
    z.object({
      id: z.string().uuid(),
      status: z.enum(["resolved", "dismissed"]),
    }).parse(d),
  )
  .handler(async ({ data, context }) => {
    const { error } = await context.supabase
      .from("user_reports")
      .update({ status: data.status })
      .eq("id", data.id);
    if (error) throw new Error(error.message);
    return { ok: true };
  });
