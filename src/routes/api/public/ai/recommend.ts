import { createFileRoute } from "@tanstack/react-router";
import { aiRoute } from "@/lib/ai-public-auth";
import { recommendNextSessionCore } from "@/lib/ai.functions";

/** Public AI route for Android: next-session recommendation (Atlas card). Errors map to typed JSON via aiRoute. */
export const Route = createFileRoute("/api/public/ai/recommend")({
  server: {
    handlers: {
      POST: async ({ request }) =>
        aiRoute(request, async ({ supabase, userId }) =>
          recommendNextSessionCore(supabase, userId),
        ),
    },
  },
});
