import { useEffect, useState } from "react";

import { haptic } from "@/lib/haptics";
import { useLowPower } from "@/hooks/use-low-power";
import { playProSfx } from "@/lib/celebration-sfx";

/**
 * PRO celebration — "Understand Your Focus". Motif: crystallization / signal
 * lock. A radial scan sweep resolves into a self-drawing hexagonal sigil that
 * snaps to lock. Precise and analytical, moderate intensity, ~3.5s staged as:
 * reveal → activation (sigil draws + locks) → celebration line → settle.
 *
 * Deliberately shares NO animation code with the Elite celebration.
 */
type Beat = "reveal" | "activate" | "celebrate";

export function CelebratePro({ open, onClose }: { open: boolean; onClose: () => void }) {
  const lowPower = useLowPower();
  const [beat, setBeat] = useState<Beat>("reveal");

  useEffect(() => {
    if (!open) return;
    setBeat("reveal");
    haptic("select");
    playProSfx();

    const t1 = setTimeout(() => {
      setBeat("activate");
      haptic("success");
    }, 700);
    const t2 = setTimeout(() => setBeat("celebrate"), 1900);
    return () => {
      clearTimeout(t1);
      clearTimeout(t2);
    };
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  if (!open) return null;

  const drawn = beat !== "reveal";

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-live="polite"
      aria-label="Pro membership activated"
      className="fixed inset-0 z-[110] flex items-center justify-center overflow-hidden bg-obsidian/95 backdrop-blur-md animate-fade-in"
      onClick={onClose}
    >
      {/* Scan sweep — a single ember line crossing once. */}
      {!lowPower && (
        <div
          className="pointer-events-none absolute inset-x-0 h-px bg-gradient-to-r from-transparent via-ember to-transparent"
          style={{ animation: "pro-scan 1.1s cubic-bezier(0.32,0.72,0,1) forwards", top: 0 }}
        />
      )}

      <div
        className="relative flex flex-col items-center px-6 text-center"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Self-drawing hexagonal sigil */}
        <svg width="132" height="132" viewBox="0 0 132 132" className="mb-8" aria-hidden="true">
          <polygon
            points="66,10 114,38 114,94 66,122 18,94 18,38"
            fill="none"
            stroke="#f0a968"
            strokeWidth="1.5"
            strokeDasharray="360"
            strokeDashoffset={drawn ? 0 : 360}
            style={{
              transition: lowPower ? "none" : "stroke-dashoffset 1.1s cubic-bezier(0.32,0.72,0,1)",
              filter: beat === "celebrate" ? "drop-shadow(0 0 10px rgba(240,169,104,0.7))" : "none",
            }}
          />
          {/* Inner lock mark, appears at activation */}
          <circle
            cx="66"
            cy="66"
            r="6"
            fill="#f0a968"
            style={{
              opacity: drawn ? 1 : 0,
              transform: drawn ? "scale(1)" : "scale(0)",
              transformOrigin: "66px 66px",
              transition: lowPower ? "none" : "all 0.4s cubic-bezier(0.32,0.72,0,1) 0.8s",
            }}
          />
        </svg>

        <p className="font-mono text-[10px] tracking-[0.4em] uppercase text-ember">
          {beat === "reveal" ? "Acquiring signal" : "Signal locked"}
        </p>
        <p className="mt-3 font-serif text-5xl text-silver leading-none">Pro</p>
        <p
          className="mt-4 max-w-xs text-sm text-silver-dim"
          style={{
            opacity: beat === "celebrate" ? 1 : 0,
            transition: lowPower ? "none" : "opacity 0.5s ease-out",
          }}
        >
          Your focus, now fully mapped. Analytics, DNA and unlimited history are yours.
        </p>

        <button
          onClick={onClose}
          className="mt-8 rounded-full border border-ember/50 px-6 py-2 font-mono text-[11px] uppercase tracking-widest text-ember transition hover:bg-ember/10"
          style={{
            opacity: beat === "celebrate" ? 1 : 0,
            pointerEvents: beat === "celebrate" ? "auto" : "none",
            transition: lowPower ? "none" : "opacity 0.5s ease-out 0.15s",
          }}
        >
          Understand your focus
        </button>
      </div>
    </div>
  );
}
