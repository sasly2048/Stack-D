import { createFileRoute } from "@tanstack/react-router";
import {
  CORS_HEADERS,
  getConnectionIp,
  json,
  normEmail,
  normFp,
  readJsonBody,
  runGuardGates,
  type GuardProvider,
} from "@/lib/auth-guard.server";

const PROVIDERS: GuardProvider[] = ["email", "google", "apple"];

export const Route = createFileRoute("/api/public/auth-guard")({
  server: {
    handlers: {
      OPTIONS: async () => new Response(null, { status: 204, headers: CORS_HEADERS }),
      POST: async ({ request }) => {
        const body = await readJsonBody(request);
        const provider = body.provider as GuardProvider;
        if (!PROVIDERS.includes(provider)) {
          return json({ ok: false, code: "invalid_input", message: "Unknown provider." }, 400);
        }

        const email = normEmail(body.email ?? null);
        const fp = normFp(body.fp ?? null);
        const ip = getConnectionIp();

        const { supabaseAdmin } = await import("@/integrations/supabase/client.server");
        const failure = await runGuardGates({ admin: supabaseAdmin, provider, email, fp, ip });
        if (failure) return json(failure, 429);

        return json({ ok: true });
      },
    },
  },
});
