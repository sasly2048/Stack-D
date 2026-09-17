import { createFileRoute } from "@tanstack/react-router";
import { authenticate, unauthorized } from "@/lib/ai-public-auth";
import { recommendNextSessionCore } from "@/lib/ai.functions";

/**
 * Public AI route for the Android client — next-session recommendation.
 * Android calls {WEB_BASE_URL}/api/public/ai/recommend with its Supabase
 * bearer token; the LOVABLE_API_KEY stays server-side. Shares
 * recommendNextSessionCore with the web RPC, so both platforms get the same
 * output. On any failure the core already returns a deterministic fallback.
 */
export const Route = createFileRoute("/api/public/ai/recommend")({
  server: {
    handlers: {
      POST: async ({ request }) => {
        const ctx = await authenticate(request);
        if (!ctx) return unauthorized("Invalid or missing token.");
        const result = await recommendNextSessionCore(ctx.supabase, ctx.userId);
        return Response.json(result);
      },
    },
  },
});
