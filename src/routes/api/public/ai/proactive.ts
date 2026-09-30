import { createFileRoute } from "@tanstack/react-router";
import { aiRoute } from "@/lib/ai-public-auth";
import { getProactiveInsightsCore } from "@/lib/proactive-ai.functions";

/** Public AI route for Android: proactive insights (schedule/prediction/burnout). Errors map to typed JSON via aiRoute. */
export const Route = createFileRoute("/api/public/ai/proactive")({
  server: {
    handlers: {
      GET: async ({ request }) =>
        aiRoute(request, async ({ supabase, userId }) =>
          getProactiveInsightsCore(supabase, userId),
        ),
    },
  },
});
