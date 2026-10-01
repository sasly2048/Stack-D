import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";
import { z } from "zod";
import { humanizeKey } from "@/lib/utils";

export interface ReplayEvent {
  t: string;
  kind: "session" | "breach" | "achievement" | "milestone";
  label: string;
  score?: number;
  tier?: string;
  duration?: number;
}

export const getDayReplay = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) =>
    z
      .object({
        date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
        // The caller's local-midnight window. A UTC day ran 05:30-05:30 for
        // an IST user, so late-night sessions landed on the wrong date.
        start: z.string().datetime({ offset: true }).optional(),
        end: z.string().datetime({ offset: true }).optional(),
      })
      .parse(d),
  )
  .handler(async ({ data, context }): Promise<ReplayEvent[]> => {
    let start = new Date(`${data.date}T00:00:00Z`).toISOString();
    let end = new Date(new Date(start).getTime() + 24 * 3600 * 1000).toISOString();
    if (data.start && data.end) {
      const span = new Date(data.end).getTime() - new Date(data.start).getTime();
      // One calendar day: 23-25h across DST, capped at 26h so this can't be
      // used as an unbounded history scan.
      if (span > 0 && span <= 26 * 3600 * 1000) {
        start = new Date(data.start).toISOString();
        end = new Date(data.end).toISOString();
      }
    }
    const uid = context.userId;

    const [history, activity] = await Promise.all([
      context.supabase
        .from("focus_history")
        .select("id, created_at, score, tier, duration_seconds, breaches_count, xp_earned")
        .eq("profile_id", uid)
        .gte("created_at", start)
        .lt("created_at", end),
      context.supabase
        .from("activity_events")
        .select("kind, payload, created_at")
        .eq("user_id", uid)
        .gte("created_at", start)
        .lt("created_at", end),
    ]);

    const events: ReplayEvent[] = [];
    for (const h of history.data ?? []) {
      events.push({
        t: h.created_at as string,
        kind: "session",
        label: `${h.tier} · ${Math.round((h.duration_seconds as number) / 60)}m · ${h.score}pt`,
        score: h.score as number,
        tier: h.tier as string,
        duration: h.duration_seconds as number,
      });
    }
    for (const a of activity.data ?? []) {
      if (a.kind === "achievement_unlock") {
        const payload = (a.payload ?? {}) as { id?: string };
        events.push({
          t: a.created_at as string,
          kind: "achievement",
          label: `Unlocked ${payload.id ? humanizeKey(payload.id) : "mark"}`,
        });
      }
    }
    return events.sort((a, b) => a.t.localeCompare(b.t));
  });
