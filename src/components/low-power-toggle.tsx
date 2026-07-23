import { useLowPower, setLowPower } from "@/hooks/use-low-power";

/**
 * Small pill toggle for Low Power Mode. Renders in Profile / Settings so
 * users on lower-end devices can silence particles + parallax.
 */
export function LowPowerToggle() {
  const on = useLowPower();
  return (
    <div className="border border-white/10 rounded-md p-4 flex items-center justify-between bg-black/40">
      <div>
        <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Low Power</p>
        <p className="mt-1 text-sm text-silver">Trim particles, meteors, and parallax.</p>
        <p className="text-xs text-silver-dim mt-1">
          Auto-enables on reduced-motion or low battery.
        </p>
      </div>
      <button
        onClick={() => setLowPower(!on)}
        role="switch"
        aria-checked={on}
        className={`relative w-12 h-6 rounded-full transition ${on ? "bg-ember" : "bg-white/10"}`}
      >
        <span
          className={`absolute top-0.5 left-0.5 w-5 h-5 rounded-full bg-obsidian transition-transform ${on ? "translate-x-6" : ""}`}
        />
      </button>
    </div>
  );
}
