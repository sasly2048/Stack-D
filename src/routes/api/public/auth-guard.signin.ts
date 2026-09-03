import { createFileRoute } from "@tanstack/react-router";
import {
  CORS_HEADERS,
  getConnectionIp,
  getUserAgent,
  json,
  normEmail,
  normFp,
  readJsonBody,
  runGuardGates,
} from "@/lib/auth-guard.server";

const INVALID = {
  ok: false as const,
  code: "invalid_credentials" as const,
  message: "Invalid email or password.",
};

export const Route = createFileRoute("/api/public/auth-guard/signin")({
  server: {
    handlers: {
      OPTIONS: async () => new Response(null, { status: 204, headers: CORS_HEADERS }),
      POST: async ({ request }) => {
        const body = await readJsonBody(request);
        const email = normEmail(body.email ?? null);
        const password = typeof body.password === "string" ? body.password : "";
        const fp = normFp(body.fp ?? null);
        const ip = getConnectionIp();
        const userAgent = getUserAgent();

        if (!email || !password) {
          return json({ ok: false, code: "invalid_input", message: "Email and password are required." }, 400);
        }

        const { supabaseAdmin } = await import("@/integrations/supabase/client.server");

        const failure = await runGuardGates({
          admin: supabaseAdmin,
          provider: "email",
          email,
          fp,
          ip,
        });
        if (failure) return json(failure, 429);

        // Fail closed: anything unexpected below must not read as a success.
        let accessToken: string | null = null;
        let refreshToken: string | null = null;
        let reason: string | null = null;

        try {
          const { data, error } = await supabaseAdmin.auth.signInWithPassword({ email, password });
          if (error) {
            reason = error.message.slice(0, 200);
          } else if (!data.session) {
            reason = "no_session";
          } else {
            accessToken = data.session.access_token;
            refreshToken = data.session.refresh_token;
          }
        } catch (e) {
          reason = (e instanceof Error ? e.message : "unexpected_error").slice(0, 200);
        }

        const success = Boolean(accessToken && refreshToken);

        // Log the observed outcome; never let logging change the result.
        try {
          await supabaseAdmin.from("auth_attempts").insert({
            provider: "email",
            email,
            success,
            reason,
            ip,
            user_agent: userAgent,
          });

          // Alert on server-verified failure spikes only.
          if (!success) {
            const { FAILURE_SPIKE_WINDOW_SEC, FAILURE_SPIKE_THRESHOLD, maybeDispatchAuthAlert } =
              await import("@/lib/security-alerts.server");
            const { data: failures } = await supabaseAdmin.rpc("recent_auth_failures", {
              _provider: "email",
              _email: email,
              _window_seconds: FAILURE_SPIKE_WINDOW_SEC,
            } as never);
            if (((failures as number | null) ?? 0) >= FAILURE_SPIKE_THRESHOLD) {
              await maybeDispatchAuthAlert({
                kind: "email_failure_spike",
                email,
                failureCount: (failures as number | null) ?? 0,
                ip,
                userAgent,
              });
            }
          }
        } catch (e) {
          console.error("auth_guard_signin_log_failed", e);
        }


        if (!success) return json(INVALID, 401);

        return json({ ok: true, access_token: accessToken, refresh_token: refreshToken });
      },
    },
  },
});
