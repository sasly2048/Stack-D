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
    return (data ?? []) as unknown as Array<{
      title_id: string;
      earned_at: string;
      titles: { name: string; description: string; icon: string | null } | null;
    }>;
  });

export const equipTitle = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ titleId: z.string().nullable() }).parse(d))
  .handler(async ({ data, context }) => {
    let name: string | null = null;
    if (data.titleId) {
      const { data: owned } = await context.supabase
        .from("user_titles")
        .select("titles(name)")
        .eq("user_id", context.userId)
        .eq("title_id", data.titleId)
        .maybeSingle();
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
    // Awarding is now server-authoritative: award_earned_titles() (SECURITY
    // DEFINER) re-checks every title's criteria against the caller's real data
    // and inserts only the earned ones. Direct writes to user_titles are
    // revoked, so a client can no longer grant itself a title. This wrapper just
    // invokes the RPC and returns the newly-awarded ids.
    const { data, error } = await context.supabase.rpc("award_earned_titles" as never);
    if (error) throw new Error(error.message);
    const rows = (data ?? []) as unknown as Array<{ title_id: string }>;
    return { awarded: rows.map((r) => r.title_id) };
  });
