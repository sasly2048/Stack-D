import { useEffect, useRef } from "react";

/**
 * Lightweight app-wide XP synchronisation bus.
 *
 * Any mutation that changes a user's XP (daily reward claim, prestige ascent,
 * session finalisation, challenge completion…) calls `notifyXpChanged()`.
 * Every card that renders XP-derived state subscribes with `useXpSync()` and
 * refetches, so the whole UI stays in sync without a reload.
 *
 * Also mirrored over a BroadcastChannel so other open tabs stay in sync.
 */
const EVENT = "stackd:xp-changed";
const CHANNEL = "stackd-xp";

let channel: BroadcastChannel | null = null;
function getChannel(): BroadcastChannel | null {
  if (typeof window === "undefined" || typeof BroadcastChannel === "undefined") return null;
  if (!channel) {
    channel = new BroadcastChannel(CHANNEL);
    channel.onmessage = () => {
      window.dispatchEvent(new CustomEvent(EVENT, { detail: { remote: true } }));
    };
  }
  return channel;
}

/** Announce that the signed-in user's XP / progression state changed. */
export function notifyXpChanged() {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new CustomEvent(EVENT));
  try {
    getChannel()?.postMessage({ at: Date.now() });
  } catch {
    /* channel unavailable — same-tab dispatch already happened */
  }
}

/**
 * Query keys whose data is derived from XP, streaks or progression.
 *
 * Subscribing per-screen is opt-in, so it degrades silently: leaderboard,
 * groups and achievements all render XP-derived numbers and none of them had
 * subscribed, meaning they showed stale totals after a session finalised.
 * Listing the keys here lets one root-level subscriber keep them all honest,
 * so a new screen using one of these keys is correct by default.
 */
export const XP_DERIVED_QUERY_KEYS = [
  "analytics",
  "leaderboard",
  "groups",
  "achievements",
  "challenges",
  "my-profile",
  "seasons",
  "productivity-dna",
] as const;

/** Re-run `handler` whenever XP changes anywhere in the app. */
export function useXpSync(handler: () => void) {
  const ref = useRef(handler);
  ref.current = handler;
  useEffect(() => {
    getChannel();
    const onChange = () => ref.current();
    window.addEventListener(EVENT, onChange);
    return () => window.removeEventListener(EVENT, onChange);
  }, []);
}
