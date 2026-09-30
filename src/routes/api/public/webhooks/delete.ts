import { createFileRoute } from "@tanstack/react-router";
import { authedRoute } from "@/lib/ai-public-auth";
import { deleteWebhookCore, validateWebhookId } from "@/lib/webhooks.functions";

/** Public webhooks route for Android (delete); same core as the web server function. */
export const Route = createFileRoute("/api/public/webhooks/delete")({
  server: {
    handlers: {
      POST: async ({ request }) =>
        authedRoute(request, async ({ supabase, userId }) =>
          deleteWebhookCore(supabase, userId, validateWebhookId(await request.json())),
        ),
    },
  },
});
