import { useEffect, useRef } from "react";

type Options = {
  onSwipeLeft?: () => void;
  onSwipeRight?: () => void;
  onSwipeUp?: () => void;
  onSwipeDown?: () => void;
  threshold?: number;
};

export function useSwipe<T extends HTMLElement>(opts: Options) {
  const ref = useRef<T | null>(null);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const threshold = opts.threshold ?? 60;
    let x0 = 0;
    let y0 = 0;
    let t0 = 0;
    const start = (e: TouchEvent) => {
      const t = e.touches[0];
      x0 = t.clientX;
      y0 = t.clientY;
      t0 = Date.now();
    };
    const end = (e: TouchEvent) => {
      if (Date.now() - t0 > 800) return;
      const t = e.changedTouches[0];
      const dx = t.clientX - x0;
      const dy = t.clientY - y0;
      if (Math.abs(dx) > Math.abs(dy)) {
        if (dx > threshold) opts.onSwipeRight?.();
        else if (dx < -threshold) opts.onSwipeLeft?.();
      } else {
        if (dy > threshold) opts.onSwipeDown?.();
        else if (dy < -threshold) opts.onSwipeUp?.();
      }
    };
    el.addEventListener("touchstart", start, { passive: true });
    el.addEventListener("touchend", end, { passive: true });
    return () => {
      el.removeEventListener("touchstart", start);
      el.removeEventListener("touchend", end);
    };
  }, [opts]);
  return ref;
}
