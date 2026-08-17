import type { SupabaseClient } from "@supabase/supabase-js";

import type { AccessTier } from "./subscription.functions";

/**
 * Server-side premium gate. Throws if the caller doesn't meet `required`. This
 * is the REAL enforcement — <PremiumGate> in the UI is cosmetic and bypassable;
 * this runs inside a server function, calling the SECURITY DEFINER has_tier()
 * RPC (which resolves admin/lifetime/expiry authoritatively).
 *
 *   await requireTier(context.supabase, "pro");   // top of a gated handler
 *
 * The RPC name uses `as never` because the generated Supabase types don't
 * include has_tier() until regenerated — same convention as the *.functions.ts
 * files. Typed against the base SupabaseClient so any generated client matches.
 */
export async function requireTier(
  supabase: SupabaseClient,
  required: Exclude<AccessTier, "free">,
): Promise<void> {
  const { data, error } = await supabase.rpc("has_tier" as never, {
    _required: required,
  } as never);
  if (error) {
    // Fail closed: if the gate check itself errors, deny rather than leak.
    throw new Error("Could not verify subscription.");
  }
  if (data !== true) {
    const tierName = required === "elite" ? "Elite" : "Pro";
    throw new Error(`This feature requires ${tierName}.`);
  }
}
