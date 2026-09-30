import { createFileRoute } from "@tanstack/react-router";
import { authedRoute } from "@/lib/ai-public-auth";
import { testWebhookCore, validateWebhookRef } from "@/lib/webhook-deliveries.functions";

/** Public webhooks route for Android (test); same core as the web server function. */
export const Route = createFileRoute("/api/public/webhooks/test")({
  server: {
    handlers: {
      POST: async ({ request }) =>
        authedRoute(request, async ({ supabase, userId }) =>
          testWebhookCore(supabase, userId, validateWebhookRef(await request.json())),
        ),
    },
  },
});
