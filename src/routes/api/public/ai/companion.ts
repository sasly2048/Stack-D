import { createFileRoute } from "@tanstack/react-router";
import { aiRoute } from "@/lib/ai-public-auth";
import { askCompanionCore, validateCompanionInput } from "@/lib/companion.functions";

/** Public AI route for Android: Study Companion chat. Errors map to typed JSON via aiRoute. */
export const Route = createFileRoute("/api/public/ai/companion")({
  server: {
    handlers: {
      POST: async ({ request }) =>
        aiRoute(request, async ({ supabase, userId }) =>
          askCompanionCore(supabase, userId, validateCompanionInput(await request.json())),
        ),
    },
  },
});
