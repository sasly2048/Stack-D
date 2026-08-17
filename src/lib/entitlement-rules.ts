/**
 * Pure tier-comparison rules. The authoritative check is server-side
 * (my_entitlement / has_tier RPCs); this mirror exists only so the client can
 * reflect gating without a round-trip. Never the sole gate.
 */
import type { AccessTier, Entitlement } from "./subscription.functions";

const RANK: Record<AccessTier, number> = { free: 0, pro: 1, elite: 2 };

export function tierRank(t: AccessTier): number {
  return RANK[t];
}

/** Does this entitlement meet at least `required`? Admin/lifetime resolve to elite. */
export function meetsTier(ent: Pick<Entitlement, "tier">, required: AccessTier): boolean {
  return tierRank(ent.tier) >= tierRank(required);
}

/** Annual saving vs 12x the monthly price, as a rounded percentage. */
export function annualSavingsPct(monthlyInr: number, annualInr: number): number {
  const yearAtMonthly = monthlyInr * 12;
  if (yearAtMonthly <= 0) return 0;
  return Math.round((1 - annualInr / yearAtMonthly) * 100);
}
