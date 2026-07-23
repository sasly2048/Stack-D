import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";
import { z } from "zod";

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

export const listVault = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ q: z.string().optional(), tag: z.string().optional(), limit: z.number().min(1).max(100).default(50) }).parse(d))
  .handler(async ({ data, context }): Promise<VaultItem[]> => {
    let q = context.supabase
      .from("memory_vault_items")
      .select("id, history_id, title, body, url, tags, ai_summary, created_at")
      .eq("user_id", context.userId)
      .order("created_at", { ascending: false })
      .limit(data.limit);
    if (data.tag) q = q.contains("tags", [data.tag]);
    if (data.q) q = q.or(`title.ilike.%${data.q}%,body.ilike.%${data.q}%,ai_summary.ilike.%${data.q}%`);
    const { data: rows, error } = await q;
    if (error) throw new Error(error.message);
    return (rows ?? []) as VaultItem[];
  });

export const createVaultItem = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({
    title: z.string().min(1).max(200),
    body: z.string().max(20000).optional(),
    url: z.string().url().optional(),
    tags: z.array(z.string().max(24)).max(12).default([]),
    historyId: z.string().uuid().optional(),
  }).parse(d))
  .handler(async ({ data, context }): Promise<{ id: string }> => {
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
    if (error) throw new Error(error.message);
    return { id: row.id as string };
  });

export const deleteVaultItem = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ id: z.string().uuid() }).parse(d))
  .handler(async ({ data, context }): Promise<{ ok: true }> => {
    const { error } = await context.supabase.from("memory_vault_items").delete().eq("id", data.id).eq("user_id", context.userId);
    if (error) throw new Error(error.message);
    return { ok: true };
  });

export const summarizeVaultItem = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ id: z.string().uuid() }).parse(d))
  .handler(async ({ data, context }): Promise<{ summary: string }> => {
    const { data: item } = await context.supabase.from("memory_vault_items").select("title, body").eq("id", data.id).eq("user_id", context.userId).maybeSingle();
    if (!item) throw new Error("not_found");
    const key = process.env.LOVABLE_API_KEY;
    if (!key) throw new Error("Missing LOVABLE_API_KEY");
    const res = await fetch("https://ai.gateway.lovable.dev/v1/chat/completions", {
      method: "POST",
      headers: { "Content-Type": "application/json", "Lovable-API-Key": key },
      body: JSON.stringify({
        model: "google/gemini-3.5-flash",
        messages: [
          { role: "system", content: "You summarize study notes in 2 sentences. Precise, useful for later recall." },
          { role: "user", content: `Title: ${item.title}\n\n${item.body ?? ""}` },
        ],
      }),
    });
    if (!res.ok) throw new Error("ai_failed");
    const j = await res.json();
    const summary = String(j.choices?.[0]?.message?.content ?? "").trim();
    await context.supabase.from("memory_vault_items").update({ ai_summary: summary }).eq("id", data.id).eq("user_id", context.userId);
    return { summary };
  });
