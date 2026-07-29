import { useEffect, useRef, useState, type ElementType, type ReactNode } from "react";

/**
 * TextReveal — fades and lifts text into view as it enters the viewport.
 * When `perWord` is true, splits string children into words and reveals
 * them in sequence for a slower, ritual feel.
 */
export function TextReveal({
  children,
  as: As = "div",
  perWord = false,
  delay = 0,
  className = "",
}: {
  children: ReactNode;
  as?: ElementType;
  perWord?: boolean;
  delay?: number;
  className?: string;
}) {
  const ref = useRef<HTMLElement>(null);
  const [shown, setShown] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const io = new IntersectionObserver(
      (entries) => {
        for (const e of entries)
          if (e.isIntersecting) {
            setShown(true);
            io.disconnect();
          }
      },
      { threshold: 0.2 },
    );
    io.observe(el);
    return () => io.disconnect();
  }, []);

  if (perWord && typeof children === "string") {
    const words = children.split(/(\s+)/);
    return (
      <As ref={ref as never} className={className}>
        {words.map((w, i) =>
          /^\s+$/.test(w) ? (
            <span key={i}>{w}</span>
          ) : (
            <span
              key={i}
              className="inline-block will-change-transform"
              style={{
                opacity: shown ? 1 : 0,
                transform: shown ? "translateY(0)" : "translateY(0.4em)",
                transition: `opacity 700ms ease-out ${delay + i * 40}ms, transform 700ms ease-out ${delay + i * 40}ms`,
              }}
            >
              {w}
            </span>
          ),
        )}
      </As>
    );
  }

  return (
    <As
      ref={ref as never}
      className={`will-change-transform ${className}`}
      style={{
        opacity: shown ? 1 : 0,
        transform: shown ? "translateY(0)" : "translateY(1rem)",
        transition: `opacity 900ms ease-out ${delay}ms, transform 900ms ease-out ${delay}ms`,
      }}
    >
      {children}
    </As>
  );
}
