/**
 * A tiny global event bus for the post-purchase celebration. The celebration
 * must NOT live inside the upgrade dialog / UpgradeCard: those unmount the
 * instant entitlement flips to premium (UpgradeCard returns null for premium
 * users), which would kill the celebration before it renders. Instead a single
 * <CelebrationHost> mounted at the app root listens here and survives any
 * entitlement change.
 */
export type CelebrationTier = "pro" | "elite";

const listeners = new Set<(tier: CelebrationTier) => void>();

/** Fire the celebration for a tier. Safe to call from anywhere. */
export function triggerCelebration(tier: CelebrationTier) {
  listeners.forEach((l) => l(tier));
}

/** Subscribe (used by CelebrationHost). Returns an unsubscribe fn. */
export function onCelebration(fn: (tier: CelebrationTier) => void): () => void {
  listeners.add(fn);
  return () => listeners.delete(fn);
}
