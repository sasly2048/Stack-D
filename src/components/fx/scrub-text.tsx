import { useEffect, useRef, type ElementType, type ReactNode } from "react";
import { gsap } from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";

if (typeof window !== "undefined") {
  gsap.registerPlugin(ScrollTrigger);
}

/**
 * ScrubText — splits children into words (or optionally chars) and reveals
 * them driven by scroll progress across the container. Awwwards-style:
 * each word rises, unblurs, and de-clips as it hits mid-viewport.
 *
 * Pass plain string children only (no nested markup). For structured
 * markup, compose multiple ScrubText spans.
 */
export function ScrubText({
  as: Tag = "span",
  children,
  className = "",
  splitBy = "word",
  from = { y: 40, opacity: 0.15, blur: 8 },
  start = "top 85%",
  end = "top 30%",
  stagger = 0.05,
}: {
  as?: ElementType;
  children: string | ReactNode;
  className?: string;
  splitBy?: "word" | "char";
  from?: { y?: number; opacity?: number; blur?: number };
  start?: string;
  end?: string;
  stagger?: number;
}) {
  const ref = useRef<HTMLElement | null>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el || typeof window === "undefined") return;
    const reduced = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;

    const parts = el.querySelectorAll<HTMLElement>("[data-scrub-part]");
    if (!parts.length) return;

    if (reduced) {
      parts.forEach((p) => {
        p.style.opacity = "1";
        p.style.transform = "none";
        p.style.filter = "none";
      });
      return;
    }

    const ctx = gsap.context(() => {
      gsap.set(parts, {
        yPercent: from.y ?? 40,
        opacity: from.opacity ?? 0.15,
        filter: `blur(${from.blur ?? 8}px)`,
        willChange: "transform, opacity, filter",
      });

      gsap.to(parts, {
        yPercent: 0,
        opacity: 1,
        filter: "blur(0px)",
        ease: "power2.out",
        stagger,
        scrollTrigger: {
          trigger: el,
          start,
          end,
          scrub: 0.6,
        },
      });
    }, el);

    return () => ctx.revert();
  }, [from.y, from.opacity, from.blur, start, end, stagger]);

  // Only string children get split — nested elements pass through unchanged.
  const content =
    typeof children === "string"
      ? (() => {
          if (splitBy === "char") {
            return Array.from(children).map((c, i) => (
              <span
                key={i}
                data-scrub-part
                className="inline-block will-change-transform"
                style={{ whiteSpace: c === " " ? "pre" : undefined }}
              >
                {c}
              </span>
            ));
          }
          return children.split(/(\s+)/).map((w, i) =>
            /\s+/.test(w) ? (
              <span key={i}> </span>
            ) : (
              <span
                key={i}
                data-scrub-part
                className="inline-block will-change-transform"
              >
                {w}
              </span>
            ),
          );
        })()
      : children;

  return (
    <Tag ref={ref as never} className={className}>
      {content}
    </Tag>
  );
}
