import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { toast } from "sonner";
import { getPrestigeStatus, prestigeUp, type PrestigeStatus } from "@/lib/prestige.functions";
import { haptic } from "@/lib/haptics";

export function PrestigeCeremony() {
  const load = useServerFn(getPrestigeStatus);
  const ascend = useServerFn(prestigeUp);
  const [status, setStatus] = useState<PrestigeStatus | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    load().then(setStatus).catch(() => undefined);
  }, []);

  if (!status) return null;
  const pct = Math.min(100, (status.lifetimeXp / status.neededXp) * 100);

  const onAscend = async () => {
    setBusy(true);
    try {
      const { newPrestige } = await ascend();
      haptic("heavy");
      toast.success(`Prestige ${newPrestige} · ascended`);
      setConfirming(false);
      const fresh = await load();
      setStatus(fresh);
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "Failed");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="border border-white/10 rounded-md p-6 bg-black/40">
      <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Prestige</p>
      <div className="mt-3 flex items-baseline gap-3">
        <span className="font-serif text-4xl text-silver">P{status.level}</span>
        <span className="font-mono text-xs text-silver-dim">
          {status.lifetimeXp.toLocaleString()} / {status.neededXp.toLocaleString()} XP
        </span>
      </div>
      <div className="mt-3 h-1 rounded-full bg-white/5 overflow-hidden">
        <div className="h-full bg-ember" style={{ width: `${pct}%` }} />
      </div>
      {status.canPrestige ? (
        confirming ? (
          <div className="mt-4 p-4 border border-ember/40 rounded bg-ember/[0.06] text-sm text-silver">
            <p className="font-serif text-lg">Ascend to Prestige {status.level + 1}?</p>
            <p className="mt-2 text-silver-dim text-xs leading-relaxed">
              Your XP total remains. Your streak resets to zero. A new ring joins your frame.
            </p>
            <div className="mt-4 flex gap-3">
              <button
                onClick={onAscend}
                disabled={busy}
                className="px-4 py-2 rounded-full bg-ember text-obsidian font-mono text-xs uppercase tracking-widest disabled:opacity-50"
              >
                {busy ? "Ascending…" : "Ascend"}
              </button>
              <button
                onClick={() => setConfirming(false)}
                className="px-4 py-2 rounded-full border border-white/20 text-silver-dim font-mono text-xs uppercase tracking-widest"
              >
                Cancel
              </button>
            </div>
          </div>
        ) : (
          <button
            onClick={() => setConfirming(true)}
            className="mt-4 px-4 py-2 rounded-full border border-ember text-ember font-mono text-xs uppercase tracking-widest hover:bg-ember/10"
          >
            Prestige now
          </button>
        )
      ) : (
        <p className="mt-3 text-xs text-silver-dim">
          Reach {status.neededXp.toLocaleString()} XP to ascend.
        </p>
      )}
    </div>
  );
}
