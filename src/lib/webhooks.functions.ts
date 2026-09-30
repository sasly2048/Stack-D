import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";
import { z } from "zod";
import type { SupabaseClient } from "@supabase/supabase-js";
import type { Database } from "@/integrations/supabase/types";
import { isPublicHttpUrl } from "@/lib/safe-url";
import { publicDbError } from "@/lib/db-error";

type Db = SupabaseClient<Database>;

export interface Webhook {
  id: string;
  url: string;
  events: string[];
  secret: string;
  active: boolean;
  created_at: string;
}

export const EVENT_TYPES = [
  "session.complete",
  "session.start",
  "achievement.unlock",
  "challenge.complete",
  "friend.add",
  "streak.milestone",
] as const;

const CreateSchema = z.object({
  url: z
    .string()
    .url()
    .max(500)
    .refine((u) => isPublicHttpUrl(u), {
      message: "URL must be a public http(s) endpoint",
    }),
  events: z.array(z.enum(EVENT_TYPES)).min(1).max(20),
});
const ToggleSchema = z.object({ id: z.string().uuid(), active: z.boolean() });
const IdSchema = z.object({ id: z.string().uuid() });

export const validateCreateWebhook = (d: unknown) => CreateSchema.parse(d);
export const validateToggleWebhook = (d: unknown) => ToggleSchema.parse(d);
export const validateWebhookId = (d: unknown) => IdSchema.parse(d);

/*
 * Shared bodies — the web server functions below and the public routes the
 * Android app calls (/api/public/webhooks/*) both run exactly these.
 */

export async function listWebhooksCore(supabase: Db, userId: string): Promise<Webhook[]> {
  const { data, error } = await supabase
    .from("webhooks")
    .select("id, url, events, secret, active, created_at")
    .eq("user_id", userId)
    .order("created_at", { ascending: false });
  if (error) throw new Error(error.message);
  return (data ?? []) as Webhook[];
}

export async function createWebhookCore(
  userId: string,
  data: z.infer<typeof CreateSchema>,
): Promise<Webhook> {
  const secret = crypto.randomUUID().replace(/-/g, "") + crypto.randomUUID().replace(/-/g, "");
  // Direct INSERT/UPDATE on webhooks is revoked from clients so a raw Supabase
  // write can't bypass the SSRF URL validation above. Write via the admin
  // client AFTER validating, scoping the row to this user explicitly (admin
  // bypasses RLS, so the user_id here is the only owner guard).
  const { supabaseAdmin } = await import("@/integrations/supabase/client.server");
  const { data: row, error } = await supabaseAdmin
    .from("webhooks")
    .insert({ user_id: userId, url: data.url, events: data.events, secret, active: true })
    .select("id, url, events, secret, active, created_at")
    .single();
  if (error) throw publicDbError(error, "db_write_failed");
  return row as Webhook;
}

export async function toggleWebhookCore(
  userId: string,
  data: z.infer<typeof ToggleSchema>,
): Promise<{ ok: true }> {
  // UPDATE is revoked from clients; go through admin, scoped to the owner.
  const { supabaseAdmin } = await import("@/integrations/supabase/client.server");
  const { error } = await supabaseAdmin
    .from("webhooks")
    .update({ active: data.active })
    .eq("id", data.id)
    .eq("user_id", userId);
  if (error) throw publicDbError(error, "db_write_failed");
  return { ok: true };
}

export async function deleteWebhookCore(
  supabase: Db,
  userId: string,
  data: z.infer<typeof IdSchema>,
): Promise<{ ok: true }> {
  const { error } = await supabase
    .from("webhooks")
    .delete()
    .eq("id", data.id)
    .eq("user_id", userId);
  if (error) throw publicDbError(error, "db_write_failed");
  return { ok: true };
}

export const listWebhooks = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(({ context }): Promise<Webhook[]> => listWebhooksCore(context.supabase, context.userId));

export const createWebhook = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator(validateCreateWebhook)
  .handler(({ data, context }) => createWebhookCore(context.userId, data));

export const toggleWebhook = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator(validateToggleWebhook)
  .handler(({ data, context }) => toggleWebhookCore(context.userId, data));

export const deleteWebhook = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator(validateWebhookId)
  .handler(({ data, context }) => deleteWebhookCore(context.supabase, context.userId, data));
