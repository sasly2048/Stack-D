import { createFileRoute } from "@tanstack/react-router";
import { authenticate, unauthorized } from "@/lib/ai-public-auth";
import { getWeeklyStoryCore } from "@/lib/ai-narrative.functions";

/** Public AI route — weekly narrative story for the Android Insights screen. */
export const Route = createFileRoute("/api/public/ai/weekly-story")({
  server: {
    handlers: {
      POST: async ({ request }) => {
        const ctx = await authenticate(request);
        if (!ctx) return unauthorized("Invalid or missing token.");
        const result = await getWeeklyStoryCore(ctx.supabase, ctx.userId);
        return Response.json(result);
      },
    },
  },
});
