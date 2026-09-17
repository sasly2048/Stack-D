import { createFileRoute } from "@tanstack/react-router";
import { authenticate, unauthorized } from "@/lib/ai-public-auth";
import { askCompanionCore, validateCompanionInput } from "@/lib/companion.functions";

/** Public AI route — the Study Companion chat for Android. */
export const Route = createFileRoute("/api/public/ai/companion")({
  server: {
    handlers: {
      POST: async ({ request }) => {
        const ctx = await authenticate(request);
        if (!ctx) return unauthorized("Invalid or missing token.");
        const input = validateCompanionInput(await request.json());
        const result = await askCompanionCore(ctx.supabase, ctx.userId, input);
        return Response.json(result);
      },
    },
  },
});
