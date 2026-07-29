import { useEffect, useRef, useState } from "react";

/** Marquee row that accelerates with scroll velocity. */
export function ScrollVelocity({
  words,
  className = "",
  baseSpeed = 40, // px/s
}: {
  words: string[];
  className?: string;
  baseSpeed?: number;
}) {
  const trackRef = useRef<HTMLDivElement>(null);
  const [offset, setOffset] = useState(0);
  const velRef = useRef(0);
  const lastYRef = useRef(0);
  const lastTRef = useRef(0);

  useEffect(() => {
    const reduced = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
    if (reduced) return;

    let raf = 0;
    let prev = performance.now();
    const tick = (now: number) => {
      const dt = (now - prev) / 1000;
      prev = now;
      // decay scroll-added velocity
      velRef.current *= 0.92;
      const speed = baseSpeed + velRef.current;
      setOffset((o) => {
        const track = trackRef.current;
        if (!track) return o;
        const half = track.scrollWidth / 2;
        let next = o - speed * dt;
        if (half > 0 && -next >= half) next += half;
        if (half > 0 && next > 0) next -= half;
        return next;
      });
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);

    const onScroll = () => {
      const y = window.scrollY;
      const now = performance.now();
      const dt = Math.max(now - lastTRef.current, 16) / 1000;
      const dy = y - lastYRef.current;
      lastYRef.current = y;
      lastTRef.current = now;
      // add scroll speed (px/s) into velocity, capped
      const add = Math.max(-1200, Math.min(1200, dy / dt));
      velRef.current += add * 0.15;
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener("scroll", onScroll);
    };
  }, [baseSpeed]);

  const items = [...words, ...words];

  return (
    <div className={`overflow-hidden ${className}`} aria-hidden="true">
      <div
        ref={trackRef}
        className="flex whitespace-nowrap will-change-transform"
        style={{ transform: `translate3d(${offset}px,0,0)` }}
      >
        {items.map((w, i) => (
          <span
            key={i}
            className="font-mono text-[clamp(2.5rem,8vw,6rem)] uppercase tracking-[0.15em] text-silver-dim/40 px-8 select-none"
          >
            {w}
            <span className="text-ember/50 mx-6">·</span>
          </span>
        ))}
      </div>
    </div>
  );
}
