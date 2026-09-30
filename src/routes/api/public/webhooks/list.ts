import { createFileRoute } from "@tanstack/react-router";
import { authedRoute } from "@/lib/ai-public-auth";
import { listWebhooksCore } from "@/lib/webhooks.functions";

/** Public webhooks route for Android (list); same core as the web server function. */
export const Route = createFileRoute("/api/public/webhooks/list")({
  server: {
    handlers: {
      GET: async ({ request }) =>
        authedRoute(request, async ({ supabase, userId }) => listWebhooksCore(supabase, userId)),
    },
  },
});
