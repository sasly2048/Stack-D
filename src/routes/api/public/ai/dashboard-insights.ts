import { createFileRoute } from "@tanstack/react-router";
import { aiRoute } from "@/lib/ai-public-auth";
import { generateDashboardInsightsCore } from "@/lib/ai.functions";

/** Public AI route for Android: dashboard ledger insights. Errors map to typed JSON via aiRoute. */
export const Route = createFileRoute("/api/public/ai/dashboard-insights")({
  server: {
    handlers: {
      POST: async ({ request }) =>
        aiRoute(request, async ({ supabase, userId }) =>
          generateDashboardInsightsCore(supabase, userId),
        ),
    },
  },
});
