import type { AccessTier } from "./subscription.functions";

/**
 * The single source of truth for premium entitlements — executable, not just
 * display copy. Every premium capability is one row here:
 *
 *   key | requiredTier | limit | serverGate | uiLabel | description
 *
 * The UI reads it for the comparison table and <PremiumGate>; the server reads
 * it via requireFeature() so a handler gates on a feature key, never a
 * hardcoded tier. Change a feature's tier or limit HERE and both the gate and
 * the marketing move together — no drift across systems.
 *
 * Positioning (deliberate, product-level):
 *   PRO  — "Understand Your Focus": personal optimization, analytics,
 *          self-awareness. The layer that shows you your own patterns.
 *   ELITE — "Optimize Your Focus": intelligence, automation, personalization,
 *          influence, exclusivity. Stack'd's intelligence layer, not "Pro+more".
 */

export type PremiumTier = Exclude<AccessTier, "free">;

export interface PremiumFeature {
  /** Stable key: the <PremiumGate feature="…"> id and the requireFeature() id. */
  key: string;
  /** Lowest tier that unlocks it. */
  requiredTier: PremiumTier;
  /**
   * Whether a server-side gate is actually enforced today. Features whose
   * backend isn't built yet (or that are limit-based rather than hard-gated)
   * are catalogued for the UI but marked serverGate:false so this file never
   * over-claims enforcement it doesn't have.
   */
  serverGate: boolean;
  /**
   * Optional usage limit. `null` = unlimited/not-metered. A number is the
   * monthly (or applicable-window) allowance for the tier that unlocks it.
   * Metering enforcement is future work; the value documents intent now.
   */
  limit: number | null;
  /** Short label for the comparison table. */
  uiLabel: string;
  /** One-line benefit description. */
  description: string;
}

/**
 * PRO features — understanding. ELITE features — optimization/intelligence.
 * Elite is cumulative (includes everything Pro has).
 */
export const PREMIUM_FEATURES: PremiumFeature[] = [
  // ---- PRO — Understand Your Focus ------------------------------------------
  {
    key: "focus_dna",
    requiredTier: "pro",
    serverGate: true,
    limit: null,
    uiLabel: "Focus DNA",
    description: "Your focus signature, mapped from every session into traits you can act on.",
  },
  {
    key: "deep_analytics",
    requiredTier: "pro",
    serverGate: false, // insights route not hard-gated yet; DNA carries the gate
    limit: null,
    uiLabel: "Deep Analytics",
    description: "Full-depth personal analytics and historical trends.",
  },
  {
    key: "unlimited_history",
    requiredTier: "pro",
    serverGate: false, // limit-based, not a hard route lock
    limit: null,
    uiLabel: "Unlimited History",
    description: "Every session kept and searchable, not just the last 30 days.",
  },
  {
    key: "custom_protocols",
    requiredTier: "pro",
    serverGate: false, // backend not built yet
    limit: null,
    uiLabel: "Custom Protocols",
    description: "Save and reuse your own enforcement presets.",
  },
  {
    key: "advanced_recaps",
    requiredTier: "pro",
    serverGate: false,
    limit: null,
    uiLabel: "Advanced Session Recaps",
    description: "Richer post-session breakdowns of what shaped your score.",
  },
  {
    key: "advanced_leaderboards",
    requiredTier: "pro",
    serverGate: false,
    limit: null,
    uiLabel: "Advanced Leaderboards",
    description: "Filter by friends, group and season; deeper standings.",
  },
  {
    key: "progress_insights",
    requiredTier: "pro",
    serverGate: false,
    limit: null,
    uiLabel: "Progress Insights",
    description: "Trend-level insight into how your focus is changing over time.",
  },
  {
    key: "custom_themes",
    requiredTier: "pro",
    serverGate: false,
    limit: null,
    uiLabel: "Custom Themes",
    description: "Personalize the look — a secondary perk, not the reason to upgrade.",
  },

  // ---- ELITE — Optimize Your Focus ------------------------------------------
  {
    key: "atlas_coach",
    requiredTier: "elite",
    serverGate: false, // AI stubbed; gate lands when Atlas ships
    limit: 100, // intended monthly Atlas actions (metering is future work)
    uiLabel: "Atlas AI Coach",
    description: "An ambient coach that reads your history and recommends what's next.",
  },
  {
    key: "focus_forecast",
    requiredTier: "elite",
    serverGate: true,
    limit: null,
    uiLabel: "Focus Forecast",
    description: "Projects your trajectory toward XP and focus goals.",
  },
  {
    key: "adaptive_sessions",
    requiredTier: "elite",
    serverGate: false,
    limit: null,
    uiLabel: "Adaptive Sessions",
    description: "Sessions that tune themselves to your patterns.",
  },
  {
    key: "focus_autopilot",
    requiredTier: "elite",
    serverGate: false,
    limit: null,
    uiLabel: "Focus Autopilot",
    description: "Hands-off routines that schedule and enforce focus for you.",
  },
  {
    key: "private_circles",
    requiredTier: "elite",
    serverGate: false,
    limit: null,
    uiLabel: "Private Focus Circles",
    description: "Invite-only circles for the people you actually focus with.",
  },
  {
    key: "advanced_room_controls",
    requiredTier: "elite",
    serverGate: false,
    limit: null,
    uiLabel: "Advanced Room Controls",
    description: "Finer control over enforcement, roles and room behavior.",
  },
  {
    key: "elite_weekly_reports",
    requiredTier: "elite",
    serverGate: false,
    limit: null,
    uiLabel: "Elite Weekly Reports",
    description: "A weekly intelligence digest of your focus.",
  },
  {
    key: "vault",
    requiredTier: "elite",
    serverGate: true,
    limit: null,
    uiLabel: "Memory Vault",
    description: "A private, searchable archive of your sessions, notes and links.",
  },
  {
    key: "time_capsules",
    requiredTier: "elite",
    serverGate: true,
    limit: null,
    uiLabel: "Time Capsules",
    description: "Letters to your future self, sealed until they open.",
  },
  {
    key: "early_access",
    requiredTier: "elite",
    serverGate: false,
    limit: null,
    uiLabel: "Early Access",
    description: "First to new features — a bonus, not the headline.",
  },
];

/** Tier positioning lines used across the upgrade UI. */
export const TIER_TAGLINE: Record<PremiumTier, string> = {
  pro: "Understand your focus",
  elite: "Optimize your focus",
};

const FEATURE_BY_KEY = new Map(PREMIUM_FEATURES.map((f) => [f.key, f]));

/** Look up a feature by key (undefined if unknown). */
export function featureByKey(key: string): PremiumFeature | undefined {
  return FEATURE_BY_KEY.get(key);
}

/** Features unlocked at a given tier (cumulative — elite includes pro). */
export function featuresFor(tier: PremiumTier): PremiumFeature[] {
  if (tier === "elite") return PREMIUM_FEATURES;
  return PREMIUM_FEATURES.filter((f) => f.requiredTier === "pro");
}
