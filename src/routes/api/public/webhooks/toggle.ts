import { createFileRoute } from "@tanstack/react-router";
import { authedRoute } from "@/lib/ai-public-auth";
import { toggleWebhookCore, validateToggleWebhook } from "@/lib/webhooks.functions";

/** Public webhooks route for Android (toggle); same core as the web server function. */
export const Route = createFileRoute("/api/public/webhooks/toggle")({
  server: {
    handlers: {
      POST: async ({ request }) =>
        authedRoute(request, async ({ supabase, userId }) =>
          toggleWebhookCore(userId, validateToggleWebhook(await request.json())),
        ),
    },
  },
});
