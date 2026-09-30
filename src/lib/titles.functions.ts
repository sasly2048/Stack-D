import { createServerFn } from "@tanstack/react-start";
import { publicDbError } from "@/lib/db-error";
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
    // profiles.title is client-unwritable (column grant). equip_title() is
    // SECURITY DEFINER: it verifies ownership in user_titles, then sets the
    // title server-side. NULL clears it.
    const { data: name, error } = await context.supabase.rpc("equip_title", {
      _title_id: data.titleId as unknown as string,
    });
    if (error) throw publicDbError(error, "db_write_failed");
    return { ok: true, title: (name as string | null) ?? null };
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
    if (error) throw publicDbError(error, "db_write_failed");
    const rows = (data ?? []) as unknown as Array<{ title_id: string }>;
    return { awarded: rows.map((r) => r.title_id) };
  });
