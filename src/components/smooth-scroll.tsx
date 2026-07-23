import { useEffect, type ReactNode } from "react";
import Lenis from "lenis";
import { gsap } from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";

if (typeof window !== "undefined") {
  gsap.registerPlugin(ScrollTrigger);
}

/**
 * SmoothScroll — global Lenis instance that drives page scroll with inertia,
 * and syncs GSAP ScrollTrigger to Lenis's virtual scroll so scrubbed
 * animations stay in perfect step. Respects prefers-reduced-motion (falls
 * back to native scroll silently).
 *
 * Mount ONCE, near the top of the tree (in __root RootComponent).
 */
export function SmoothScroll({ children }: { children: ReactNode }) {
  useEffect(() => {
    if (typeof window === "undefined") return;
    const reduced = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
    if (reduced) return;

    const lenis = new Lenis({
      // Awwwards-tier feel: long inertia, near-zero touch friction.
      duration: 1.15,
      easing: (t: number) => Math.min(1, 1.001 - Math.pow(2, -10 * t)),
      wheelMultiplier: 1,
      touchMultiplier: 1.4,
      smoothWheel: true,
      // `smoothTouch` doesn't exist on Lenis v1 types; touch inertia is
      // controlled by touchMultiplier + native touch scrolling.
    });

    // Drive Lenis from GSAP's ticker so ScrollTrigger stays in sync.
    lenis.on("scroll", ScrollTrigger.update);
    const tick = (time: number) => lenis.raf(time * 1000);
    gsap.ticker.add(tick);
    gsap.ticker.lagSmoothing(0);

    // Expose so anchor-scroll utilities can use it (nav "Philosophy" link etc).
    (window as unknown as { __lenis?: Lenis }).__lenis = lenis;

    return () => {
      gsap.ticker.remove(tick);
      lenis.destroy();
      delete (window as unknown as { __lenis?: Lenis }).__lenis;
    };
  }, []);

  return <>{children}</>;
}
