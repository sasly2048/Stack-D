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
    if (!user) { setCount(0); return; }
    setCount(getQueueSize(user.id));
    const unsub = subscribeQueue(() => setCount(getQueueSize(user.id)));
    const onOnline = () => flushFinalizeQueue(user.id).catch(() => {});
    window.addEventListener("online", onOnline);
    return () => { unsub(); window.removeEventListener("online", onOnline); };
  }, [user]);

  if (!user || count === 0) return null;

  const retry = async () => {
    setBusy(true);
    haptic("tap");
    await flushFinalizeQueue(user.id).catch(() => {});
    setCount(getQueueSize(user.id));
    setBusy(false);
  };

  return (
    <button
      onClick={retry}
      disabled={busy}
      className="fixed bottom-4 left-4 z-40 glass rounded-full pl-3 pr-4 py-2 flex items-center gap-2 text-[11px] font-mono uppercase tracking-widest text-silver hover:border-ember/60 border border-white/10 disabled:opacity-50"
      aria-live="polite"
    >
      <span className={`size-2 rounded-full ${busy ? "bg-silver animate-pulse" : "bg-ember"}`} />
      {busy ? "Syncing…" : `${count} pending · retry`}
    </button>
  );
}
