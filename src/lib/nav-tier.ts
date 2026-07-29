export type NavTier = "starter" | "intermediate" | "advanced";

export const TIER_THRESHOLDS = {
  advanced: { xp: 2000, streak: 7, sessions: 20 },
  intermediate: { xp: 300, streak: 3, sessions: 5 },
} as const;

/**
 * Progressive-disclosure tier. Any single signal crossing a threshold is
 * enough — a user with a long streak but low XP still counts as seasoned.
 */
export function computeTier(xp: number, streak: number, sessions: number): NavTier {
  const a = TIER_THRESHOLDS.advanced;
  if (xp >= a.xp || streak >= a.streak || sessions >= a.sessions) return "advanced";
  const i = TIER_THRESHOLDS.intermediate;
  if (xp >= i.xp || streak >= i.streak || sessions >= i.sessions) return "intermediate";
  return "starter";
}
