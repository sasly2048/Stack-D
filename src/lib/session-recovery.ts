import { supabase } from "@/integrations/supabase/client";

interface MaybeError {
  message?: string | null;
  code?: string | null;
}

/**
 * A request that reached Postgres as `anon` — i.e. the access token expired
 * while the page stayed open. Postgres reports this as 42501 /
 * "permission denied for ...", never as a 401, so the client sees it as an
 * ordinary query error and would otherwise just toast and give up.
 */
export function isAuthLapse(error: MaybeError | null | undefined): boolean {
  if (!error) return false;
  const msg = (error.message ?? "").toLowerCase();
  return (
    error.code === "42501" ||
    msg.includes("permission denied") ||
    msg.includes("jwt expired") ||
    msg.includes("invalid claim")
  );
}

/**
 * Run a Supabase call and, if it fails only because the session lapsed,
 * refresh the token and try exactly once more. Callers keep their normal
 * error handling for every other failure.
 */
export async function withSessionRetry<T extends { error: MaybeError | null }>(
  run: () => PromiseLike<T>,
): Promise<T> {
  const first = await run();
  if (!isAuthLapse(first.error)) return first;

  const { data, error } = await supabase.auth.refreshSession();
  if (error || !data.session) return first;

  return run();
}
