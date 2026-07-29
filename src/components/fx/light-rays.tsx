/**
 * LightRays — soft radial rays that breathe. Sits behind session content
 * to add depth without motion sickness. Purely decorative.
 */
export function LightRays({
  className = "",
  color = "rgba(240,169,104,0.10)",
  rays = 10,
}: {
  className?: string;
  color?: string;
  rays?: number;
}) {
  return (
    <div
      aria-hidden="true"
      className={`pointer-events-none absolute inset-0 flex items-center justify-center overflow-hidden ${className}`}
    >
      <div
        className="relative"
        style={{
          width: "140%",
          aspectRatio: "1 / 1",
          animation: "rays-breathe 6.5s ease-in-out infinite",
        }}
      >
        {Array.from({ length: rays }).map((_, i) => {
          const angle = (180 / rays) * i;
          return (
            <span
              key={i}
              className="absolute left-1/2 top-1/2 block origin-center"
              style={{
                width: "2px",
                height: "100%",
                marginLeft: "-1px",
                marginTop: "-50%",
                background: `linear-gradient(to bottom, transparent 0%, ${color} 45%, ${color} 55%, transparent 100%)`,
                transform: `rotate(${angle}deg)`,
                filter: "blur(4px)",
              }}
            />
          );
        })}
      </div>
    </div>
  );
}
