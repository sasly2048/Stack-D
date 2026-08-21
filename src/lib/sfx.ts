/**
 * Lightweight UI sound effects — synthesized via WebAudio, no audio assets.
 * A small semantic vocabulary so callers pick an intent, not a frequency, and
 * so the app never uses one generic blip everywhere.
 *
 * Respect: silent when the user has muted sounds (soundEnabled pref) or the
 * browser can't play audio (no AudioContext, autoplay-blocked before a
 * gesture). Reduced-motion is honored by the paired haptics; audio itself is
 * gated by the explicit sound preference.
 *
 * Kept intentionally quiet (low gains) and short (< ~0.6s) so it reads as
 * tactile, not noisy.
 */

const SOUND_KEY = "stackd:sound";

/** Sound on unless the user turned it off. */
export function soundEnabled(): boolean {
  if (typeof window === "undefined") return false;
  try {
    return localStorage.getItem(SOUND_KEY) !== "0";
  } catch {
    return true;
  }
}

export function setSoundEnabled(on: boolean) {
  try {
    localStorage.setItem(SOUND_KEY, on ? "1" : "0");
  } catch {
    /* noop */
  }
}

export type SfxKind =
  | "tap" // generic button press
  | "select" // toggle / tab / choice
  | "open" // modal / menu opens
  | "close" // modal / menu dismiss
  | "success" // form submit ok, generic positive
  | "error" // failure / warning
  | "auth" // sign-in / sign-up landed
  | "xp" // xp gained
  | "achievement" // achievement / streak milestone
  | "notify" // incoming notification
  | "purchase"; // subscription selected / checkout opened

let ctx: AudioContext | null = null;
function getCtx(): AudioContext | null {
  if (typeof window === "undefined" || !soundEnabled()) return null;
  const Ctor =
    window.AudioContext ||
    (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!Ctor) return null;
  try {
    ctx ??= new Ctor();
    // A gesture may be needed to resume; best-effort.
    if (ctx.state === "suspended") void ctx.resume().catch(() => undefined);
    return ctx;
  } catch {
    return null;
  }
}

/**
 * Browsers create an AudioContext 'suspended' and only let it start inside a
 * user gesture. Many of our sounds fire programmatically (after a setTimeout,
 * on a route change, from a webhook poll), which is NOT a gesture — so without
 * this, the first such sound is silently dropped. Prime and resume the context
 * on the first real gesture so every later sound just works.
 */
let unlockInstalled = false;
/**
 * Arm the audio unlock. MUST be called eagerly at app start (from __root), not
 * lazily when the first sound plays — otherwise the listener may not exist yet
 * when the user's first gesture happens, and the context is never resumed. The
 * unlock force-creates the context (regardless of the sound pref, so it's ready
 * if the user turns sound on later) and resumes it inside the gesture, which is
 * the only place a browser will let it start.
 */
export function primeAudio() {
  if (typeof window === "undefined" || unlockInstalled) return;
  unlockInstalled = true;

  const unlock = () => {
    const Ctor =
      window.AudioContext ||
      (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctor) return;
    try {
      ctx ??= new Ctor();
      // resume() inside the gesture is what actually starts the context.
      void ctx.resume().catch(() => undefined);
      // Silent tick to fully wake the pipeline so the first real sound isn't
      // clipped.
      const o = ctx.createOscillator();
      const g = ctx.createGain();
      g.gain.value = 0;
      o.connect(g).connect(ctx.destination);
      o.start();
      o.stop(ctx.currentTime + 0.01);
    } catch {
      /* noop */
    }
    // Keep listening — a single gesture may fire before the context is fully
    // running on some browsers, and re-resuming is harmless. Remove after a
    // couple of gestures to avoid leaking.
    gestureCount++;
    if (gestureCount >= 3) {
      window.removeEventListener("pointerdown", unlock);
      window.removeEventListener("keydown", unlock);
      window.removeEventListener("touchstart", unlock);
    }
  };
  let gestureCount = 0;
  window.addEventListener("pointerdown", unlock);
  window.addEventListener("keydown", unlock);
  window.addEventListener("touchstart", unlock);
}

interface Note {
  f: number; // freq
  t: number; // start offset (s)
  d: number; // duration (s)
  type?: OscillatorType;
  g?: number; // peak gain
  to?: number; // glide target freq
}

function play(notes: Note[]) {
  const c = getCtx();
  if (!c) return;
  const now = c.currentTime;
  for (const n of notes) {
    const osc = c.createOscillator();
    const gain = c.createGain();
    osc.type = n.type ?? "sine";
    osc.frequency.setValueAtTime(n.f, now + n.t);
    if (n.to) osc.frequency.exponentialRampToValueAtTime(n.to, now + n.t + n.d);
    const peak = n.g ?? 0.06;
    gain.gain.setValueAtTime(0.0001, now + n.t);
    gain.gain.exponentialRampToValueAtTime(peak, now + n.t + 0.012);
    gain.gain.exponentialRampToValueAtTime(0.0001, now + n.t + n.d);
    osc.connect(gain).connect(c.destination);
    osc.start(now + n.t);
    osc.stop(now + n.t + n.d + 0.03);
  }
}

// Distinct designs per kind. Low gains, short durations — subtle by design.
const SFX: Record<SfxKind, () => void> = {
  tap: () => play([{ f: 660, t: 0, d: 0.05, type: "sine", g: 0.04 }]),
  select: () => play([{ f: 880, t: 0, d: 0.06, type: "triangle", g: 0.05 }]),
  open: () => play([{ f: 520, t: 0, d: 0.09, type: "sine", g: 0.05, to: 720 }]),
  close: () => play([{ f: 620, t: 0, d: 0.09, type: "sine", g: 0.045, to: 440 }]),
  success: () =>
    play([
      { f: 660, t: 0, d: 0.09, type: "triangle", g: 0.06 },
      { f: 990, t: 0.07, d: 0.16, type: "sine", g: 0.06 },
    ]),
  error: () =>
    play([
      { f: 300, t: 0, d: 0.12, type: "sawtooth", g: 0.05, to: 180 },
      { f: 220, t: 0.1, d: 0.14, type: "square", g: 0.04 },
    ]),
  auth: () =>
    play([
      { f: 523, t: 0, d: 0.1, type: "sine", g: 0.05 },
      { f: 784, t: 0.09, d: 0.22, type: "sine", g: 0.06 },
    ]),
  xp: () => play([{ f: 1046, t: 0, d: 0.12, type: "triangle", g: 0.05, to: 1568 }]),
  achievement: () =>
    play([
      { f: 659, t: 0, d: 0.1, type: "triangle", g: 0.06 },
      { f: 880, t: 0.08, d: 0.1, type: "triangle", g: 0.06 },
      { f: 1318, t: 0.16, d: 0.26, type: "sine", g: 0.06 },
    ]),
  notify: () =>
    play([
      { f: 880, t: 0, d: 0.07, type: "sine", g: 0.045 },
      { f: 1174, t: 0.06, d: 0.12, type: "sine", g: 0.045 },
    ]),
  purchase: () =>
    play([
      { f: 587, t: 0, d: 0.09, type: "triangle", g: 0.05 },
      { f: 880, t: 0.07, d: 0.14, type: "sine", g: 0.055 },
    ]),
};

/** Play a UI sound. Silent if sound is off or audio is unavailable. */
export function sfx(kind: SfxKind) {
  try {
    SFX[kind]?.();
  } catch {
    /* noop */
  }
}
