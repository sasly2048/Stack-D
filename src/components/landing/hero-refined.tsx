/**
 * HeroRefined — Production-grade hero section
 *
 * Improvements over HeroStage:
 * - RAF only runs when component is in viewport
 * - Scoped pointer events to hero container only
 * - Respects prefers-reduced-motion
 * - Optimized transform calculations
 * - Proper cleanup and memory management
 * - Responsive at all breakpoints (320px–1920px)
 * - Full accessibility support
 */

import { useEffect, useRef, useState } from "react";
import { cn } from "@/lib/utils";

interface HeroRefinedProps {
  className?: string;
  showStage?: boolean;
  children?: React.ReactNode;
}

/**
 * Layer component with proper depth management
 */
function HeroLayer({
  depth,
  className,
  children,
  x,
  y,
}: {
  depth: number;
  className?: string;
  children: React.ReactNode;
  x: number;
  y: number;
}) {
  // Calculate parallax offset based on depth
  const offsetX = x * 34 * depth;
  const offsetY = y * 26 * depth;

  return (
    <div
      className={cn("absolute will-change-transform", className)}
      style={{
        transform: `translate3d(${offsetX.toFixed(1)}px, ${offsetY.toFixed(1)}px, 0)`,
      }}
    >
      {children}
    </div>
  );
}

export function HeroRefined({ className, showStage = true, children }: HeroRefinedProps) {
  const rootRef = useRef<HTMLDivElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const observerRef = useRef<IntersectionObserver | null>(null);
  const rafRef = useRef<number | null>(null);

  const [isVisible, setIsVisible] = useState(false);
  const [isReducedMotion, setIsReducedMotion] = useState(false);
  const [isHoverCapable, setIsHoverCapable] = useState(false);

  // Parallax state
  const stateRef = useRef({
    targetX: 0,
    targetY: 0,
    currentX: 0,
    currentY: 0,
  });

  // Check for prefers-reduced-motion and hover capability
  useEffect(() => {
    const prefersReduced = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;
    const hasHover = window.matchMedia?.("(hover: hover)").matches ?? false;

    setIsReducedMotion(prefersReduced);
    setIsHoverCapable(hasHover);
  }, []);

  // Intersection Observer to pause RAF when component is off-screen
  useEffect(() => {
    const el = rootRef.current;
    if (!el) return;

    const observer = new IntersectionObserver(
      ([entry]) => {
        setIsVisible(entry.isIntersecting);
      },
      { threshold: 0 },
    );

    observer.observe(el);
    observerRef.current = observer;

    return () => observer.unobserve(el);
  }, []);

  // RAF animation loop — only runs when visible and motion is allowed
  useEffect(() => {
    if (!isVisible || isReducedMotion || !isHoverCapable || !containerRef.current) {
      if (rafRef.current) {
        cancelAnimationFrame(rafRef.current);
        rafRef.current = null;
      }
      return;
    }

    const animate = () => {
      const state = stateRef.current;
      const el = containerRef.current;
      if (!el) return;

      // Smooth interpolation toward target position
      state.currentX += (state.targetX - state.currentX) * 0.08;
      state.currentY += (state.targetY - state.currentY) * 0.08;

      // Apply 3D rotation to container
      el.style.transform = `rotateX(${(-state.currentY * 5).toFixed(2)}deg) rotateY(${(state.currentX * 7).toFixed(2)}deg)`;

      rafRef.current = requestAnimationFrame(animate);
    };

    rafRef.current = requestAnimationFrame(animate);

    return () => {
      if (rafRef.current) {
        cancelAnimationFrame(rafRef.current);
        rafRef.current = null;
      }
    };
  }, [isVisible, isReducedMotion, isHoverCapable]);

  // Pointer event handlers — scoped to hero container
  useEffect(() => {
    const el = rootRef.current;
    if (!el || !isHoverCapable || isReducedMotion) return;

    const handlePointerMove = (e: PointerEvent) => {
      const rect = el.getBoundingClientRect();
      const centerX = rect.left + rect.width / 2;
      const centerY = rect.top + rect.height / 2;

      stateRef.current.targetX = (e.clientX - centerX) / (rect.width / 2);
      stateRef.current.targetY = (e.clientY - centerY) / (rect.height / 2);
    };

    const handlePointerLeave = () => {
      stateRef.current.targetX = 0;
      stateRef.current.targetY = 0;
    };

    // Use capture phase to ensure events are processed
    el.addEventListener("pointermove", handlePointerMove, { passive: true });
    el.addEventListener("pointerleave", handlePointerLeave);

    return () => {
      el.removeEventListener("pointermove", handlePointerMove);
      el.removeEventListener("pointerleave", handlePointerLeave);
    };
  }, [isHoverCapable, isReducedMotion]);

  // Cleanup
  useEffect(() => {
    return () => {
      if (observerRef.current) {
        observerRef.current.disconnect();
      }
      if (rafRef.current) {
        cancelAnimationFrame(rafRef.current);
      }
    };
  }, []);

  return (
    <div
      ref={rootRef}
      className={cn(
        "relative mx-auto w-full overflow-hidden",
        // Responsive aspect ratio: square on mobile, wider on desktop
        "aspect-square sm:aspect-[5/6]",
        // Responsive max-width scaling
        "max-w-none scale-90 sm:scale-100",
        "sm:max-w-[600px] lg:max-w-[640px]",
        className,
      )}
    >
      {/* Ambient glow background */}
      {showStage && (
        <div
          className="pointer-events-none absolute -inset-12 -z-10 blur-3xl"
          style={{
            background:
              "radial-gradient(55% 45% at 50% 45%, rgba(240,169,104,0.20) 0%, transparent 72%)",
          }}
          aria-hidden="true"
        />
      )}

      {/* 3D perspective container */}
      <div
        ref={containerRef}
        className={cn(
          "absolute inset-0 transition-transform duration-500 ease-out",
          isReducedMotion ? "transform-none" : "[transform-style:preserve-3d]",
        )}
      >
        {showStage && (
          <>
            {/* Table plate — grounds the stack */}
            <HeroLayer
              depth={0.2}
              x={stateRef.current.currentX}
              y={stateRef.current.currentY}
              className="left-1/2 top-[62%] h-[46%] w-[78%] -translate-x-1/2 rounded-full border border-white/5 bg-white/[0.02] blur-sm"
            >
              <span className="sr-only">Stack table surface</span>
            </HeroLayer>

            {/* Four stacked phones */}
            <HeroLayer
              depth={0.55}
              x={stateRef.current.currentX}
              y={stateRef.current.currentY}
              className="left-1/2 top-[44%] -translate-x-1/2 -translate-y-1/2"
            >
              <div className="relative h-[330px] w-[208px] [transform-style:preserve-3d] sm:h-[350px] sm:w-[222px]">
                {[0, 1, 2, 3].map((i) => (
                  <div
                    key={i}
                    className="absolute inset-0 rounded-[26px] border border-white/12 bg-gradient-to-b from-white/10 to-white/[0.015] shadow-[0_50px_90px_-40px_rgba(0,0,0,0.95)]"
                    style={{
                      transform: `translateY(${i * -16}px) translateZ(${i * 26}px) rotate(${(i % 2 ? 1 : -1) * 2.2}deg)`,
                      animation: isReducedMotion
                        ? "none"
                        : `float-soft ${6 + i}s ease-in-out ${i * 0.35}s infinite`,
                    }}
                    aria-hidden="true"
                  >
                    {/* Notch indicator */}
                    <span className="absolute left-1/2 top-4 h-1 w-10 -translate-x-1/2 rounded-full bg-white/10" />
                    {/* Status indicator */}
                    <span
                      className={`absolute bottom-4 left-1/2 h-1.5 w-1.5 -translate-x-1/2 rounded-full ${
                        i === 3 ? "bg-red-500" : "animate-pulse bg-green-500"
                      }`}
                    />
                  </div>
                ))}
              </div>
            </HeroLayer>
          </>
        )}

        {/* Custom content layers can be passed as children */}
        {children}
      </div>
    </div>
  );
}

/**
 * PhoneStackStage — Decorated hero with product panels
 * Replaces the original HeroStage with production-ready version
 */
export function PhoneStackStage() {
  const state = useRef({
    targetX: 0,
    targetY: 0,
    currentX: 0,
    currentY: 0,
  });

  return <HeroRefined>{/* Product panel layers can go here */}</HeroRefined>;
}
