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
