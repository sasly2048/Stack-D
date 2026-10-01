import { createFileRoute } from "@tanstack/react-router";
import { aiRoute } from "@/lib/ai-public-auth";
import { discoverPatternsCore } from "@/lib/ai-narrative.functions";

/** Public AI route for Android: pattern discovery. Errors map to typed JSON via aiRoute. */
export const Route = createFileRoute("/api/public/ai/discover-patterns")({
  server: {
    handlers: {
      POST: async ({ request }) =>
        aiRoute(request, async ({ supabase, userId }) => discoverPatternsCore(supabase, userId)),
    },
  },
});
