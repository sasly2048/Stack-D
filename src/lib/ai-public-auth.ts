/**
 * Bearer-token auth for the public AI routes that the Android client calls
 * ({WEB_BASE_URL}/api/public/ai/*). Mirrors requireSupabaseAuth (the web RPC
 * middleware) but as a plain helper usable inside a TanStack file-route server
 * handler, where middleware doesn't apply. Same token → same token-scoped
 * client → same userId, so the routes enforce exactly what the web RPCs do.
 *
 * The LOVABLE_API_KEY the AI calls need never leaves the server; the client only
 * ever sends its own Supabase access token, which RLS already governs.
 */
import { createClient, type SupabaseClient } from "@supabase/supabase-js";
import type { Database } from "@/integrations/supabase/types";

export type AuthedContext = {
  supabase: SupabaseClient<Database>;
  userId: string;
};

/**
 * Runs a public AI route: authenticates, parses nothing itself, and maps every
 * failure to a typed JSON response. Previously an uncaught throw — a gateway
 * 402 "Not enough credits", a Zod validation error, malformed JSON — escaped
 * the handler and the platform answered with a 500 HTML error page, which the
 * Android client could only treat as "unavailable" with no reason.
 *
 *   400 { error: "invalid_input" }                bad body / failed validation
 *   402 { error: "ai_quota", message }            caller's AI allowance exhausted
 *   503 { error: "ai_unavailable", reason }       gateway refused: credits | rate_limited | gateway
 *   500 { error: "internal" }                     anything else (logged server-side)
 */
export async function aiRoute(
  request: Request,
  run: (ctx: AuthedContext) => Promise<unknown>,
): Promise<Response> {
  const ctx = await authenticate(request);
  if (!ctx) return unauthorized("Invalid or missing token.");
  try {
    return Response.json(await run(ctx));
  } catch (err) {
    return aiErrorResponse(err);
  }
}

/** The same wrapper for any authenticated public route (webhooks, …). */
export const authedRoute = aiRoute;

/** Exported for tests: the error → response mapping used by [aiRoute]. */
export function aiErrorResponse(err: unknown): Response {
  const e = err as {
    name?: string;
    status?: number;
    message?: string;
    issues?: { message?: string }[];
  };
  if (e?.name === "ZodError" || err instanceof SyntaxError) {
    // First validation message, when there is one, so a form can show it
    // ("URL must be a public http(s) endpoint").
    const message = e?.issues?.[0]?.message;
    return Response.json(
      message ? { error: "invalid_input", message } : { error: "invalid_input" },
      { status: 400 },
    );
  }
  if (typeof e?.status === "number") {
    const reason = e.status === 402 ? "credits" : e.status === 429 ? "rate_limited" : "gateway";
    return Response.json({ error: "ai_unavailable", reason }, { status: 503 });
  }
  const message = e?.message ?? "";
  if (/AI features are available on Pro|used all .* AI actions/.test(message)) {
    return Response.json({ error: "ai_quota", message }, { status: 402 });
  }
  // Server code paths throw short machine codes on expected refusals
  // (not_found, url_not_public, db_write_failed…); pass the code through.
  if (/^[a-z][a-z0-9_]{2,40}$/.test(message)) {
    return Response.json({ error: message }, { status: 422 });
  }
  console.error("[ai-route]", err);
  return Response.json({ error: "internal" }, { status: 500 });
}

/** A 401 helper for handlers to `return` on auth failure. */
export function unauthorized(message: string): Response {
  return Response.json({ error: message }, { status: 401 });
}

/**
 * Resolves the caller from the request's Authorization header, or null if the
 * token is missing/invalid (the caller returns `unauthorized(...)` then).
 */
export async function authenticate(request: Request): Promise<AuthedContext | null> {
  const SUPABASE_URL = process.env.SUPABASE_URL;
  const SUPABASE_PUBLISHABLE_KEY = process.env.SUPABASE_PUBLISHABLE_KEY;
  if (!SUPABASE_URL || !SUPABASE_PUBLISHABLE_KEY) return null;

  const authHeader = request.headers.get("authorization");
  if (!authHeader?.startsWith("Bearer ")) return null;
  const token = authHeader.slice("Bearer ".length).trim();
  if (!token) return null;

  const supabase = createClient<Database>(SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY, {
    global: { headers: { Authorization: `Bearer ${token}` } },
    auth: { storage: undefined, persistSession: false, autoRefreshToken: false },
  });

  const { data, error } = await supabase.auth.getClaims(token);
  const userId = data?.claims?.sub;
  if (error || !userId) return null;

  return { supabase, userId };
}
