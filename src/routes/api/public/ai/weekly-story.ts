import { createFileRoute } from "@tanstack/react-router";
import { aiRoute } from "@/lib/ai-public-auth";
import { getWeeklyStoryCore } from "@/lib/ai-narrative.functions";

/** Public AI route for Android: weekly narrative. Errors map to typed JSON via aiRoute. */
export const Route = createFileRoute("/api/public/ai/weekly-story")({
  server: {
    handlers: {
      POST: async ({ request }) =>
        aiRoute(request, async ({ supabase, userId }) => getWeeklyStoryCore(supabase, userId)),
    },
  },
});
