/**
 * Haptic feedback wrapper. Silently no-ops on desktop/unsupported devices.
 * Keep patterns short and semantic so callers don't invent their own timings.
 */
export type HapticKind = "tap" | "select" | "success" | "warn" | "error" | "heavy";

const PATTERNS: Record<HapticKind, number | number[]> = {
  tap: 8,
  select: 12,
  success: [12, 40, 20],
  warn: [30, 60, 30],
  error: [60, 40, 60, 40, 60],
  heavy: 100,
};

export function haptic(kind: HapticKind = "tap") {
  try {
    if (typeof navigator === "undefined" || !("vibrate" in navigator)) return;
    if (typeof matchMedia !== "undefined" && matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    navigator.vibrate(PATTERNS[kind]);
  } catch {
    /* noop */
  }
}
