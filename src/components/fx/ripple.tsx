/** Concentric ripple rings — silver at low opacity, decorative only. */
export function Ripple({
  className = "",
  rings = 5,
}: {
  className?: string;
  rings?: number;
}) {
  return (
    <div
      aria-hidden="true"
      className={`pointer-events-none absolute inset-0 flex items-center justify-center overflow-hidden ${className}`}
    >
      {Array.from({ length: rings }).map((_, i) => {
        const size = 120 + i * 110;
        const opacity = 0.14 - i * 0.022;
        const delay = i * 0.6;
        return (
          <span
            key={i}
            className="absolute rounded-full border border-silver/30"
            style={{
              width: `${size}px`,
              height: `${size}px`,
              opacity: Math.max(opacity, 0.02),
              animation: `ripple-pulse 5.5s var(--ease-ritual) ${delay}s infinite`,
            }}
          />
        );
      })}
    </div>
  );
}
