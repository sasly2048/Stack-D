import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";

function escapeCsv(v: unknown): string {
  if (v === null || v === undefined) return "";
  const s = String(v);
  if (/[",\n\r]/.test(s)) return `"${s.replace(/"/g, '""')}"`;
  return s;
}

export const exportFocusHistoryCsv = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<{ filename: string; csv: string; rowCount: number }> => {
    const { data, error } = await context.supabase
      .from("focus_history")
      .select("created_at, tier, score, xp_earned, duration_seconds, breaches_count, notes, tags, room_id")
      .eq("profile_id", context.userId)
      .order("created_at", { ascending: false })
      .limit(5000);
    if (error) throw new Error(error.message);

    const headers = [
      "date",
      "tier",
      "score",
      "xp_earned",
      "duration_minutes",
      "breaches",
      "notes",
      "tags",
      "room_id",
    ];
    const lines = [headers.join(",")];
    for (const r of data ?? []) {
      lines.push(
        [
          new Date(r.created_at as string).toISOString(),
          r.tier,
          r.score,
          r.xp_earned,
          Math.round(((r.duration_seconds as number) ?? 0) / 60),
          r.breaches_count,
          r.notes,
          Array.isArray(r.tags) ? (r.tags as string[]).join(";") : "",
          r.room_id,
        ]
          .map(escapeCsv)
          .join(","),
      );
    }
    const stamp = new Date().toISOString().slice(0, 10);
    return {
      filename: `stackd-focus-history-${stamp}.csv`,
      csv: lines.join("\n"),
      rowCount: data?.length ?? 0,
    };
  });
