import { defineTool } from "@lovable.dev/mcp-js";
import { z } from "zod";
import { supabaseForUser, unauthenticated } from "../supabase-for-user";

export default defineTool({
  name: "list_focus_history",
  title: "List my focus sessions",
  description:
    "Return the signed-in user's recent focus sessions, most recent first, with score, tier, duration, breaches, and XP earned.",
  inputSchema: {
    limit: z
      .number()
      .int()
      .describe("Maximum number of sessions to return. Defaults to 20, capped at 100.")
      .optional(),
  },
  annotations: { readOnlyHint: true, idempotentHint: true, openWorldHint: false },
  handler: async ({ limit }, ctx) => {
    if (!ctx.isAuthenticated()) return unauthenticated();
    const capped = Math.max(1, Math.min(limit ?? 20, 100));
    const { data, error } = await supabaseForUser(ctx)
      .from("focus_history")
      .select("id, room_id, score, tier, duration_seconds, breaches_count, xp_earned, created_at")
      .eq("profile_id", ctx.getUserId()!)
      .order("created_at", { ascending: false })
      .limit(capped);
    if (error) return { content: [{ type: "text", text: error.message }], isError: true };
    return {
      content: [{ type: "text", text: JSON.stringify(data ?? []) }],
      structuredContent: { sessions: data ?? [] },
    };
  },
});
