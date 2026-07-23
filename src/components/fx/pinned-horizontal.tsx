import { useEffect, useRef, type ReactNode } from "react";
import { gsap } from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";

if (typeof window !== "undefined") {
  gsap.registerPlugin(ScrollTrigger);
}

/**
 * PinnedHorizontal — sticky section that translates its inner track
 * horizontally as the user scrolls vertically. Classic Awwwards pinned
 * scene. Height of the outer wrapper is derived from the track width so
 * the pin length matches the horizontal distance to travel.
 *
 * Usage:
 *   <PinnedHorizontal>
 *     <div className="flex gap-8 pl-6">
 *       <Card /> <Card /> ...
 *     </div>
 *   </PinnedHorizontal>
 */
export function PinnedHorizontal({
  children,
  className = "",
  trackClassName = "",
  extraPin = 0.15,
}: {
  children: ReactNode;
  className?: string;
  trackClassName?: string;
  /** Extra pin length as a fraction of viewport height (breathing room at edges). */
  extraPin?: number;
}) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const pinRef = useRef<HTMLDivElement>(null);
  const trackRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const wrap = wrapRef.current;
    const pin = pinRef.current;
    const track = trackRef.current;
    if (!wrap || !pin || !track || typeof window === "undefined") return;
    const reduced = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
    if (reduced) {
      // On reduced-motion: don't pin; expose horizontally scrollable fallback.
      track.style.overflowX = "auto";
      return;
    }

    const ctx = gsap.context(() => {
      const setSize = () => {
        const distance = Math.max(0, track.scrollWidth - window.innerWidth);
        wrap.style.height = `${window.innerHeight + distance + window.innerHeight * extraPin}px`;
        return distance;
      };

      let distance = setSize();

      const tween = gsap.to(track, {
        x: () => `-${distance}px`,
        ease: "none",
        scrollTrigger: {
          trigger: wrap,
          pin,
          start: "top top",
          end: () => `+=${distance + window.innerHeight * extraPin}`,
          scrub: 0.6,
          invalidateOnRefresh: true,
        },
      });

      const onResize = () => {
        distance = setSize();
        tween.scrollTrigger?.refresh();
      };
      window.addEventListener("resize", onResize);
      return () => window.removeEventListener("resize", onResize);
    }, wrap);

    return () => ctx.revert();
  }, [extraPin]);

  return (
    <div ref={wrapRef} className={`relative ${className}`}>
      <div ref={pinRef} className="h-screen w-full overflow-hidden flex items-center">
        <div ref={trackRef} className={`flex will-change-transform ${trackClassName}`}>
          {children}
        </div>
      </div>
    </div>
  );
}
