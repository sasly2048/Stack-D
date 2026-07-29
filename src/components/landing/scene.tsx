import { useEffect, useRef, type ReactNode } from "react";

/**
 * Scene — one immersive "page" of the landing narrative.
 *
 * Every scene is a full-viewport stage with its own hairline seam, chapter
 * index and label, so the story reads as discrete acts rather than one
 * continuous scroll. Spacing, type scale and label treatment are identical
 * across all six scenes to keep the rhythm perfectly consistent.
 *
 * Cinematic layer: each scene fades to black at both seams (a camera cut
 * between chapters) and lifts its content into place the first time it
 * enters the viewport.
 */
export function Scene({
  id,
  index,
  label,
  children,
  className = "",
  contentClassName = "",
  background,
  tone = "obsidian",
  compact = false,
  seam = true,
  glow = "center",
  handoff = true,
}: {
  id?: string;
  /** Two-digit act number, e.g. "01". */
  index: string;
  /** Short act name shown next to the index. */
  label: string;
  children: ReactNode;
  className?: string;
  contentClassName?: string;
  /** Absolutely-positioned decorative layers (particles, rays, depth). */
  background?: ReactNode;
  tone?: "obsidian" | "raised" | "void";
  /** Drop the full-viewport floor (used when a pinned track follows). */
  compact?: boolean;
  /** Cinematic fade-to-black seams at the top and bottom edges. */
  seam?: boolean;
  /**
   * Where the scene's key light sits. Giving each act a different light
   * position is what stops six stacked sections reading as one scroll.
   */
  glow?: "center" | "top-left" | "top-right" | "bottom-left" | "bottom-right" | "none";
  /** Bottom hairline + descending cue that hands the eye to the next act. */
  handoff?: boolean;
}) {
  const toneClass =
    tone === "raised" ? "bg-neutral-900/30" : tone === "void" ? "bg-black" : "bg-obsidian";

  const glowPos: Record<string, string> = {
    center: "60% 45% at 50% 42%",
    "top-left": "55% 45% at 12% 14%",
    "top-right": "55% 45% at 88% 16%",
    "bottom-left": "58% 48% at 10% 88%",
    "bottom-right": "58% 48% at 90% 86%",
  };

  const contentRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = contentRef.current;
    if (!el) return;
    if (window.matchMedia?.("(prefers-reduced-motion: reduce)").matches) {
      el.style.opacity = "1";
      return;
    }
    const io = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) {
          el.style.animation = "scene-rise 0.9s var(--ease-ritual) both";
          io.disconnect();
        }
      },
      { rootMargin: "-8% 0px -12% 0px" },
    );
    io.observe(el);
    return () => io.disconnect();
  }, []);

  return (
    <section
      id={id}
      data-scene={index}
      className={`relative isolate flex scroll-mt-20 flex-col justify-center overflow-hidden border-t border-white/5 px-6 ${
        compact ? "pb-8 pt-24 sm:pb-10 sm:pt-28" : "min-h-[100svh] pb-24 pt-28 sm:pb-28 sm:pt-32"
      } ${toneClass} ${className}`}
    >
      {background}

      {/* Key light — a different position per act, so no two scenes read alike. */}
      {glow !== "none" && (
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0 -z-[1]"
          style={{
            background: `radial-gradient(${glowPos[glow]}, rgba(240,169,104,0.10) 0%, transparent 70%)`,
          }}
        />
      )}

      {/*
        Camera cut — each scene arrives out of black. Deliberately a single
        top-only fade: a matching bottom fade on the scene above would stack
        with this one into a double black band with a hard seam in the
        middle, which is what read as an "abrupt cut" rather than one
        continuous handoff. One fade per boundary, owned by the incoming
        scene, blends any tone underneath it (raised, void, obsidian) the
        same way, so the join never depends on the two tones matching.
      */}
      {seam && (
        <div
          aria-hidden
          className="pointer-events-none absolute inset-x-0 top-0 z-[5] h-36 bg-gradient-to-b from-black to-transparent sm:h-44"
        />
      )}

      {/* Chapter marker — same position, same type, every scene. */}
      <div className="pointer-events-none absolute left-6 top-20 z-10 flex items-center gap-3 sm:left-10">
        <span className="font-mono text-[10px] uppercase tracking-[0.3em] text-ember">{index}</span>
        <span aria-hidden className="h-px w-8 bg-white/15" />
        <span className="font-mono text-[10px] uppercase tracking-[0.3em] text-muted-foreground">
          {label}
        </span>
      </div>

      <div
        ref={contentRef}
        className={`relative z-10 mx-auto w-full max-w-6xl ${contentClassName}`}
      >
        {children}
      </div>

      {/* Hand-off — a short descending rule that says "this act is finished". */}
      {handoff && !compact && (
        <div
          aria-hidden
          className="pointer-events-none absolute inset-x-0 bottom-8 z-10 flex flex-col items-center gap-2"
        >
          <span className="h-10 w-px bg-gradient-to-b from-transparent via-ember/40 to-transparent" />
          <span className="size-1 rounded-full bg-ember/60" />
        </div>
      )}
    </section>
  );
}

/** Oversized scene headline — one shared type ramp for the whole story. */
export function SceneTitle({
  children,
  as: Tag = "h2",
  className = "",
}: {
  children: ReactNode;
  as?: "h1" | "h2";
  className?: string;
}) {
  return (
    <Tag
      className={`text-balance text-[clamp(2.75rem,7vw,5rem)] font-extrabold leading-[0.9] tracking-tighter ${className}`}
    >
      {children}
    </Tag>
  );
}

/** Body copy at the single shared measure and colour. */
export function SceneLede({
  children,
  className = "",
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <p
      className={`max-w-[38ch] text-pretty text-[1.0625rem] leading-[1.65] text-silver-dim sm:max-w-xl sm:text-lg ${className}`}
    >
      {children}
    </p>
  );
}
