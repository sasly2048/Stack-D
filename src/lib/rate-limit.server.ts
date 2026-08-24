import { supabaseAdmin } from "@/integrations/supabase/client.server";

/**
 * Sliding-window rate limit backed by the check_and_record_hit RPC (service-
 * role only). Returns true when the caller is OVER the limit for `key`.
 *
 * Fails OPEN: an RPC error must never lock out a legitimate user — a limiter
 * that hard-fails is a self-inflicted DoS. The trade-off is that a broken
 * limiter stops limiting; that's the right default for a throttle whose job is
 * abuse-slowing, not authorization.
 */
export async function isRateLimited(
  key: string,
  windowSeconds: number,
  maxHits: number,
): Promise<boolean> {
  try {
    const { data, error } = await supabaseAdmin.rpc("check_and_record_hit", {
      _key: key,
      _window_seconds: windowSeconds,
      _max_hits: maxHits,
    });
    if (error) return false;
    return !!data;
  } catch {
    return false;
  }
}
