import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";
import { z } from "zod";

export const listMyTitles = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }) => {
    const { data } = await context.supabase
      .from("user_titles")
      .select("title_id, earned_at, titles(name, description, icon)")
      .eq("user_id", context.userId);
    return (data ?? []) as unknown as Array<{ title_id: string; earned_at: string; titles: { name: string; description: string; icon: string | null } | null }>;
  });

export const equipTitle = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ titleId: z.string().nullable() }).parse(d))
  .handler(async ({ data, context }) => {
    let name: string | null = null;
    if (data.titleId) {
      const { data: owned } = await context.supabase
        .from("user_titles").select("titles(name)").eq("user_id", context.userId).eq("title_id", data.titleId).maybeSingle();
      const t = (owned as unknown as { titles: { name: string } | null } | null)?.titles;
      if (!t) throw new Error("not_owned");
      name = t.name;
    }
    await context.supabase.from("profiles").update({ title: name }).eq("id", context.userId);
    return { ok: true, title: name };
  });

export const evaluateTitles = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<{ awarded: string[] }> => {
    const { data: profile } = await context.supabase
      .from("profiles").select("current_focus_streak,total_focus_seconds").eq("id", context.userId).maybeSingle();
    const { data: hist } = await context.supabase
      .from("focus_history").select("score,created_at").eq("profile_id", context.userId);
    const { data: rooms } = await context.supabase
      .from("participants").select("room_id").eq("user_id", context.userId);
    const { data: events } = await context.supabase
      .from("room_scheduled_events").select("id").eq("created_by", context.userId);

    const rows = hist ?? [];
    const flowCount = rows.filter((r) => (r.score ?? 0) >= 95).length;
    const nightCount = rows.filter((r) => { const h = new Date(r.created_at).getHours(); return h >= 22 || h < 4; }).length;
    const hours = (profile?.total_focus_seconds ?? 0) / 3600;
    const uniqueRooms = new Set((rooms ?? []).map((r) => r.room_id)).size;

    const criteria: Array<[string, boolean]> = [
      ["night_owl", nightCount >= 3],
      ["deep_thinker", flowCount >= 10],
      ["legend", hours >= 100],
      ["focused", (profile?.current_focus_streak ?? 0) >= 7],
      ["explorer", uniqueRooms >= 5],
      ["planner", (events?.length ?? 0) >= 3],
    ];
    const eligible = criteria.filter(([, ok]) => ok).map(([id]) => id);
    if (!eligible.length) return { awarded: [] };
    const rowsToInsert = eligible.map((id) => ({ user_id: context.userId, title_id: id }));
    const { data: inserted } = await context.supabase
      .from("user_titles").upsert(rowsToInsert, { onConflict: "user_id,title_id", ignoreDuplicates: true }).select("title_id");
    return { awarded: (inserted ?? []).map((r) => r.title_id as string) };
  });
