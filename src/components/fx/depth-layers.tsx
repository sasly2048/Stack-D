import { useEffect, useRef } from "react";
import { gsap } from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";

if (typeof window !== "undefined") {
  gsap.registerPlugin(ScrollTrigger);
}

/**
 * DepthLayers — ambient, absolute-positioned layered orbs and grain that
 * translate at different scroll multipliers to fake z-depth. Pointer-events
 * off; purely decorative. Mount inside a `relative overflow-hidden` section.
 */
export function DepthLayers({
  className = "",
  ember = true,
}: {
  className?: string;
  ember?: boolean;
}) {
  const root = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = root.current;
    if (!el || typeof window === "undefined") return;
    const reduced = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
    if (reduced) return;

    const ctx = gsap.context(() => {
      const layers = el.querySelectorAll<HTMLElement>("[data-depth]");
      layers.forEach((layer) => {
        const depth = parseFloat(layer.dataset.depth || "0.5");
        gsap.to(layer, {
          yPercent: -30 * depth,
          ease: "none",
          scrollTrigger: {
            trigger: el,
            start: "top bottom",
            end: "bottom top",
            scrub: true,
          },
        });
      });
    }, el);

    return () => ctx.revert();
  }, []);

  return (
    <div
      ref={root}
      aria-hidden
      className={`pointer-events-none absolute inset-0 overflow-hidden ${className}`}
    >
      {/* Far grain — barely moves */}
      <div
        data-depth="0.15"
        className="absolute inset-0 opacity-[0.06] mix-blend-overlay"
        style={{
          backgroundImage:
            "radial-gradient(rgba(255,255,255,0.6) 1px, transparent 1px)",
          backgroundSize: "3px 3px",
        }}
      />
      {/* Mid ember orb */}
      {ember && (
        <div
          data-depth="0.55"
          className="absolute -left-[10%] top-[20%] size-[55vmin] rounded-full opacity-[0.18] blur-[80px]"
          style={{ background: "radial-gradient(circle, #F0A968 0%, transparent 65%)" }}
        />
      )}
      {/* Near silver orb */}
      <div
        data-depth="0.9"
        className="absolute -right-[15%] bottom-[10%] size-[65vmin] rounded-full opacity-[0.08] blur-[100px]"
        style={{ background: "radial-gradient(circle, #E2E2E2 0%, transparent 60%)" }}
      />
      {/* Foreground vignette */}
      <div
        data-depth="1.2"
        className="absolute inset-0"
        style={{
          background:
            "radial-gradient(ellipse at center, transparent 55%, rgba(0,0,0,0.55) 100%)",
        }}
      />
    </div>
  );
}
