/**
 * The decision rules behind breach detection, as pure functions.
 *
 * These used to live inline in the `useSensors` effect, where they could only
 * be exercised by mounting a component and faking DeviceMotion events — so in
 * practice they were never tested at all, despite deciding whether someone's
 * session survives. Pulled out here so the thresholds can be asserted directly.
 *
 * Nothing in this file touches the DOM or React.
 */

/** Orientation sample, degrees. */
export type Orientation = { beta: number; gamma: number };

/**
 * Collect orientation for this long before deciding what "flat" is. Long
 * enough for a hand to leave the phone, short enough not to delay the session.
 */
export const CALIBRATION_MS = 800;
/** Guards against a device that emits orientation very slowly. */
export const CALIBRATION_MIN_SAMPLES = 5;

/** Window over which shake peaks are counted. */
export const SHAKE_WINDOW_MS = 600;
/** Exceedances required inside the window before a shake is real. */
export const SHAKE_MIN_PEAKS = 3;

/**
 * Whether enough evidence has accrued to fix the baseline.
 *
 * Both conditions matter: the elapsed time lets the phone settle, and the
 * sample count stops a device that emits orientation once a second from
 * arming on two readings.
 */
export function isCalibrationComplete(sampleCount: number, elapsedMs: number): boolean {
  return elapsedMs >= CALIBRATION_MS && sampleCount >= CALIBRATION_MIN_SAMPLES;
}

/**
 * The resting orientation, taken as the median of the calibration samples.
 *
 * Median rather than mean: the last samples before a phone comes to rest are
 * the noisiest, and one wild reading drags a mean while barely moving a
 * median. The original code used the *first* sample outright, which meant a
 * phone caught mid-placement defined a tilted pose as level — every later
 * reading was then measured against a wrong zero.
 */
export function computeBaseline(samples: readonly Orientation[]): Orientation | null {
  if (samples.length === 0) return null;
  const betas = samples.map((s) => s.beta).sort((a, b) => a - b);
  const gammas = samples.map((s) => s.gamma).sort((a, b) => a - b);
  const mid = betas.length >> 1;
  return { beta: betas[mid], gamma: gammas[mid] };
}

/**
 * Whether sustained agitation is present in the window.
 *
 * A single sample over threshold is a table bump, a dropped book, a passing
 * truck. Real shaking produces repeated peaks. The original test fired an
 * irreversible severe breach on one sample, so honest users lost sessions to
 * ambient vibration.
 */
export function isShake(
  window: ReadonlyArray<{ mag: number; at: number }>,
  threshold: number,
  now: number,
): boolean {
  const peaks = window.filter((s) => now - s.at <= SHAKE_WINDOW_MS && s.mag > threshold).length;
  return peaks >= SHAKE_MIN_PEAKS;
}

/** Drops samples that have aged out, keeping the window bounded. */
export function pruneWindow<T extends { at: number }>(
  window: readonly T[],
  now: number,
  maxAgeMs: number = SHAKE_WINDOW_MS,
): T[] {
  return window.filter((s) => now - s.at <= maxAgeMs);
}

/**
 * Whether a tilt delta counts as a "lift" rather than a lean.
 *
 * 90° means the phone has been turned over or picked up, which is categorical
 * enough to skip the hold timer.
 */
export function isLift(deltaBeta: number, deltaGamma: number): boolean {
  return deltaBeta > 90 || deltaGamma > 90;
}
