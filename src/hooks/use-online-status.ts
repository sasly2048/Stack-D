import { useSyncExternalStore } from "react";

/**
 * Whether the browser currently believes it has a network connection.
 *
 * The app previously read connectivity in exactly one place — a `"online"`
 * listener in queue-badge.tsx used to flush the finalize queue. There was no
 * `"offline"` listener anywhere and nothing ever told the user they had lost
 * connection; a dead network simply looked like an app that had stopped
 * working.
 *
 * `useSyncExternalStore` rather than useState+useEffect so the value is correct
 * on the very first render after hydration instead of flashing "online".
 */

function subscribe(onChange: () => void) {
  window.addEventListener("online", onChange);
  window.addEventListener("offline", onChange);
  return () => {
    window.removeEventListener("online", onChange);
    window.removeEventListener("offline", onChange);
  };
}

const getSnapshot = () => navigator.onLine;

// The server has no navigator; assume online so SSR markup matches the common
// client case and doesn't render an offline banner into the initial HTML.
const getServerSnapshot = () => true;

export function useOnlineStatus(): boolean {
  return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
}
