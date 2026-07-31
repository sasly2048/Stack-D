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
