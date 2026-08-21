/**
 * Synthesized celebration sound — no audio assets, WebAudio only. Two distinct
 * profiles so Pro and Elite sound nothing alike:
 *   - Pro:   a clean two-tone "signal lock" that resolves to one note. Precise,
 *            analytical, short.
 *   - Elite: a layered ascending swell — a low rising drone under a bright
 *            arpeggio that lands on a chord. Grander, longer, exclusive.
 *
 * Both are best-effort: if the browser blocks audio (autoplay policy before a
 * user gesture, no AudioContext), they no-op silently. Call from a click/tap
 * handler so the gesture unlocks audio.
 */

import { soundEnabled } from "./sfx";

function getCtx(): AudioContext | null {
  if (typeof window === "undefined" || !soundEnabled()) return null;
  const Ctor =
    window.AudioContext ||
    (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!Ctor) return null;
  try {
    return new Ctor();
  } catch {
    return null;
  }
}

function tone(
  ctx: AudioContext,
  {
    freq,
    start,
    dur,
    type = "sine",
    gain = 0.15,
    to,
  }: {
    freq: number;
    start: number;
    dur: number;
    type?: OscillatorType;
    gain?: number;
    to?: number; // optional glide target
  },
) {
  const osc = ctx.createOscillator();
  const g = ctx.createGain();
  osc.type = type;
  osc.frequency.setValueAtTime(freq, ctx.currentTime + start);
  if (to) osc.frequency.exponentialRampToValueAtTime(to, ctx.currentTime + start + dur);
  // Short attack, smooth decay — no clicks.
  g.gain.setValueAtTime(0.0001, ctx.currentTime + start);
  g.gain.exponentialRampToValueAtTime(gain, ctx.currentTime + start + 0.02);
  g.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + start + dur);
  osc.connect(g).connect(ctx.destination);
  osc.start(ctx.currentTime + start);
  osc.stop(ctx.currentTime + start + dur + 0.05);
}

/** Pro: a crisp two-note lock resolving to one. ~0.9s. */
export function playProSfx() {
  const ctx = getCtx();
  if (!ctx) return;
  // Two clean tones converging — a "signal acquired" feel.
  tone(ctx, { freq: 587.33, start: 0, dur: 0.22, type: "triangle", gain: 0.12 }); // D5
  tone(ctx, { freq: 880, start: 0.14, dur: 0.5, type: "sine", gain: 0.16 }); // A5, the lock
  // A soft high shimmer on the resolve.
  tone(ctx, { freq: 1760, start: 0.16, dur: 0.28, type: "sine", gain: 0.05 });
  setTimeout(() => ctx.close().catch(() => undefined), 1400);
}

/** Elite: a layered ascending swell landing on a bright chord. ~2.2s. */
export function playEliteSfx() {
  const ctx = getCtx();
  if (!ctx) return;
  // Low rising drone — the "ignition".
  tone(ctx, { freq: 110, start: 0, dur: 1.4, type: "sawtooth", gain: 0.08, to: 220 }); // A2->A3
  // Bright ascending arpeggio (A major-ish) — the ascent.
  const arp = [440, 554.37, 659.25, 880]; // A4 C#5 E5 A5
  arp.forEach((f, i) => {
    tone(ctx, { freq: f, start: 0.25 + i * 0.16, dur: 0.4, type: "triangle", gain: 0.13 });
  });
  // Landing chord shimmer — the "granted".
  [880, 1108.73, 1318.51].forEach((f) => {
    tone(ctx, { freq: f, start: 1.05, dur: 0.9, type: "sine", gain: 0.09 });
  });
  // High sparkle tail.
  tone(ctx, { freq: 2637, start: 1.1, dur: 0.6, type: "sine", gain: 0.04 });
  setTimeout(() => ctx.close().catch(() => undefined), 2600);
}
