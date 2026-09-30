import { createFileRoute } from "@tanstack/react-router";
import { authenticate, unauthorized } from "@/lib/ai-public-auth";
import { discoverPatternsCore } from "@/lib/ai-narrative.functions";

/** Public route — discovered focus patterns (pure heuristic) for Android Insights. */
export const Route = createFileRoute("/api/public/ai/discover-patterns")({
  server: {
    handlers: {
      POST: async ({ request }) => {
        const ctx = await authenticate(request);
        if (!ctx) return unauthorized("Invalid or missing token.");
        const result = await discoverPatternsCore(ctx.supabase, ctx.userId);
        return Response.json(result);
      },
    },
  },
});
