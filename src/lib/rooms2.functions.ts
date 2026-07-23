import { createServerFn } from "@tanstack/react-start";
import { z } from "zod";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";

/* -------------------------------------------------------------------------- */
/*  Types                                                                      */
/* -------------------------------------------------------------------------- */

export type RoomVisibility = "open" | "request" | "invite";

export type RoomTemplate = {
  key: string;
  title: string;
  description: string;
  target_duration_seconds: number;
  banner_tone: string;
  visibility: RoomVisibility;
  sort_order: number;
};

export type RoomMeta = {
  id: string;
  code: string;
  host_id: string;
  title: string | null;
  description: string | null;
  banner_url: string | null;
  pinned_message: string | null;
  collective_goal_seconds: number | null;
  visibility: RoomVisibility;
  template_key: string | null;
  status: "lobby" | "active" | "complete" | "aborted";
  target_duration_seconds: number;
  started_at: string | null;
  ended_at: string | null;
  created_at: string;
};

export type RoomEventPayload = {
  reason?: string;
  severity?: string;
  message?: string;
  user_id?: string;
  requester_id?: string;
  requester_name?: string;
};

export type RoomEvent = {
  id: string;
  room_id: string;
  actor_id: string | null;
  actor_name: string | null;
  kind: string;
  payload: RoomEventPayload;
  created_at: string;
};

export type RoomStats = {
  members: number;
  breached: number;
  focus_seconds_total: number;
  goal_seconds: number | null;
  progress_pct: number;
};

export type RoomModerator = {
  user_id: string;
  granted_at: string;
  display_name: string | null;
  avatar_url: string | null;
};

export type JoinRequest = {
  id: string;
  room_id: string;
  user_id: string;
  display_name: string;
  message: string | null;
  status: "pending" | "approved" | "denied" | "cancelled";
  created_at: string;
  responded_at: string | null;
};

/* -------------------------------------------------------------------------- */
/*  Templates                                                                  */
/* -------------------------------------------------------------------------- */

export const listRoomTemplates = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<RoomTemplate[]> => {
    const { data, error } = await context.supabase
      .from("room_templates")
      .select("key,title,description,target_duration_seconds,banner_tone,visibility,sort_order")
      .order("sort_order", { ascending: true });
    if (error) throw new Error(error.message);
    return (data ?? []) as RoomTemplate[];
  });

/* -------------------------------------------------------------------------- */
/*  Meta read                                                                  */
/* -------------------------------------------------------------------------- */

export const getRoomMeta = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ code: z.string().length(6) }).parse(d))
  .handler(async ({ data, context }): Promise<RoomMeta | null> => {
    const { data: row, error } = await context.supabase
      .from("rooms")
      .select("*")
      .eq("code", data.code.toUpperCase())
      .maybeSingle();
    if (error) throw new Error(error.message);
    return (row as RoomMeta | null) ?? null;
  });

/* -------------------------------------------------------------------------- */
/*  Meta update (host or moderator)                                            */
/* -------------------------------------------------------------------------- */

const MetaPatch = z.object({
  roomId: z.string().uuid(),
  title: z.string().trim().max(80).optional(),
  description: z.string().trim().max(500).optional(),
  banner_url: z.string().trim().url().max(500).optional().or(z.literal("")),
  pinned_message: z.string().trim().max(400).optional().or(z.literal("")),
  collective_goal_seconds: z.number().int().min(0).max(60 * 60 * 24 * 30).optional().nullable(),
  visibility: z.enum(["open", "request", "invite"]).optional(),
});

export const updateRoomMeta = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => MetaPatch.parse(d))
  .handler(async ({ data, context }) => {
    const { supabase, userId } = context;
    const { data: canEdit } = await supabase.rpc("is_room_moderator", {
      _room_id: data.roomId,
      _user_id: userId,
    });
    if (!canEdit) throw new Error("forbidden");

    const patch: {
      title?: string | null;
      description?: string | null;
      banner_url?: string | null;
      pinned_message?: string | null;
      collective_goal_seconds?: number | null;
      visibility?: RoomVisibility;
    } = {};
    if (data.title !== undefined) patch.title = data.title || null;
    if (data.description !== undefined) patch.description = data.description || null;
    if (data.banner_url !== undefined) patch.banner_url = data.banner_url || null;
    if (data.pinned_message !== undefined) patch.pinned_message = data.pinned_message || null;
    if (data.collective_goal_seconds !== undefined) patch.collective_goal_seconds = data.collective_goal_seconds;
    if (data.visibility !== undefined) patch.visibility = data.visibility;

    const { error } = await supabase.from("rooms").update(patch).eq("id", data.roomId);
    if (error) throw new Error(error.message);

    if (data.pinned_message) {
      await supabase.rpc("record_room_event", {
        _room_id: data.roomId,
        _kind: "pinned",
        _payload: { message: data.pinned_message.slice(0, 200) },
      });
    }
    return { ok: true };
  });

/* -------------------------------------------------------------------------- */
/*  Moderators                                                                 */
/* -------------------------------------------------------------------------- */

export const listRoomModerators = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ roomId: z.string().uuid() }).parse(d))
  .handler(async ({ data, context }): Promise<RoomModerator[]> => {
    const { data: rows, error } = await context.supabase
      .from("room_moderators")
      .select("user_id, granted_at")
      .eq("room_id", data.roomId);
    if (error) throw new Error(error.message);
    const ids = (rows ?? []).map((r) => r.user_id as string);
    let profileMap = new Map<string, { display_name: string | null; avatar_url: string | null }>();
    if (ids.length) {
      const { data: profs } = await context.supabase
        .from("profiles")
        .select("id, display_name, avatar_url")
        .in("id", ids);
      profileMap = new Map(
        (profs ?? []).map((p) => [p.id as string, { display_name: p.display_name, avatar_url: p.avatar_url }]),
      );
    }
    return (rows ?? []).map((r) => {
      const p = profileMap.get(r.user_id as string);
      return {
        user_id: r.user_id as string,
        granted_at: r.granted_at as string,
        display_name: p?.display_name ?? null,
        avatar_url: p?.avatar_url ?? null,
      };
    });
  });

export const promoteModerator = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ roomId: z.string().uuid(), userId: z.string().uuid() }).parse(d))
  .handler(async ({ data, context }) => {
    const { error } = await context.supabase
      .from("room_moderators")
      .insert({ room_id: data.roomId, user_id: data.userId });
    if (error && !error.message.includes("duplicate")) throw new Error(error.message);
    await context.supabase.rpc("record_room_event", {
      _room_id: data.roomId,
      _kind: "moderator_added",
      _payload: { user_id: data.userId },
    });
    return { ok: true };
  });

export const demoteModerator = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ roomId: z.string().uuid(), userId: z.string().uuid() }).parse(d))
  .handler(async ({ data, context }) => {
    const { error } = await context.supabase
      .from("room_moderators")
      .delete()
      .eq("room_id", data.roomId)
      .eq("user_id", data.userId);
    if (error) throw new Error(error.message);
    await context.supabase.rpc("record_room_event", {
      _room_id: data.roomId,
      _kind: "moderator_removed",
      _payload: { user_id: data.userId },
    });
    return { ok: true };
  });

/* -------------------------------------------------------------------------- */
/*  Join requests                                                              */
/* -------------------------------------------------------------------------- */

export const requestToJoinRoom = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) =>
    z.object({
      code: z.string().length(6),
      message: z.string().trim().max(280).optional(),
    }).parse(d),
  )
  .handler(async ({ data, context }) => {
    const { supabase, userId } = context;
    const { data: room, error: rErr } = await supabase
      .from("rooms")
      .select("id, visibility")
      .eq("code", data.code.toUpperCase())
      .maybeSingle();
    if (rErr) throw new Error(rErr.message);
    if (!room) throw new Error("not_found");

    const { data: prof } = await supabase.from("profiles").select("display_name").eq("id", userId).maybeSingle();
    const displayName = prof?.display_name ?? "Anon";

    const { error } = await supabase
      .from("room_join_requests")
      .upsert(
        {
          room_id: room.id,
          user_id: userId,
          display_name: displayName,
          message: data.message ?? null,
          status: "pending",
        },
        { onConflict: "room_id,user_id" },
      );
    if (error) throw new Error(error.message);

    // record via RPC (definer) — requester may not be a room member yet, so
    // the direct insert into room_events is bypassed by the definer check.
    // For public/open rooms, join happens directly; requests only for 'request'.
    return { ok: true };
  });

export const listJoinRequests = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ roomId: z.string().uuid() }).parse(d))
  .handler(async ({ data, context }): Promise<JoinRequest[]> => {
    const { data: rows, error } = await context.supabase
      .from("room_join_requests")
      .select("*")
      .eq("room_id", data.roomId)
      .eq("status", "pending")
      .order("created_at", { ascending: true });
    if (error) throw new Error(error.message);
    return (rows ?? []) as JoinRequest[];
  });

export const respondToJoinRequest = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) =>
    z.object({ requestId: z.string().uuid(), approve: z.boolean() }).parse(d),
  )
  .handler(async ({ data, context }) => {
    const { supabase, userId } = context;
    const { data: req, error: rErr } = await supabase
      .from("room_join_requests")
      .select("id, room_id, user_id, display_name, status")
      .eq("id", data.requestId)
      .maybeSingle();
    if (rErr) throw new Error(rErr.message);
    if (!req) throw new Error("not_found");

    const { data: canModerate } = await supabase.rpc("is_room_moderator", {
      _room_id: req.room_id,
      _user_id: userId,
    });
    if (!canModerate) throw new Error("forbidden");

    const newStatus = data.approve ? "approved" : "denied";
    const { error: uErr } = await supabase
      .from("room_join_requests")
      .update({ status: newStatus, responded_at: new Date().toISOString() })
      .eq("id", req.id);
    if (uErr) throw new Error(uErr.message);

    await supabase.rpc("record_room_event", {
      _room_id: req.room_id,
      _kind: data.approve ? "join_approved" : "join_denied",
      _payload: { requester_id: req.user_id, requester_name: req.display_name },
    });

    return { ok: true };
  });

/* -------------------------------------------------------------------------- */
/*  Live events + stats                                                        */
/* -------------------------------------------------------------------------- */

export const listRoomEvents = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ roomId: z.string().uuid(), limit: z.number().int().min(1).max(100).default(30) }).parse(d))
  .handler(async ({ data, context }): Promise<RoomEvent[]> => {
    const { data: rows, error } = await context.supabase
      .from("room_events")
      .select("*")
      .eq("room_id", data.roomId)
      .order("created_at", { ascending: false })
      .limit(data.limit);
    if (error) throw new Error(error.message);
    return (rows ?? []) as RoomEvent[];
  });

export const getRoomStats = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ roomId: z.string().uuid() }).parse(d))
  .handler(async ({ data, context }): Promise<RoomStats> => {
    const { supabase } = context;
    const [{ data: room }, { data: parts }, { data: history }] = await Promise.all([
      supabase.from("rooms").select("collective_goal_seconds").eq("id", data.roomId).maybeSingle(),
      supabase.from("participants").select("id, breached").eq("room_id", data.roomId),
      supabase.from("focus_history").select("duration_seconds").eq("room_id", data.roomId),
    ]);
    const focus = (history ?? []).reduce((a, r) => a + Number((r as { duration_seconds: number }).duration_seconds ?? 0), 0);
    const goal = (room?.collective_goal_seconds ?? null) as number | null;
    return {
      members: parts?.length ?? 0,
      breached: (parts ?? []).filter((p) => (p as { breached: boolean }).breached).length,
      focus_seconds_total: focus,
      goal_seconds: goal,
      progress_pct: goal && goal > 0 ? Math.min(100, Math.round((focus / goal) * 100)) : 0,
    };
  });

/* -------------------------------------------------------------------------- */
/*  Create room from template                                                  */
/* -------------------------------------------------------------------------- */

export const createRoomFromTemplate = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) =>
    z.object({
      templateKey: z.string().min(1).max(60),
      title: z.string().trim().max(80).optional(),
      description: z.string().trim().max(500).optional(),
      collective_goal_seconds: z.number().int().min(0).max(60 * 60 * 24 * 30).optional().nullable(),
    }).parse(d),
  )
  .handler(async ({ data, context }): Promise<{ code: string }> => {
    const { supabase, userId } = context;
    const { data: tpl, error: tErr } = await supabase
      .from("room_templates").select("*").eq("key", data.templateKey).maybeSingle();
    if (tErr) throw new Error(tErr.message);
    if (!tpl) throw new Error("template_not_found");

    // Generate unique code (retry a few times)
    const gen = () => Array.from({ length: 6 }, () =>
      "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"[Math.floor(Math.random() * 32)]
    ).join("");
    let code = gen();
    for (let i = 0; i < 5; i++) {
      const { data: exists } = await supabase.rpc("room_code_exists", { _code: code });
      if (!exists) break;
      code = gen();
    }

    const { data: room, error: iErr } = await supabase
      .from("rooms")
      .insert({
        code,
        host_id: userId,
        target_duration_seconds: tpl.target_duration_seconds,
        status: "lobby",
        title: data.title ?? tpl.title,
        description: data.description ?? tpl.description,
        collective_goal_seconds: data.collective_goal_seconds ?? null,
        visibility: tpl.visibility,
        template_key: tpl.key,
      })
      .select("code, id")
      .single();
    if (iErr) throw new Error(iErr.message);

    const { data: prof } = await supabase.from("profiles").select("display_name").eq("id", userId).maybeSingle();
    await supabase.from("participants").insert({
      room_id: room.id,
      user_id: userId,
      display_name: prof?.display_name ?? "Host",
    });

    return { code: room.code };
  });
