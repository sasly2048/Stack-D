import type { SupabaseClient } from "@supabase/supabase-js";

/**
 * Consume one AI action for the caller, or refuse. Call this immediately before
 * any real AI-gateway request so cost is bounded per tier per billing cycle
 * (free 0, pro 20, elite 200; admin/lifetime unlimited). Atomic + race-safe in
 * the ai_meter() RPC. Throws a user-facing message when the monthly allowance is
 * exhausted so the handler surfaces it instead of making the paid call.
 */
export async function requireAiBudget(supabase: SupabaseClient): Promise<void> {
  const { data, error } = await supabase.rpc("ai_meter" as never);
  if (error) {
    // Fail closed: if we can't verify budget, don't make the paid AI call.
    throw new Error("Couldn't verify your AI usage. Please try again.");
  }
  const row = Array.isArray(data) ? data[0] : data;
  const r = row as { ok?: boolean; allowance?: number; unlimited?: boolean } | null;
  if (!r?.ok) {
    if ((r?.allowance ?? 0) <= 0) {
      throw new Error("AI features are available on Pro and Elite.");
    }
    throw new Error(
      `You've used all ${r?.allowance ?? 0} of your AI actions for this billing period. They reset when your subscription renews.`,
    );
  }
}

/** Give back a consumed AI action (best-effort; never throws). */
export async function refundAiBudget(supabase: SupabaseClient): Promise<void> {
  try {
    await supabase.rpc("ai_refund" as never);
  } catch {
    // A failed refund must not mask the original error.
  }
}

/**
 * Reserve one AI action, run `work`, and refund the action if `work` throws
 * (e.g. the provider call fails) so a user isn't charged for nothing. The
 * result of `work` is returned on success.
 */
export async function withAiBudget<T>(
  supabase: SupabaseClient,
  work: () => Promise<T>,
): Promise<T> {
  await requireAiBudget(supabase);
  try {
    return await work();
  } catch (err) {
    await refundAiBudget(supabase);
    throw err;
  }
}
