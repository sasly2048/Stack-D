import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";
import { z } from "zod";

export type CompanionMessage = { role: "user" | "assistant"; content: string };

const MessageSchema = z.object({
  role: z.enum(["user", "assistant"]),
  content: z.string().min(1).max(4000),
});

export const askCompanion = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) =>
    z
      .object({
        history: z.array(MessageSchema).max(30).default([]),
        message: z.string().min(1).max(2000),
      })
      .parse(d),
  )
  .handler(async ({ data, context }): Promise<{ reply: string }> => {
    // Pull light user context so the coach can reference actual behaviour.
    const uid = context.userId;
    const [{ data: profile }, { data: recent }] = await Promise.all([
      context.supabase
        .from("profiles")
        .select("display_name, lifetime_xp, current_focus_streak")
        .eq("id", uid)
        .maybeSingle(),
      context.supabase
        .from("focus_history")
        .select("score, tier, duration_seconds, created_at")
        .eq("profile_id", uid)
        .order("created_at", { ascending: false })
        .limit(10),
    ]);

    const factSheet = [
      profile ? `name: ${profile.display_name ?? "operator"}` : "",
      profile ? `xp: ${profile.lifetime_xp ?? 0}` : "",
      profile ? `streak_days: ${profile.current_focus_streak ?? 0}` : "",
      recent && recent.length
        ? `recent_sessions: ${recent
            .map((r) => `${r.tier}/${Math.round((r.duration_seconds ?? 0) / 60)}m/${r.score}pt`)
            .join(", ")}`
        : "recent_sessions: none",
    ].filter(Boolean).join("\n");

    const { callAIJson, BRAND_TONE } = await import("./ai.server");
    const out = await callAIJson<{ reply: string }>({
      messages: [
        {
          role: "system",
          content:
            BRAND_TONE +
            "\nYou are the Stack'd Study Companion — a private, one-on-one focus coach. " +
            "Answer conversationally, but stay short (2-4 sentences). " +
            "Reference the operator's own data when useful. Never invent statistics. " +
            "Return strict JSON: {\"reply\": string}.\n\nOPERATOR:\n" + factSheet,
        },
        ...data.history.slice(-20).map((m) => ({ role: m.role, content: m.content })),
        { role: "user", content: data.message },
      ],
      temperature: 0.7,
    });

    return { reply: out.reply?.slice(0, 2000) ?? "" };
  });
