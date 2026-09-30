import { createClient, type SupabaseClient } from "@supabase/supabase-js";

/**
 * RLS/IDOR integration helpers. These talk to a LOCAL `supabase start` stack
 * (real Postgres + GoTrue), not the hosted project, so tests exercise actual
 * policies with real JWTs. If the stack isn't up, the suite skips (never fails)
 * so `npm test` stays green in environments without Docker.
 *
 * The default anon/service keys below are the Supabase CLI's fixed local demo
 * keys — they are NOT secrets and only work against 127.0.0.1:54321.
 */
const LOCAL_URL = process.env.SUPABASE_TEST_URL ?? "http://127.0.0.1:54321";

// Supabase CLI local demo keys (public, documented, local-only).
const LOCAL_ANON =
  process.env.SUPABASE_TEST_ANON_KEY ??
  "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJyb2xlIjoiYW5vbiIsImlzcyI6InN1cGFiYXNlLWRlbW8iLCJpYXQiOjE2NDE3NjkyMDAsImV4cCI6MTc5OTUzNTYwMH0.dc_X5iR_VP_qT0zsiyj_I_OZ2T9FtRU2BBNWN8Bu4GE";
const LOCAL_SERVICE =
  process.env.SUPABASE_TEST_SERVICE_KEY ??
  "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJyb2xlIjoic2VydmljZV9yb2xlIiwiaXNzIjoic3VwYWJhc2UtZGVtbyIsImlhdCI6MTY0MTc2OTIwMCwiZXhwIjoxNzk5NTM1NjAwfQ.DaYlNEoUrrEn2Ig7tqibS-PHK5vgusbcbo7X36XVt4Q";

let reachable: boolean | null = null;

/** True if the local stack answers on the auth health endpoint. */
export async function stackIsUp(): Promise<boolean> {
  if (reachable !== null) return reachable;
  try {
    const res = await fetch(`${LOCAL_URL}/auth/v1/health`, {
      headers: { apikey: LOCAL_ANON },
      signal: AbortSignal.timeout(2000),
    });
    reachable = res.ok;
  } catch {
    reachable = false;
  }
  return reachable;
}

export function adminClient(): SupabaseClient {
  return createClient(LOCAL_URL, LOCAL_SERVICE, {
    auth: { autoRefreshToken: false, persistSession: false },
  });
}

export interface TestUser {
  id: string;
  email: string;
  client: SupabaseClient; // authenticated as this user (RLS applies)
}

/**
 * Create a fresh confirmed user and return a client authenticated as them.
 * Uses a unique email per call so reruns don't collide.
 */
export async function makeUser(tag: string): Promise<TestUser> {
  const email = `rls_${tag}_${Math.floor(performance.now())}@example.test`;
  const password = "test-password-123456";
  const client = createClient(LOCAL_URL, LOCAL_ANON, {
    auth: { autoRefreshToken: false, persistSession: false },
  });
  const { data, error } = await client.auth.signUp({ email, password });
  if (error) throw new Error(`signUp failed: ${error.message}`);
  const id = data.user?.id;
  if (!id) throw new Error("signUp returned no user id");
  // With enable_confirmations=false the sign-up already yields a session; make
  // sure the client carries it.
  if (!data.session) {
    const { error: sErr } = await client.auth.signInWithPassword({ email, password });
    if (sErr) throw new Error(`signIn failed: ${sErr.message}`);
  }
  return { id, email, client };
}

/** Anonymous (no JWT) client — the bar every "no anon reads" policy must clear. */
export function anonClient(): SupabaseClient {
  return createClient(LOCAL_URL, LOCAL_ANON, {
    auth: { autoRefreshToken: false, persistSession: false },
  });
}
