import { useEffect, useRef, useState, type ReactNode } from "react";
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
/** Below this width the pin is replaced by native horizontal scrolling (Tailwind `md`). */
const PIN_MIN_WIDTH = 768;

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
  // Bumped on a breakpoint crossing so the effect re-runs and swaps modes —
  // otherwise a rotate from portrait to landscape keeps whichever branch was
  // chosen at mount.
  const [layoutEpoch, setLayoutEpoch] = useState(0);

  useEffect(() => {
    if (typeof window === "undefined") return;
    const mq = window.matchMedia(`(min-width: ${PIN_MIN_WIDTH}px)`);
    const onChange = () => setLayoutEpoch((n) => n + 1);
    mq.addEventListener("change", onChange);
    return () => mq.removeEventListener("change", onChange);
  }, []);

  useEffect(() => {
    const wrap = wrapRef.current;
    const pin = pinRef.current;
    const track = trackRef.current;
    if (!wrap || !pin || !track || typeof window === "undefined") return;

    const reduced = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
    // Scroll-jacking a phone is the worst version of this effect: the track is
    // several viewports wide, so the user loses vertical control for a long
    // stretch and can't flick past it. Below `md` we hand back a plain swipe.
    const narrow = window.innerWidth < PIN_MIN_WIDTH;

    if (reduced || narrow) {
      // Native swipe fallback. The overflow lives on the pin box (the track is
      // a flex row sized by its children, so it has nothing to overflow), and
      // touch-action keeps vertical page scroll gestures working over the
      // cards. Height must be auto so the section doesn't clip its content.
      pin.style.overflowX = "auto";
      pin.style.overflowY = "visible";
      pin.style.touchAction = "pan-x pan-y";
      pin.style.overscrollBehaviorX = "contain";
      pin.style.height = "auto";
      wrap.style.height = "auto";
      return () => {
        pin.style.overflowX = "";
        pin.style.overflowY = "";
        pin.style.touchAction = "";
        pin.style.overscrollBehaviorX = "";
        pin.style.height = "";
        wrap.style.height = "";
      };
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

    return () => {
      ctx.revert();
      // ctx.revert() restores what GSAP set, but the wrapper height is ours.
      wrap.style.height = "";
    };
  }, [extraPin, layoutEpoch]);

  return (
    <div ref={wrapRef} className={`relative ${className}`}>
      <div
        ref={pinRef}
        className="w-full flex items-center overflow-x-auto md:overflow-x-hidden md:h-screen md:overflow-hidden"
      >

        <div ref={trackRef} className={`flex will-change-transform ${trackClassName}`}>
          {children}
        </div>
      </div>
    </div>
  );
}
