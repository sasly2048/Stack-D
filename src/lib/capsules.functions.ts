import { createServerFn } from "@tanstack/react-start";
import { z } from "zod";
import { publicDbError } from "@/lib/db-error";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";
import { requireFeature } from "@/lib/require-tier";

export interface Capsule {
  id: string;
  message: string;
  open_at: string;
  opened_at: string | null;
  created_at: string;
}

export const listCapsules = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<{ rows: Capsule[] }> => {
    await requireFeature(context.supabase, "time_capsules");
    const { data } = await context.supabase
      .from("time_capsules")
      .select("id,message,open_at,opened_at,created_at")
      .eq("user_id", context.userId)
      .order("open_at", { ascending: true });
    return { rows: (data ?? []) as Capsule[] };
  });

export const writeCapsule = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((input: { message: string; days: number }) => input)
  .handler(async ({ data, context }): Promise<{ id: string }> => {
    await requireFeature(context.supabase, "time_capsules");
    const msg = data.message.trim().slice(0, 4000);
    if (!msg) throw new Error("empty");
    const days = Math.min(365, Math.max(1, Math.floor(data.days)));
    const openAt = new Date(Date.now() + days * 86400_000).toISOString();
    const { data: row, error } = await context.supabase
      .from("time_capsules")
      .insert({ user_id: context.userId, message: msg, open_at: openAt })
      .select("id")
      .single();
    if (error) throw publicDbError(error, "db_write_failed");
    return { id: row!.id };
  });

export const openCapsule = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((input) => z.object({ id: z.string().uuid() }).parse(input))
  .handler(async ({ data, context }): Promise<{ ok: true }> => {
    await requireFeature(context.supabase, "time_capsules");
    // open_at/opened_at are client-unwritable (column grant); opening goes
    // through the SECURITY DEFINER open_capsule() RPC which enforces the
    // open_at <= now() gate server-side so it can't be bypassed by editing
    // open_at directly.
    const { error } = await context.supabase.rpc("open_capsule", { _id: data.id });
    if (error) throw publicDbError(error, "capsule_open_failed");
    return { ok: true };
  });
