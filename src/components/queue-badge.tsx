/**
 * Background job queue visibility.
 * Shows a floating pill whenever finalize payloads are parked in localStorage
 * (offline / RPC blip). Click to retry immediately.
 */
import { useEffect, useState } from "react";
import { useAuth } from "@/hooks/use-auth";
import { getQueueSize, flushFinalizeQueue, subscribeQueue } from "@/lib/finalize-queue";
import { haptic } from "@/lib/haptics";

export function QueueBadge() {
  const { user } = useAuth();
  const [count, setCount] = useState(0);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!user) {
      setCount(0);
      return;
    }
    // The queue moved to IndexedDB, so reads are async now. `cancelled` guards
    // against a resolve landing after unmount or after the user changes.
    let cancelled = false;
    const refresh = () => {
      void getQueueSize(user.id).then((n) => {
        if (!cancelled) setCount(n);
      });
    };
    refresh();
    const unsub = subscribeQueue(refresh);
    const onOnline = () => flushFinalizeQueue(user.id).catch(() => {});
    window.addEventListener("online", onOnline);
    return () => {
      cancelled = true;
      unsub();
      window.removeEventListener("online", onOnline);
    };
  }, [user]);

  if (!user || count === 0) return null;

  const retry = async () => {
    setBusy(true);
    haptic("tap");
    // Explicit user action — bypass the backoff schedule, which exists to stop
    // automatic flushes hammering the API, not to ignore the person pressing
    // the button.
    await flushFinalizeQueue(user.id, { force: true }).catch(() => {});
    setCount(await getQueueSize(user.id));
    setBusy(false);
  };

  return (
    <button
      onClick={retry}
      disabled={busy}
      className="fixed bottom-[calc(1rem+env(safe-area-inset-bottom))] left-[calc(1rem+env(safe-area-inset-left))] z-40 flex min-h-11 max-w-[calc(100vw-2rem)] items-center gap-2 rounded-full border border-white/10 glass py-2 pl-3 pr-4 font-mono text-[11px] uppercase tracking-widest text-silver transition-colors hover:border-ember/60 disabled:cursor-not-allowed disabled:opacity-50"
      aria-live="polite"
    >
      <span className={`size-2 rounded-full ${busy ? "bg-silver animate-pulse" : "bg-ember"}`} />
      {busy ? "Syncing…" : `${count} pending · retry`}
    </button>
  );
}
