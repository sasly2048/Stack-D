import { type ReactNode } from "react";

/**
 * OrbitingCircles — a stack of concentric orbit rings with items rotating
 * around a central node.
 *
 * Fully viewport-responsive: `size` is a CSS length (defaults to
 * `clamp(280px, 82vw, 520px)`) and orbit radii are given as a fraction of
 * half-size (0..1), so ring positions, item offsets and marker sizes all
 * scale together. On a 390px phone the whole ring stays comfortably inside
 * the viewport; on desktop it fills its slot without stretching.
 */
export function OrbitingCircles({
  center,
  orbits,
  size = "clamp(280px, 82vw, 520px)",
  labelGutter = "clamp(30px, 6vw, 42px)",
  className = "",
}: {
  center: ReactNode;
  orbits: Array<{
    /** Fraction of half-size, 0..1. e.g. 0.4 → 40% of radius. */
    radius: number;
    duration: number;
    reverse?: boolean;
    items: ReactNode[];
  }>;
  size?: string | number;
  /** Clear space between the outer ring and canvas edge for orbit labels. */
  labelGutter?: string | number;
  className?: string;
}) {
  const cssSize = typeof size === "number" ? `${size}px` : size;
  const cssGutter = typeof labelGutter === "number" ? `${labelGutter}px` : labelGutter;

  return (
    <div
      data-orbit-root
      className={`relative shrink-0 ${className}`}
      style={{
        width: cssSize,
        aspectRatio: "1 / 1",
        // Establish an inline-size container so children can resolve the
        // orbit diameter in `cqi` units. Percentages inside translate() would
        // otherwise resolve against each child's own (zero) box.
        containerType: "inline-size",
        ["--orbit-gutter" as string]: cssGutter,
        ["--orbit-diameter" as string]: `calc(100cqi - (${cssGutter} * 2))`,
      }}
      aria-hidden="true"
    >

      {/* Ring outlines */}
      {orbits.map((o, i) => (
        <div
          key={`ring-${i}`}
          data-orbit-ring
          className="absolute left-1/2 top-1/2 rounded-full border border-white/10"
          style={{
            width: `calc(var(--orbit-diameter) * ${o.radius})`,
            height: `calc(var(--orbit-diameter) * ${o.radius})`,
            transform: "translate(-50%, -50%)",
          }}
        />
      ))}

      {/* Orbits */}
      {orbits.map((o, i) => {
        const ringSize = `calc(var(--orbit-diameter) * ${o.radius})`;
        return (
          <div
            key={`orbit-${i}`}
            className="absolute left-1/2 top-1/2"
            style={{
              width: ringSize,
              height: ringSize,
              // Center this ring on the orbit root, then spin a child inside
              // it. Keeping the spin on a separate element preserves centering
              // (otherwise the rotate() transform overrides translate(-50%)).
              transform: "translate(-50%, -50%)",
            }}
          >
            <div
              className="absolute inset-0 rounded-full"
              style={{
                animation: `orbit-spin ${o.duration}s linear infinite`,
                animationDirection: o.reverse ? "reverse" : "normal",
              }}
            >
              {o.items.map((item, j) => {
                const angle = (360 / o.items.length) * j;
                return (
                  <div
                    key={j}
                    className="absolute left-1/2 top-1/2 w-0 h-0"
                    style={{
                      transform: `rotate(${angle}deg) translateY(calc(${ringSize} / -2))`,
                    }}
                  >
                    <div
                      data-orbit-item
                      className="inline-block -translate-x-1/2 -translate-y-1/2"
                      style={{
                        animation: `orbit-counter ${o.duration}s linear infinite`,
                        animationDirection: o.reverse ? "reverse" : "normal",
                      }}
                    >
                      <div style={{ transform: `rotate(-${angle}deg)` }}>
                        {item}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        );
      })}


      {/* Center */}
      <div className="absolute inset-0 flex items-center justify-center">
        {center}
      </div>
    </div>
  );
}
