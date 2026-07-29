import { useEffect, useRef, useState } from "react";

/**
 * Returns [ref, inView] — flips inView to true once the target enters the
 * viewport (with rootMargin) and stays true afterwards. Used to defer heavy
 * FX (map, meteors) until the user actually scrolls to them.
 */
export function useInView<T extends HTMLElement>(rootMargin = "300px 0px") {
  const ref = useRef<T | null>(null);
  const [inView, setInView] = useState(false);
  useEffect(() => {
    const el = ref.current;
    if (!el || inView) return;
    const io = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) {
          setInView(true);
          io.disconnect();
        }
      },
      { rootMargin },
    );
    io.observe(el);
    return () => io.disconnect();
  }, [inView, rootMargin]);
  return [ref, inView] as const;
}
