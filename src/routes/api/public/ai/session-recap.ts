import { createFileRoute } from "@tanstack/react-router";
import { authenticate, unauthorized } from "@/lib/ai-public-auth";
import {
  generateSessionRecapCore,
  validateSessionRecapInput,
  type SessionRecapInput,
} from "@/lib/ai.functions";

/** Public AI route — post-session recap for the Android Ended screen. */
export const Route = createFileRoute("/api/public/ai/session-recap")({
  server: {
    handlers: {
      POST: async ({ request }) => {
        const ctx = await authenticate(request);
        if (!ctx) return unauthorized("Invalid or missing token.");
        const body = (await request.json()) as SessionRecapInput;
        const input = validateSessionRecapInput(body);
        const result = await generateSessionRecapCore(ctx.supabase, ctx.userId, input);
        return Response.json(result);
      },
    },
  },
});
