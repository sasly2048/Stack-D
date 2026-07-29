/**
 * Landing page performance and accessibility optimization hooks
 */

import { useEffect, useRef, useCallback, useState } from "react";

/**
 * useViewportPause — Pause animations when component is off-screen
 * Improves performance by avoiding unnecessary render cycles
 */
export function useViewportPause() {
  const ref = useRef<HTMLElement>(null);
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;

    const observer = new IntersectionObserver(([entry]) => setIsVisible(entry.isIntersecting), {
      threshold: 0.1,
    });

    observer.observe(el);
    return () => observer.unobserve(el);
  }, []);

  return { ref, isVisible };
}

/**
 * useReducedMotion — Detect prefers-reduced-motion media query
 */
export function useReducedMotion(): boolean {
  const [prefersReduced, setPrefersReduced] = useState(false);

  useEffect(() => {
    const mediaQuery = window.matchMedia?.("(prefers-reduced-motion: reduce)");
    setPrefersReduced(mediaQuery?.matches ?? false);

    const listener = (e: MediaQueryListEvent) => setPrefersReduced(e.matches);
    mediaQuery?.addEventListener?.("change", listener);

    return () => mediaQuery?.removeEventListener?.("change", listener);
  }, []);

  return prefersReduced;
}

/**
 * useHoverCapable — Detect if device supports hover (mouse/trackpad)
 */
export function useHoverCapable(): boolean {
  const [hasHover, setHasHover] = useState(false);

  useEffect(() => {
    const mediaQuery = window.matchMedia?.("(hover: hover)");
    setHasHover(mediaQuery?.matches ?? false);

    const listener = (e: MediaQueryListEvent) => setHasHover(e.matches);
    mediaQuery?.addEventListener?.("change", listener);

    return () => mediaQuery?.removeEventListener?.("change", listener);
  }, []);

  return hasHover;
}

/**
 * useScrollVelocity — Track scroll velocity for parallax calculations
 */
export function useScrollVelocity() {
  const velocityRef = useRef(0);
  const lastScrollRef = useRef(0);
  const lastTimeRef = useRef(0);

  useEffect(() => {
    const handleScroll = () => {
      const now = Date.now();
      const scrollDelta = window.scrollY - lastScrollRef.current;
      const timeDelta = now - lastTimeRef.current;

      if (timeDelta > 0) {
        velocityRef.current = scrollDelta / timeDelta;
      }

      lastScrollRef.current = window.scrollY;
      lastTimeRef.current = now;
    };

    window.addEventListener("scroll", handleScroll, { passive: true });
    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  return velocityRef.current;
}

/**
 * useFocusTrap — Trap focus within a modal or overlay
 */
export function useFocusTrap(ref: React.RefObject<HTMLElement>, enabled = true) {
  useEffect(() => {
    if (!enabled || !ref.current) return;

    const el = ref.current;
    const focusableElements = el.querySelectorAll(
      "button, [href], input, select, textarea, [tabindex]:not([tabindex='-1'])",
    ) as NodeListOf<HTMLElement>;

    if (focusableElements.length === 0) return;

    const firstElement = focusableElements[0];
    const lastElement = focusableElements[focusableElements.length - 1];

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key !== "Tab") return;

      if (e.shiftKey) {
        if (document.activeElement === firstElement) {
          e.preventDefault();
          lastElement.focus();
        }
      } else {
        if (document.activeElement === lastElement) {
          e.preventDefault();
          firstElement.focus();
        }
      }
    };

    el.addEventListener("keydown", handleKeyDown);
    firstElement.focus();

    return () => el.removeEventListener("keydown", handleKeyDown);
  }, [ref, enabled]);
}

/**
 * useLazyLoad — Lazy load images/iframes with intersection observer
 */
export function useLazyLoad(options?: IntersectionObserverInit) {
  const ref = useRef<HTMLElement>(null);
  const [isLoaded, setIsLoaded] = React.useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;

    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) {
        setIsLoaded(true);
        observer.unobserve(el);
      }
    }, options);

    observer.observe(el);
    return () => observer.unobserve(el);
  }, [options]);

  return { ref, isLoaded };
}

/**
 * useSkipAnimation — Skip animations when user prefers reduced motion
 */
export function useSkipAnimation(): string {
  const prefersReduced = useReducedMotion();
  return prefersReduced ? "none" : "auto";
}

/**
 * useAccessibleButton — Enhance button with keyboard and focus management
 */
export function useAccessibleButton(ref: React.RefObject<HTMLButtonElement>, onPress?: () => void) {
  useEffect(() => {
    const el = ref.current;
    if (!el) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Enter" || e.key === " ") {
        e.preventDefault();
        el.click();
      }
    };

    el.addEventListener("keydown", handleKeyDown);
    return () => el.removeEventListener("keydown", handleKeyDown);
  }, [ref, onPress]);
}

/**
 * useBreakpoint — Detect current breakpoint for responsive behavior
 */
export function useBreakpoint(): "xs" | "sm" | "md" | "lg" | "xl" | "2xl" {
  const [breakpoint, setBreakpoint] = useState<"xs" | "sm" | "md" | "lg" | "xl" | "2xl">("xs");

  useEffect(() => {
    const updateBreakpoint = () => {
      const width = window.innerWidth;
      if (width < 640) setBreakpoint("xs");
      else if (width < 768) setBreakpoint("sm");
      else if (width < 1024) setBreakpoint("md");
      else if (width < 1280) setBreakpoint("lg");
      else if (width < 1536) setBreakpoint("xl");
      else setBreakpoint("2xl");
    };

    updateBreakpoint();
    window.addEventListener("resize", updateBreakpoint);
    return () => window.removeEventListener("resize", updateBreakpoint);
  }, []);

  return breakpoint;
}
