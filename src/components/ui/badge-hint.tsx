import type { ReactNode } from "react";

/**
 * Small contextual markers — "Last used", "Recommended", "New", "Saved".
 *
 * These are the cheapest usability wins in the app: they answer a question the
 * user would otherwise have to answer from memory ("which button did I sign in
 * with?", "which of these is the sensible default?"). Centralised so the whole
 * app speaks with one visual voice instead of each screen inventing a pill.
 *
 * Every tone stays on the existing palette, and all of them sit at or above the
 * AA contrast floor on obsidian.
 */

type Tone = "neutral" | "accent" | "positive" | "info";

const TONES: Record<Tone, string> = {
  // Quiet — a fact about the past, not a call to action.
  neutral: "border-white/15 bg-white/[0.06] text-silver-dim",
  // Ember — the app's "pay attention here" colour.
  accent: "border-ember/40 bg-ember/10 text-ember",
  positive: "border-pulse/40 bg-pulse/10 text-pulse",
  info: "border-white/20 bg-white/10 text-silver",
};

export function BadgeHint({
  children,
  tone = "neutral",
  className = "",
  title,
}: {
  children: ReactNode;
  tone?: Tone;
  className?: string;
  /** Native tooltip for the reasoning, when the label alone is terse. */
  title?: string;
}) {
  return (
    <span
      title={title}
      className={`inline-flex shrink-0 items-center rounded-full border px-2 py-[3px] font-mono text-[9px] uppercase leading-none tracking-[0.18em] ${TONES[tone]} ${className}`}
    >
      {children}
    </span>
  );
}

/**
 * "Last used" specifically. Carries an sr-only prefix because the visual label
 * leans on adjacency to the button it describes, which a screen reader reading
 * linearly does not get.
 */
export function LastUsedBadge({ className = "" }: { className?: string }) {
  return (
    <BadgeHint tone="accent" className={className} title="You signed in with this last time">
      <span className="sr-only">Previously used: </span>
      Last used
    </BadgeHint>
  );
}
