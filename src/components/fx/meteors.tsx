import { useMemo } from "react";

/** Deterministic pseudo-random so SSR and client markup match exactly. */
function seeded(seed: number) {
  let s = seed >>> 0;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 4294967296;
  };
}

/**
 * Meteors — diagonal streaks falling across the container. Absolute,
 * pointer-events-none, purely decorative.
 */
export function Meteors({ count = 20, className = "" }: { count?: number; className?: string }) {
  const meteors = useMemo(() => {
    const rand = seeded(count * 9301 + 49297);
    return Array.from({ length: count }).map((_, i) => ({
      left: `${(rand() * 100).toFixed(3)}%`,
      top: `${(rand() * -40).toFixed(3)}%`,
      delay: `${(rand() * 6).toFixed(3)}s`,
      duration: `${(4 + rand() * 6).toFixed(3)}s`,
      length: Number((60 + rand() * 120).toFixed(2)),
      key: i,
    }));
  }, [count]);


  return (
    <div
      aria-hidden="true"
      className={`pointer-events-none absolute inset-0 overflow-hidden ${className}`}
    >
      {meteors.map((m) => (
        <span
          key={m.key}
          className="absolute block h-[1px] rotate-[215deg] rounded-full bg-gradient-to-l from-silver/70 to-transparent"
          style={{
            left: m.left,
            top: m.top,
            width: `${m.length}px`,
            animation: `meteor ${m.duration} linear ${m.delay} infinite`,
            filter: "drop-shadow(0 0 6px rgba(226,226,226,0.4))",
          }}
        />
      ))}
    </div>
  );
}
