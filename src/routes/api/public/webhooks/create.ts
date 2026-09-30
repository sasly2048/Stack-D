import { createFileRoute } from "@tanstack/react-router";
import { authedRoute } from "@/lib/ai-public-auth";
import { createWebhookCore, validateCreateWebhook } from "@/lib/webhooks.functions";

/** Public webhooks route for Android (create); same core as the web server function. */
export const Route = createFileRoute("/api/public/webhooks/create")({
  server: {
    handlers: {
      POST: async ({ request }) =>
        authedRoute(request, async ({ supabase, userId }) =>
          createWebhookCore(userId, validateCreateWebhook(await request.json())),
        ),
    },
  },
});
