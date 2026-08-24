import { createServerFn } from "@tanstack/react-start";
import { publicDbError } from "@/lib/db-error";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";
import { requireFeature } from "@/lib/require-tier";
import { withAiBudget } from "@/lib/require-ai-budget";
import { z } from "zod";
import { httpUrl } from "@/lib/zod-url";

export interface VaultItem {
  id: string;
  history_id: string | null;
  title: string;
  body: string | null;
  url: string | null;
  tags: string[];
  ai_summary: string | null;
  created_at: string;
}

/**
 * Make a user search term safe to interpolate into a PostgREST .or() ilike
 * filter. The term goes into a filter STRING, so its control characters (comma,
 * parens, dot, backslash) would break the filter or inject extra conditions;
 * strip those. Then escape the ilike wildcards (% and _) so a literal '%'
 * searches for a percent sign instead of matching every row. Returns "" when
 * nothing searchable remains — callers should then skip the filter, not match
 * all rows.
 */
export function sanitizeVaultSearch(raw: string): string {
  return raw
    .replace(/[,()\\.]/g, " ")
    .replace(/[%_]/g, (c) => `\\${c}`)
    .trim()
    .slice(0, 100);
}

export const listVault = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) =>
    z
      .object({
        q: z.string().optional(),
        tag: z.string().optional(),
        limit: z.number().min(1).max(100).default(50),
      })
      .parse(d),
  )
  .handler(async ({ data, context }): Promise<VaultItem[]> => {
    await requireFeature(context.supabase, "vault");
    let q = context.supabase
      .from("memory_vault_items")
      .select("id, history_id, title, body, url, tags, ai_summary, created_at")
      .eq("user_id", context.userId)
      .order("created_at", { ascending: false })
      .limit(data.limit);
    if (data.tag) q = q.contains("tags", [data.tag]);
    if (data.q) {
      const term = sanitizeVaultSearch(data.q);
      if (term) {
        q = q.or(`title.ilike.%${term}%,body.ilike.%${term}%,ai_summary.ilike.%${term}%`);
      }
    }
    const { data: rows, error } = await q;
    if (error) throw new Error(error.message);
    return (rows ?? []) as VaultItem[];
  });

export const createVaultItem = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) =>
    z
      .object({
        title: z.string().min(1).max(200),
        body: z.string().max(20000).optional(),
        url: httpUrl.max(2000).optional(),
        tags: z.array(z.string().max(24)).max(12).default([]),
        historyId: z.string().uuid().optional(),
      })
      .parse(d),
  )
  .handler(async ({ data, context }): Promise<{ id: string }> => {
    await requireFeature(context.supabase, "vault");
    // #20: RLS only checks the vault row's user_id, not that historyId belongs
    // to the caller. Verify ownership here so a user can't attach another
    // user's focus_history id (which they might know) to their vault item.
    if (data.historyId) {
      const { data: owned } = await context.supabase
        .from("focus_history")
        .select("id")
        .eq("id", data.historyId)
        .eq("profile_id", context.userId)
        .maybeSingle();
      if (!owned) throw new Error("history_not_owned");
    }
    const { data: row, error } = await context.supabase
      .from("memory_vault_items")
      .insert({
        user_id: context.userId,
        title: data.title,
        body: data.body ?? null,
        url: data.url ?? null,
        tags: data.tags.map((t) => t.trim().toLowerCase()).filter(Boolean),
        history_id: data.historyId ?? null,
      })
      .select("id")
      .single();
    if (error) throw publicDbError(error, "db_write_failed");
    return { id: row.id as string };
  });

export const deleteVaultItem = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ id: z.string().uuid() }).parse(d))
  .handler(async ({ data, context }): Promise<{ ok: true }> => {
    await requireFeature(context.supabase, "vault");
    const { error } = await context.supabase
      .from("memory_vault_items")
      .delete()
      .eq("id", data.id)
      .eq("user_id", context.userId);
    if (error) throw publicDbError(error, "db_write_failed");
    return { ok: true };
  });

export const summarizeVaultItem = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ id: z.string().uuid() }).parse(d))
  .handler(async ({ data, context }): Promise<{ summary: string }> => {
    // Vault access is Elite; the summary also spends one AI action (Elite = 200).
    await requireFeature(context.supabase, "vault");
    const { data: item } = await context.supabase
      .from("memory_vault_items")
      .select("title, body")
      .eq("id", data.id)
      .eq("user_id", context.userId)
      .maybeSingle();
    if (!item) throw new Error("not_found");
    const key = process.env.LOVABLE_API_KEY;
    if (!key) throw new Error("Missing LOVABLE_API_KEY");
    // Reserve the AI action around the gateway call so a provider failure
    // refunds the unit instead of burning it.
    const summary = await withAiBudget(context.supabase, async () => {
      const res = await fetch("https://ai.gateway.lovable.dev/v1/chat/completions", {
        method: "POST",
        headers: { "Content-Type": "application/json", "Lovable-API-Key": key },
        body: JSON.stringify({
          model: "google/gemini-3.5-flash",
          messages: [
            {
              role: "system",
              content:
                "You summarize study notes in 2 sentences. Precise, useful for later recall.",
            },
            { role: "user", content: `Title: ${item.title}\n\n${item.body ?? ""}` },
          ],
        }),
      });
      if (!res.ok) throw new Error("ai_failed");
      const j = await res.json();
      return String(j.choices?.[0]?.message?.content ?? "").trim();
    });
    await context.supabase
      .from("memory_vault_items")
      .update({ ai_summary: summary })
      .eq("id", data.id)
      .eq("user_id", context.userId);
    return { summary };
  });
