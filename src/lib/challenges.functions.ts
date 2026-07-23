import { createServerFn } from "@tanstack/react-start";
import { z } from "zod";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";

export type ChallengeRow = {
  id: string;
  name: string;
  description: string;
  cadence: "daily" | "weekly";
  metric: "sessions" | "focus_minutes" | "perfect_sessions" | "flow_sessions";
  target: number;
  xp_reward: number;
  progress: number;
  completed_at: string | null;
};

function isoDay(d: Date) {
  return d.toISOString().slice(0, 10);
}
function isoWeekStart(d: Date) {
  const x = new Date(d);
  const day = (x.getUTCDay() + 6) % 7; // Monday = 0
  x.setUTCDate(x.getUTCDate() - day);
  return x.toISOString().slice(0, 10);
}

export const listChallenges = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<{ rows: ChallengeRow[] }> => {
    const { supabase, userId } = context;
    const now = new Date();
    const today = isoDay(now);
    const weekStart = isoWeekStart(now);

    const [{ data: cat }, { data: prog }] = await Promise.all([
      supabase
        .from("challenges")
        .select("id, name, description, cadence, metric, target, xp_reward, sort_order")
        .order("sort_order"),
      supabase
        .from("challenge_progress")
        .select("challenge_id, period_start, progress, completed_at")
        .eq("user_id", userId)
        .in("period_start", [today, weekStart]),
    ]);

    const rows: ChallengeRow[] = (cat ?? []).map((c) => {
      const period = c.cadence === "daily" ? today : weekStart;
      const p = (prog ?? []).find((x) => x.challenge_id === c.id && x.period_start === period);
      return {
        id: c.id,
        name: c.name,
        description: c.description,
        cadence: c.cadence as "daily" | "weekly",
        metric: c.metric as ChallengeRow["metric"],
        target: c.target,
        xp_reward: c.xp_reward,
        progress: p?.progress ?? 0,
        completed_at: p?.completed_at ?? null,
      };
    });
    return { rows };
  });

/** Update notes + tags on a completed session. */
export const updateSessionMeta = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) =>
    z
      .object({
        historyId: z.string().uuid(),
        notes: z.string().max(2000).optional().default(""),
        tags: z.array(z.string().trim().min(1).max(24)).max(8).default([]),
      })
      .parse(d),
  )
  .handler(async ({ data, context }) => {
    const { error } = await context.supabase.rpc("update_session_meta", {
      _history_id: data.historyId,
      _notes: data.notes,
      _tags: data.tags,
    });
    if (error) throw new Error(error.message);
    return { ok: true };
  });
