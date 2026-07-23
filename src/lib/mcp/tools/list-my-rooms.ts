import { defineTool } from "@lovable.dev/mcp-js";
import { z } from "zod";
import { supabaseForUser, unauthenticated } from "../supabase-for-user";

export default defineTool({
  name: "list_my_rooms",
  title: "List rooms I created",
  description:
    "Return focus rooms the signed-in user created (host rooms), most recent first, with code and settings.",
  inputSchema: {
    limit: z
      .number()
      .int()
      .describe("Maximum rooms to return. Defaults to 20, capped at 100.")
      .optional(),
  },
  annotations: { readOnlyHint: true, idempotentHint: true, openWorldHint: false },
  handler: async ({ limit }, ctx) => {
    if (!ctx.isAuthenticated()) return unauthenticated();
    const capped = Math.max(1, Math.min(limit ?? 20, 100));
    const { data, error } = await supabaseForUser(ctx)
      .from("rooms")
      .select("*")
      .eq("host_id", ctx.getUserId()!)
      .order("created_at", { ascending: false })
      .limit(capped);
    if (error) return { content: [{ type: "text", text: error.message }], isError: true };
    return {
      content: [{ type: "text", text: JSON.stringify(data ?? []) }],
      structuredContent: { rooms: data ?? [] },
    };
  },
});
