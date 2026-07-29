import { type ReactNode } from "react";

/**
 * Marquee — infinite horizontal scroller. Renders children twice for a
 * seamless loop; consumers pass a row of cards/quotes.
 */
export function Marquee({
  children,
  reverse = false,
  pauseOnHover = true,
  speedSeconds = 40,
  className = "",
}: {
  children: ReactNode;
  reverse?: boolean;
  pauseOnHover?: boolean;
  speedSeconds?: number;
  className?: string;
}) {
  return (
    <div
      className={`group relative flex overflow-hidden [mask-image:linear-gradient(to_right,transparent,black_10%,black_90%,transparent)] ${className}`}
    >
      {[0, 1].map((i) => (
        <div
          key={i}
          aria-hidden={i === 1 ? true : undefined}
          className={`flex shrink-0 items-stretch gap-6 pr-6 ${
            pauseOnHover ? "group-hover:[animation-play-state:paused]" : ""
          }`}
          style={{
            animation: `marquee-x ${speedSeconds}s linear infinite`,
            animationDirection: reverse ? "reverse" : "normal",
            minWidth: "100%",
          }}
        >
          {children}
        </div>
      ))}
    </div>
  );
}
