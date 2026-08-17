import type { AccessTier } from "./subscription.functions";

/**
 * The human-facing premium catalog: what each tier unlocks, for the upgrade UI
 * and the feature-comparison table. This is presentation copy only — the
 * authoritative gate is has_tier() server-side. The `minTier` here must match
 * the tier a feature's server gate actually requires.
 */
export interface PremiumFeature {
  /** Stable key, also used as the gate identifier for <PremiumGate feature="…">. */
  key: string;
  /** What the user gets, in their words. */
  label: string;
  /** Lowest tier that unlocks it. */
  minTier: Exclude<AccessTier, "free">;
}

export const PREMIUM_FEATURES: PremiumFeature[] = [
  { key: "extended_history", label: "Unlimited session history", minTier: "pro" },
  {
    key: "advanced_analytics",
    label: "Advanced personal analytics (DNA, insights)",
    minTier: "pro",
  },
  {
    key: "leaderboard_filters",
    label: "Leaderboard filters — friends, group, season",
    minTier: "pro",
  },
  { key: "custom_themes", label: "Custom themes & premium profile badges", minTier: "pro" },
  { key: "higher_limits", label: "Higher room, challenge & group limits", minTier: "pro" },
  { key: "exclusive_achievements", label: "Exclusive achievements", minTier: "pro" },
  { key: "forecast", label: "Productivity forecasting", minTier: "elite" },
  { key: "vault", label: "Memory vault & time capsules", minTier: "elite" },
  { key: "premium_challenges", label: "Premium-only challenges & events", minTier: "elite" },
  { key: "priority_access", label: "Priority access to new features", minTier: "elite" },
  { key: "ai_first", label: "AI companion & narrative, first (when it ships)", minTier: "elite" },
];

export const TIER_TAGLINE: Record<Exclude<AccessTier, "free">, string> = {
  pro: "Your daily driver, unlocked",
  elite: "Everything, plus what's next",
};

/** Features unlocked at a given tier (cumulative — elite includes pro). */
export function featuresFor(tier: Exclude<AccessTier, "free">): PremiumFeature[] {
  if (tier === "elite") return PREMIUM_FEATURES;
  return PREMIUM_FEATURES.filter((f) => f.minTier === "pro");
}
