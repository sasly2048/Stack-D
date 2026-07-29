import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";

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
    const msg = data.message.trim().slice(0, 4000);
    if (!msg) throw new Error("empty");
    const days = Math.min(365, Math.max(1, Math.floor(data.days)));
    const openAt = new Date(Date.now() + days * 86400_000).toISOString();
    const { data: row, error } = await context.supabase
      .from("time_capsules")
      .insert({ user_id: context.userId, message: msg, open_at: openAt })
      .select("id")
      .single();
    if (error) throw new Error(error.message);
    return { id: row!.id };
  });

export const openCapsule = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((input: { id: string }) => input)
  .handler(async ({ data, context }): Promise<{ ok: true }> => {
    await context.supabase
      .from("time_capsules")
      .update({ opened_at: new Date().toISOString() })
      .eq("id", data.id)
      .eq("user_id", context.userId)
      .lte("open_at", new Date().toISOString());
    return { ok: true };
  });
