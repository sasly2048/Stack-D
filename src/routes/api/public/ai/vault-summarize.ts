import { createFileRoute } from "@tanstack/react-router";
import { z } from "zod";
import { authenticate, unauthorized } from "@/lib/ai-public-auth";
import { summarizeVaultItemCore } from "@/lib/memory-vault.functions";

/** Public AI route — vault item summary (Elite-gated) for Android. */
export const Route = createFileRoute("/api/public/ai/vault-summarize")({
  server: {
    handlers: {
      POST: async ({ request }) => {
        const ctx = await authenticate(request);
        if (!ctx) return unauthorized("Invalid or missing token.");
        const { id } = z.object({ id: z.string().uuid() }).parse(await request.json());
        const result = await summarizeVaultItemCore(ctx.supabase, ctx.userId, { id });
        return Response.json(result);
      },
    },
  },
});
