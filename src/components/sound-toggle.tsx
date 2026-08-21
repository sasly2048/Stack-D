import { useState } from "react";

import { soundEnabled, setSoundEnabled, sfx } from "@/lib/sfx";

/**
 * Pill toggle for UI sound effects. Sits in Profile next to Low Power so users
 * can silence audio feedback. Haptics stay independent (governed by
 * reduced-motion + device support).
 */
export function SoundToggle() {
  const [on, setOn] = useState(() => soundEnabled());

  const toggle = () => {
    const next = !on;
    setSoundEnabled(next);
    setOn(next);
    if (next) sfx("select"); // a little confirmation when turning it back on
  };

  return (
    <div className="border border-white/10 rounded-md p-4 flex items-center justify-between bg-black/40">
      <div>
        <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Sound</p>
        <p className="mt-1 text-sm text-silver">Subtle sound effects on key actions.</p>
        <p className="text-xs text-silver-dim mt-1">Haptics follow your device settings.</p>
      </div>
      <button
        onClick={toggle}
        role="switch"
        aria-checked={on}
        aria-label="Toggle sound effects"
        className={`relative w-12 h-6 rounded-full transition ${on ? "bg-ember" : "bg-white/10"}`}
      >
        <span
          className={`absolute top-0.5 left-0.5 w-5 h-5 rounded-full bg-obsidian transition-transform ${on ? "translate-x-6" : ""}`}
        />
      </button>
    </div>
  );
}
