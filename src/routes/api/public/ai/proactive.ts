import { createFileRoute } from "@tanstack/react-router";
import { authenticate, unauthorized } from "@/lib/ai-public-auth";
import { getProactiveInsightsCore } from "@/lib/proactive-ai.functions";

/** Public AI route — proactive insights (schedule/prediction/burnout) for Android. */
export const Route = createFileRoute("/api/public/ai/proactive")({
  server: {
    handlers: {
      GET: async ({ request }) => {
        const ctx = await authenticate(request);
        if (!ctx) return unauthorized("Invalid or missing token.");
        const result = await getProactiveInsightsCore(ctx.supabase, ctx.userId);
        return Response.json(result);
      },
    },
  },
});
