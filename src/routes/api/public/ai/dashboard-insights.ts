import { createFileRoute } from "@tanstack/react-router";
import { authenticate, unauthorized } from "@/lib/ai-public-auth";
import { generateDashboardInsightsCore } from "@/lib/ai.functions";

/** Public AI route — ledger insights for the Android dashboard. */
export const Route = createFileRoute("/api/public/ai/dashboard-insights")({
  server: {
    handlers: {
      POST: async ({ request }) => {
        const ctx = await authenticate(request);
        if (!ctx) return unauthorized("Invalid or missing token.");
        const result = await generateDashboardInsightsCore(ctx.supabase, ctx.userId);
        return Response.json(result);
      },
    },
  },
});
