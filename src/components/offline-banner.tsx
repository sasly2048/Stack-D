import { useOnlineStatus } from "@/hooks/use-online-status";

/**
 * Tells the user the network is gone.
 *
 * Without this, losing connection presents as an app that has quietly stopped
 * responding: fetches fail, toasts say "couldn't load", and nothing explains
 * why. One honest line removes a whole class of "the app is broken" confusion.
 *
 * Deliberately sits below the queue badge's z-index so the two never fight for
 * the same corner, and uses the ember/breach palette already in use for
 * degraded states.
 */
export function OfflineBanner() {
  const online = useOnlineStatus();
  if (online) return null;

  return (
    <div
      role="status"
      aria-live="polite"
      className="fixed inset-x-0 top-0 z-[60] border-b border-breach/30 bg-breach/10 px-4 py-2 text-center backdrop-blur-md"
    >
      <span className="font-mono text-[10px] uppercase tracking-[0.3em] text-breach">
        Offline — your progress is held until you reconnect
      </span>
    </div>
  );
}
