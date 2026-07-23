import { useEffect, useRef, useState } from "react";

/** Cycles each glyph through 0/1 for a short window, then resolves to the target character. */
export function MatrixText({
  text,
  className = "",
  duration = 550,
  stagger = 45,
}: {
  text: string;
  className?: string;
  duration?: number;
  stagger?: number;
}) {
  const [chars, setChars] = useState<string[]>(() => text.split(""));
  const ref = useRef<HTMLSpanElement>(null);
  const startedRef = useRef(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const reduced = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
    if (reduced) {
      setChars(text.split(""));
      return;
    }

    const run = () => {
      if (startedRef.current) return;
      startedRef.current = true;
      const target = text.split("");
      const started = performance.now();
      const resolved = new Array(target.length).fill(false);

      let raf = 0;
      const tick = (now: number) => {
        const next = target.map((c, i) => {
          if (c === " ") return c;
          const startAt = i * stagger;
          const elapsed = now - started - startAt;
          if (elapsed < 0) return "";
          if (elapsed > duration) {
            resolved[i] = true;
            return c;
          }
          return Math.random() > 0.5 ? "1" : "0";
        });
        setChars(next);
        if (resolved.every(Boolean)) return;
        raf = requestAnimationFrame(tick);
      };
      raf = requestAnimationFrame(tick);
      return () => cancelAnimationFrame(raf);
    };

    const io = new IntersectionObserver(
      (entries) => {
        for (const e of entries) if (e.isIntersecting) run();
      },
      { threshold: 0.4 },
    );
    io.observe(el);
    return () => io.disconnect();
  }, [text, duration, stagger]);

  return (
    <span ref={ref} className={className} aria-label={text}>
      <span aria-hidden="true" className="tabular-nums">
        {chars.map((c, i) => (
          <span key={i} style={{ display: "inline-block", minWidth: c === "" ? "0.5em" : undefined }}>
            {c || "\u00A0"}
          </span>
        ))}
      </span>
    </span>
  );
}
