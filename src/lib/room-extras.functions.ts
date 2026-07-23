import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";

export interface Milestone {
  id: string;
  kind: string;
  label: string;
  reached_at: string;
}
export interface ScheduledEvent {
  id: string;
  title: string;
  description: string | null;
  starts_at: string;
  duration_minutes: number;
  created_by: string;
}

export const listMilestones = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .inputValidator((input: { roomId: string }) => input)
  .handler(async ({ data, context }): Promise<{ rows: Milestone[] }> => {
    const { data: rows } = await context.supabase
      .from("room_milestones")
      .select("id,kind,label,reached_at")
      .eq("room_id", data.roomId)
      .order("reached_at", { ascending: false })
      .limit(30);
    return { rows: (rows ?? []) as Milestone[] };
  });

export const listSchedule = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .inputValidator((input: { roomId: string }) => input)
  .handler(async ({ data, context }): Promise<{ rows: ScheduledEvent[] }> => {
    const { data: rows } = await context.supabase
      .from("room_scheduled_events")
      .select("id,title,description,starts_at,duration_minutes,created_by")
      .eq("room_id", data.roomId)
      .gte("starts_at", new Date(Date.now() - 86400_000).toISOString())
      .order("starts_at", { ascending: true });
    return { rows: (rows ?? []) as ScheduledEvent[] };
  });

export const createScheduledEvent = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((input: { roomId: string; title: string; description?: string; startsAt: string; durationMinutes: number }) => input)
  .handler(async ({ data, context }): Promise<{ id: string }> => {
    const { data: row, error } = await context.supabase
      .from("room_scheduled_events")
      .insert({
        room_id: data.roomId,
        created_by: context.userId,
        title: data.title.slice(0, 120),
        description: data.description?.slice(0, 500) ?? null,
        starts_at: data.startsAt,
        duration_minutes: Math.min(480, Math.max(5, Math.floor(data.durationMinutes))),
      })
      .select("id")
      .single();
    if (error) throw new Error(error.message);
    return { id: row!.id };
  });
