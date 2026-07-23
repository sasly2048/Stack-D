import { useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { toggleReaction } from "@/lib/session-interactions.functions";
import { haptic } from "@/lib/haptics";
import { toast } from "sonner";

const PICKER = ["🔥", "💎", "🧘", "⚡", "🌒", "◆"];

export function SessionReactionBar({
  sessionId,
  reactions: initial,
}: {
  sessionId: string;
  reactions: Array<{ emoji: string; count: number; mine: boolean }>;
}) {
  const [rx, setRx] = useState(initial);
  const [busy, setBusy] = useState(false);
  const [picking, setPicking] = useState(false);
  const toggle = useServerFn(toggleReaction);

  async function fire(emoji: string) {
    if (busy) return;
    haptic("select");
    setBusy(true);
    // optimistic
    const idx = rx.findIndex((r) => r.emoji === emoji);
    const next = rx.slice();
    if (idx >= 0) {
      const cur = next[idx];
      const updated = { ...cur, mine: !cur.mine, count: cur.count + (cur.mine ? -1 : 1) };
      if (updated.count <= 0) next.splice(idx, 1);
      else next[idx] = updated;
    } else {
      next.push({ emoji, count: 1, mine: true });
    }
    setRx(next);
    try {
      await toggle({ data: { sessionId, emoji } });
    } catch (e) {
      setRx(initial);
      toast.error(e instanceof Error ? e.message : "Reaction failed");
    } finally {
      setBusy(false);
      setPicking(false);
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-1.5">
      {rx.map((r) => (
        <button
          key={r.emoji}
          onClick={() => fire(r.emoji)}
          disabled={busy}
          className={`text-[11px] font-mono px-2 py-0.5 rounded-full border transition-colors ${
            r.mine
              ? "border-ember/60 bg-ember/10 text-ember"
              : "border-white/10 bg-white/5 text-silver-dim hover:border-white/20"
          }`}
        >
          <span className="mr-1">{r.emoji}</span>
          {r.count}
        </button>
      ))}
      <div className="relative">
        <button
          onClick={() => setPicking((v) => !v)}
          className="text-[11px] font-mono px-2 py-0.5 rounded-full border border-white/5 text-muted-foreground hover:text-silver hover:border-white/20"
          aria-label="Add reaction"
        >
          +
        </button>
        {picking && (
          <div className="absolute z-10 top-full mt-1 left-0 flex gap-1 rounded-md border border-white/10 bg-background/95 backdrop-blur p-1 shadow-lg">
            {PICKER.map((e) => (
              <button
                key={e}
                onClick={() => fire(e)}
                className="text-sm px-1.5 py-0.5 rounded hover:bg-white/10"
              >
                {e}
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
