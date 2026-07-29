import { defineTool } from "@lovable.dev/mcp-js";
import { z } from "zod";
import { supabaseForUser, unauthenticated } from "../supabase-for-user";

export default defineTool({
  name: "list_groups",
  title: "List my focus groups",
  description: "Return the focus groups the signed-in user belongs to.",
  inputSchema: {} as Record<string, z.ZodTypeAny>,
  annotations: { readOnlyHint: true, idempotentHint: true, openWorldHint: false },
  handler: async (_input, ctx) => {
    if (!ctx.isAuthenticated()) return unauthenticated();
    const client = supabaseForUser(ctx);
    const { data: memberships, error } = await client
      .from("group_members")
      .select("group_id, joined_at, focus_groups(id, name, total_group_xp, created_by, created_at)")
      .eq("profile_id", ctx.getUserId()!);
    if (error) return { content: [{ type: "text", text: error.message }], isError: true };
    const groups = (memberships ?? []).map((m) => ({
      joined_at: m.joined_at,
      ...(m.focus_groups as Record<string, unknown> | null),
    }));
    return {
      content: [{ type: "text", text: JSON.stringify(groups) }],
      structuredContent: { groups },
    };
  },
});
