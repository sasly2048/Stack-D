import { createFileRoute } from "@tanstack/react-router";
import { aiRoute } from "@/lib/ai-public-auth";
import { z } from "zod";
import { summarizeVaultItemCore } from "@/lib/memory-vault.functions";

/** Public AI route for Android: vault item summary (Elite-gated). Errors map to typed JSON via aiRoute. */
export const Route = createFileRoute("/api/public/ai/vault-summarize")({
  server: {
    handlers: {
      POST: async ({ request }) =>
        aiRoute(request, async ({ supabase, userId }) =>
          summarizeVaultItemCore(
            supabase,
            userId,
            z.object({ id: z.string().uuid() }).parse(await request.json()),
          ),
        ),
    },
  },
});
