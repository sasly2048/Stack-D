import { useEffect, useRef } from "react";

/**
 * Scroll-driven parallax. Attach `ref` to the section that gates visibility;
 * attach `targetRef` to the inner element that should translate. The transform
 * is written imperatively (no React re-renders per frame) and computation is
 * gated by an IntersectionObserver so off-screen sections cost nothing.
 * Respects prefers-reduced-motion.
 */
export function useParallax<T extends HTMLElement, U extends HTMLElement = HTMLElement>(
  strength = 40,
) {
  const ref = useRef<T | null>(null);
  const targetRef = useRef<U | null>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const reduced = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
    if (reduced) return;

    let raf = 0;
    let ticking = false;
    let visible = false;

    const compute = () => {
      ticking = false;
      const rect = el.getBoundingClientRect();
      const vh = window.innerHeight || 800;
      const progress = (rect.top + rect.height / 2 - vh / 2) / (vh / 2 + rect.height / 2);
      const clamped = Math.max(-1, Math.min(1, progress));
      const y = -clamped * strength;
      const t = targetRef.current;
      if (t) t.style.transform = `translate3d(0, ${y.toFixed(2)}px, 0)`;
    };

    const onScroll = () => {
      if (!visible || ticking) return;
      ticking = true;
      raf = requestAnimationFrame(compute);
    };

    const io = new IntersectionObserver(
      (entries) => {
        visible = entries[0]?.isIntersecting ?? false;
        if (visible) compute();
      },
      { rootMargin: "200px 0px" },
    );
    io.observe(el);

    compute();
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onScroll);
    return () => {
      io.disconnect();
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onScroll);
      cancelAnimationFrame(raf);
    };
  }, [strength]);

  return { ref, targetRef, offset: 0 };
}
