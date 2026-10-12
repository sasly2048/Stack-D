import { useEffect, useRef, useState } from "react";
import { haptic } from "@/lib/haptics";

/**
 * Hold-to-confirm for consequential, irreversible actions (ending or aborting
 * a live stack, lifting the stack in the ceremony). A click does nothing: the
 * fill only advances while held, falls back on early release, and onConfirm
 * fires once it completes. Keyboard users hold Space/Enter the same way.
 * Mirrors Android's HoldButton.
 */
export function HoldButton({
  label,
  onConfirm,
  tone = "muted",
  holdMs = 1100,
  className = "",
}: {
  label: string;
  onConfirm: () => void;
  tone?: "muted" | "breach";
  holdMs?: number;
  className?: string;
}) {
  const [progress, setProgress] = useState(0);
  const raf = useRef(0);
  const start = useRef<number | null>(null);
  const fired = useRef(false);
  const latest = useRef(onConfirm);
  latest.current = onConfirm;

  const stop = () => {
    cancelAnimationFrame(raf.current);
    start.current = null;
    if (!fired.current) setProgress(0);
  };

  const begin = () => {
    if (start.current !== null || fired.current) return;
    haptic("tap");
    start.current = performance.now();
    const tick = (t: number) => {
      const p = Math.min(1, (t - (start.current ?? t)) / holdMs);
      setProgress(p);
      if (p >= 1) {
        fired.current = true;
        haptic("success");
        latest.current();
        return;
      }
      raf.current = requestAnimationFrame(tick);
    };
    raf.current = requestAnimationFrame(tick);
  };

  useEffect(() => () => cancelAnimationFrame(raf.current), []);

  const color =
    tone === "breach" ? "text-breach border-breach/40" : "text-silver-dim border-white/15";
  const fill = tone === "breach" ? "bg-breach/20" : "bg-white/10";
  return (
    <button
      type="button"
      onPointerDown={begin}
      onPointerUp={stop}
      onPointerLeave={stop}
      onPointerCancel={stop}
      onKeyDown={(e) => {
        if ((e.key === " " || e.key === "Enter") && !e.repeat) {
          e.preventDefault();
          begin();
        }
      }}
      onKeyUp={stop}
      onContextMenu={(e) => e.preventDefault()}
      className={`relative w-full select-none overflow-hidden rounded-lg border py-3 font-mono text-xs uppercase tracking-widest transition-colors ${color} ${className}`}
    >
      <span
        aria-hidden="true"
        className={`absolute inset-y-0 left-0 ${fill}`}
        style={{ width: `${progress * 100}%` }}
      />
      <span className="relative">{label}</span>
    </button>
  );
}
