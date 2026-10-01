import { createFileRoute } from "@tanstack/react-router";
import { aiRoute } from "@/lib/ai-public-auth";
import { generateSessionRecapCore, validateSessionRecapInput } from "@/lib/ai.functions";

/** Public AI route for Android: post-session recap for the Ended screen. Errors map to typed JSON via aiRoute. */
export const Route = createFileRoute("/api/public/ai/session-recap")({
  server: {
    handlers: {
      POST: async ({ request }) =>
        aiRoute(request, async ({ supabase, userId }) =>
          generateSessionRecapCore(
            supabase,
            userId,
            validateSessionRecapInput(await request.json()),
          ),
        ),
    },
  },
});
