import { createFileRoute } from "@tanstack/react-router";
import { authedRoute } from "@/lib/ai-public-auth";
import { listDeliveriesCore, validateWebhookRef } from "@/lib/webhook-deliveries.functions";

/** Public webhooks route for Android (deliveries); same core as the web server function. */
export const Route = createFileRoute("/api/public/webhooks/deliveries")({
  server: {
    handlers: {
      POST: async ({ request }) =>
        authedRoute(request, async ({ supabase, userId }) =>
          listDeliveriesCore(supabase, userId, validateWebhookRef(await request.json())),
        ),
    },
  },
});
