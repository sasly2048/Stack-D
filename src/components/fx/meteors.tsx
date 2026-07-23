import { useMemo } from "react";

/**
 * Meteors — diagonal streaks falling across the container. Absolute,
 * pointer-events-none, purely decorative.
 */
export function Meteors({
  count = 20,
  className = "",
}: {
  count?: number;
  className?: string;
}) {
  const meteors = useMemo(
    () =>
      Array.from({ length: count }).map((_, i) => ({
        left: `${Math.random() * 100}%`,
        top: `${Math.random() * -40}%`,
        delay: `${Math.random() * 6}s`,
        duration: `${4 + Math.random() * 6}s`,
        length: 60 + Math.random() * 120,
        key: i,
      })),
    [count],
  );

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
