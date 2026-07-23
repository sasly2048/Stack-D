import { createServerFn } from "@tanstack/react-start";
import { z } from "zod";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";

/* --------------------------------- Types --------------------------------- */

export type TimelineSession = {
  id: string;
  created_at: string;
  duration_seconds: number;
  score: number;
  tier: string;
  xp_earned: number;
  breaches_count: number;
  notes: string | null;
  tags: string[] | null;
  room_id: string | null;
  reactions: Array<{ emoji: string; count: number; mine: boolean }>;
};

export type WorkspaceItem = {
  id: string;
  session_id: string | null;
  room_id: string | null;
  kind: "note" | "todo" | "link";
  content: string;
  url: string | null;
  done: boolean;
  position: number;
  created_at: string;
  updated_at: string;
};

/* -------------------------------- Timeline ------------------------------- */

export const listTimeline = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) =>
    z.object({
      limit: z.number().int().min(1).max(50).default(20),
      before: z.string().datetime().optional(),
      profileId: z.string().uuid().optional(),
    }).parse(d),
  )
  .handler(async ({ data, context }): Promise<TimelineSession[]> => {
    const { supabase, userId } = context;
    const targetId = data.profileId ?? userId;

    let q = supabase
      .from("focus_history")
      .select("id, created_at, duration_seconds, score, tier, xp_earned, breaches_count, notes, tags, room_id")
      .eq("profile_id", targetId)
      .order("created_at", { ascending: false })
      .limit(data.limit);
    if (data.before) q = q.lt("created_at", data.before);
    const { data: rows, error } = await q;
    if (error) throw new Error(error.message);
    const sessions = rows ?? [];
    if (sessions.length === 0) return [];

    const { data: rx } = await supabase
      .from("session_reactions")
      .select("session_id, emoji, user_id")
      .in("session_id", sessions.map((s) => s.id));

    const grouped = new Map<string, Map<string, { count: number; mine: boolean }>>();
    for (const r of rx ?? []) {
      const perSession = grouped.get(r.session_id) ?? new Map();
      const cur = perSession.get(r.emoji) ?? { count: 0, mine: false };
      cur.count += 1;
      if (r.user_id === userId) cur.mine = true;
      perSession.set(r.emoji, cur);
      grouped.set(r.session_id, perSession);
    }

    return sessions.map((s) => ({
      ...s,
      reactions: Array.from((grouped.get(s.id) ?? new Map()).entries()).map(
        ([emoji, v]) => ({ emoji, count: v.count, mine: v.mine }),
      ),
    }));
  });

/* ------------------------------- Reactions ------------------------------- */

export const toggleReaction = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) =>
    z.object({
      sessionId: z.string().uuid(),
      emoji: z.string().min(1).max(8),
    }).parse(d),
  )
  .handler(async ({ data, context }) => {
    const { supabase, userId } = context;
    const { data: existing } = await supabase
      .from("session_reactions")
      .select("id")
      .eq("session_id", data.sessionId)
      .eq("user_id", userId)
      .eq("emoji", data.emoji)
      .maybeSingle();

    if (existing) {
      const { error } = await supabase.from("session_reactions").delete().eq("id", existing.id);
      if (error) throw new Error(error.message);
      return { toggled: "off" as const };
    }
    const { error } = await supabase.from("session_reactions").insert({
      session_id: data.sessionId,
      user_id: userId,
      emoji: data.emoji,
    });
    if (error) throw new Error(error.message);
    return { toggled: "on" as const };
  });

/* ------------------------------- Workspace ------------------------------- */

export const listWorkspace = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) =>
    z.object({
      sessionId: z.string().uuid().optional(),
      roomId: z.string().uuid().optional(),
      limit: z.number().int().min(1).max(200).default(100),
    }).parse(d),
  )
  .handler(async ({ data, context }): Promise<WorkspaceItem[]> => {
    const { supabase, userId } = context;
    let q = supabase
      .from("session_workspace_items")
      .select("*")
      .eq("user_id", userId)
      .order("position", { ascending: true })
      .order("created_at", { ascending: false })
      .limit(data.limit);
    if (data.sessionId) q = q.eq("session_id", data.sessionId);
    if (data.roomId) q = q.eq("room_id", data.roomId);
    const { data: rows, error } = await q;
    if (error) throw new Error(error.message);
    return (rows ?? []) as WorkspaceItem[];
  });

export const addWorkspaceItem = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) =>
    z.object({
      sessionId: z.string().uuid().optional(),
      roomId: z.string().uuid().optional(),
      kind: z.enum(["note", "todo", "link"]),
      content: z.string().trim().min(1).max(4000),
      url: z.string().url().max(2000).optional(),
    }).parse(d),
  )
  .handler(async ({ data, context }): Promise<WorkspaceItem> => {
    const { supabase, userId } = context;
    const { data: row, error } = await supabase
      .from("session_workspace_items")
      .insert({
        user_id: userId,
        session_id: data.sessionId ?? null,
        room_id: data.roomId ?? null,
        kind: data.kind,
        content: data.content,
        url: data.url ?? null,
      })
      .select()
      .single();
    if (error) throw new Error(error.message);
    return row as WorkspaceItem;
  });

export const updateWorkspaceItem = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) =>
    z.object({
      id: z.string().uuid(),
      content: z.string().trim().min(1).max(4000).optional(),
      done: z.boolean().optional(),
    }).parse(d),
  )
  .handler(async ({ data, context }) => {
    const { supabase, userId } = context;
    const patch: { content?: string; done?: boolean } = {};
    if (data.content !== undefined) patch.content = data.content;
    if (data.done !== undefined) patch.done = data.done;
    const { error } = await supabase
      .from("session_workspace_items")
      .update(patch)
      .eq("id", data.id)
      .eq("user_id", userId);
    if (error) throw new Error(error.message);
    return { ok: true };
  });

export const deleteWorkspaceItem = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ id: z.string().uuid() }).parse(d))
  .handler(async ({ data, context }) => {
    const { supabase, userId } = context;
    const { error } = await supabase
      .from("session_workspace_items")
      .delete()
      .eq("id", data.id)
      .eq("user_id", userId);
    if (error) throw new Error(error.message);
    return { ok: true };
  });
