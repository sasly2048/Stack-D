import { haptic, type HapticKind } from "./haptics";
import { sfx, type SfxKind } from "./sfx";

/**
 * One call for tactile feedback: fires the matching sound AND vibration for an
 * interaction, so callers express intent once and the two stay in sync. Sound
 * is gated by the sound pref; haptics by reduced-motion + device support — each
 * degrades independently.
 *
 * Use these semantic intents rather than calling sfx()/haptic() ad hoc, so the
 * app has a consistent feedback language instead of one blip everywhere.
 */
export type FeedbackKind =
  | "tap" // button press
  | "select" // toggle / tab / choice
  | "open" // modal / menu opens
  | "close" // modal / menu closes
  | "submit" // form submission
  | "success" // positive result
  | "error" // failure / warning
  | "auth" // sign-in / sign-up
  | "xp" // xp gained
  | "achievement" // achievement / streak
  | "notify" // notification arrived
  | "purchase" // subscription selected / checkout opened
  | "activate"; // subscription activated (the big one — celebration handles its own)

const MAP: Record<FeedbackKind, { sfx: SfxKind; haptic: HapticKind }> = {
  tap: { sfx: "tap", haptic: "tap" },
  select: { sfx: "select", haptic: "select" },
  open: { sfx: "open", haptic: "tap" },
  close: { sfx: "close", haptic: "tap" },
  submit: { sfx: "select", haptic: "select" },
  success: { sfx: "success", haptic: "success" },
  error: { sfx: "error", haptic: "error" },
  auth: { sfx: "auth", haptic: "success" },
  xp: { sfx: "xp", haptic: "tap" },
  achievement: { sfx: "achievement", haptic: "success" },
  notify: { sfx: "notify", haptic: "tap" },
  purchase: { sfx: "purchase", haptic: "select" },
  activate: { sfx: "purchase", haptic: "heavy" },
};

export function feedback(kind: FeedbackKind) {
  const m = MAP[kind];
  if (!m) return;
  sfx(m.sfx);
  haptic(m.haptic);
}
