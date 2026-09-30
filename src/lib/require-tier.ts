import type { SupabaseClient } from "@supabase/supabase-js";

import type { AccessTier } from "./subscription.functions";
import { featureByKey } from "./premium-catalog";

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
  const { data, error } = await supabase.rpc(
    "has_tier" as never,
    {
      _required: required,
    } as never,
  );
  if (error) {
    // Fail closed: if the gate check itself errors, deny rather than leak.
    throw new Error("Could not verify subscription.");
  }
  if (data !== true) {
    const tierName = required === "elite" ? "Elite" : "Pro";
    throw new Error(`This feature requires ${tierName}.`);
  }
}

/**
 * Gate a handler by premium-catalog feature key rather than a raw tier. The
 * catalog is the single source of truth: the feature's requiredTier is resolved
 * here, so moving a feature between tiers in premium-catalog.ts moves its server
 * gate automatically. Throws if the caller doesn't meet the feature's tier.
 *
 *   await requireFeature(context.supabase, "focus_dna");
 */
export async function requireFeature(supabase: SupabaseClient, featureKey: string): Promise<void> {
  const feature = featureByKey(featureKey);
  if (!feature) {
    // A gate referencing an unknown feature is a bug, not a paywall — fail
    // closed so it surfaces immediately rather than silently allowing access.
    throw new Error(`Unknown premium feature: ${featureKey}`);
  }
  await requireTier(supabase, feature.requiredTier);
}
