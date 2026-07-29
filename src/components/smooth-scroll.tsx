import { useEffect, type ReactNode } from "react";

/**
 * SmoothScroll — global Lenis instance that drives page scroll with inertia,
 * and syncs GSAP ScrollTrigger to Lenis's virtual scroll so scrubbed
 * animations stay in perfect step. Respects prefers-reduced-motion (falls
 * back to native scroll silently).
 *
 * Lenis + GSAP (~300 KB raw) are imported dynamically after mount so they
 * never land in the critical entry chunk — first paint and TTI don't pay for
 * scroll polish, and reduced-motion users never download them at all.
 *
 * Mount ONCE, near the top of the tree (in __root RootComponent).
 */
export function SmoothScroll({ children }: { children: ReactNode }) {
  useEffect(() => {
    if (typeof window === "undefined") return;
    const reduced = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
    if (reduced) return;

    let dispose: (() => void) | undefined;
    let cancelled = false;

    const boot = async () => {
      const [{ default: Lenis }, { gsap }, { ScrollTrigger }] = await Promise.all([
        import("lenis"),
        import("gsap"),
        import("gsap/ScrollTrigger"),
      ]);
      if (cancelled) return;
      gsap.registerPlugin(ScrollTrigger);

      const lenis = new Lenis({
        // Awwwards-tier feel: long inertia, near-zero touch friction.
        duration: 1.15,
        easing: (t: number) => Math.min(1, 1.001 - Math.pow(2, -10 * t)),
        wheelMultiplier: 1,
        touchMultiplier: 1.4,
        smoothWheel: true,
      });

      // Drive Lenis from GSAP's ticker so ScrollTrigger stays in sync.
      lenis.on("scroll", ScrollTrigger.update);
      const tick = (time: number) => lenis.raf(time * 1000);
      gsap.ticker.add(tick);
      gsap.ticker.lagSmoothing(0);

      // Expose so anchor-scroll utilities can use it (nav "Philosophy" link etc).
      (window as unknown as { __lenis?: unknown }).__lenis = lenis;

      dispose = () => {
        gsap.ticker.remove(tick);
        lenis.destroy();
        delete (window as unknown as { __lenis?: unknown }).__lenis;
      };
    };

    // Idle-boot so it never competes with hydration.
    const idle =
      window.requestIdleCallback?.(() => void boot()) ?? window.setTimeout(() => void boot(), 200);

    return () => {
      cancelled = true;
      window.cancelIdleCallback?.(idle as number);
      window.clearTimeout(idle as number);
      dispose?.();
    };
  }, []);

  return <>{children}</>;
}
