import { describe, expect, it } from "vitest";

import {
  CALIBRATION_MIN_SAMPLES,
  computeBaseline,
  isCalibrationComplete,
  isLift,
  isShake,
  pruneWindow,
  SHAKE_WINDOW_MS,
} from "@/lib/sensor-rules";

/**
 * These guard the two rules that decide whether an honest user keeps their
 * session. Both were previously single-sample judgements with no test.
 */

describe("baseline calibration", () => {
  it("does not arm on the first sample, however long the wait", () => {
    // The original bug: baseline = first reading, taken while the phone is
    // still moving toward the table.
    expect(isCalibrationComplete(1, 10_000)).toBe(false);
  });

  it("does not arm before the settle window elapses", () => {
    expect(isCalibrationComplete(50, 100)).toBe(false);
  });

  it("arms once both time and sample count are satisfied", () => {
    expect(isCalibrationComplete(CALIBRATION_MIN_SAMPLES, 800)).toBe(true);
  });

  it("takes the median, so one wild placement sample cannot define level", () => {
    // Four readings of a flat phone plus one taken mid-drop.
    const samples = [
      { beta: 2, gamma: 1 },
      { beta: 3, gamma: 2 },
      { beta: 2, gamma: 1 },
      { beta: 74, gamma: 61 }, // the jolt
      { beta: 3, gamma: 2 },
    ];
    const baseline = computeBaseline(samples);
    // A mean would land near beta 16.8 and declare a tilted phone "flat".
    expect(baseline).toEqual({ beta: 3, gamma: 2 });
  });

  it("returns null with nothing to average", () => {
    expect(computeBaseline([])).toBeNull();
  });
});

describe("shake detection", () => {
  const now = 10_000;
  const threshold = 16;

  it("ignores a single spike — a table bump must not end a session", () => {
    const window = [{ mag: 40, at: now - 10 }];
    expect(isShake(window, threshold, now)).toBe(false);
  });

  it("ignores two spikes, still short of sustained agitation", () => {
    const window = [
      { mag: 40, at: now - 20 },
      { mag: 38, at: now - 10 },
    ];
    expect(isShake(window, threshold, now)).toBe(false);
  });

  it("fires on sustained agitation inside the window", () => {
    const window = [
      { mag: 40, at: now - 30 },
      { mag: 38, at: now - 20 },
      { mag: 42, at: now - 10 },
    ];
    expect(isShake(window, threshold, now)).toBe(true);
  });

  it("does not accumulate peaks across unrelated bumps far apart in time", () => {
    // Three spikes, but spread beyond the window — three separate jolts, not
    // one shake. Without the age check these would sum into a false breach.
    const window = [
      { mag: 40, at: now - 5000 },
      { mag: 38, at: now - 3000 },
      { mag: 42, at: now - 10 },
    ];
    expect(isShake(window, threshold, now)).toBe(false);
  });

  it("ignores sustained motion below the threshold", () => {
    const window = [
      { mag: 5, at: now - 30 },
      { mag: 6, at: now - 20 },
      { mag: 5, at: now - 10 },
    ];
    expect(isShake(window, threshold, now)).toBe(false);
  });

  it("prunes aged samples so the window stays bounded", () => {
    const window = [
      { at: now - SHAKE_WINDOW_MS - 1 },
      { at: now - 10 },
    ];
    expect(pruneWindow(window, now)).toEqual([{ at: now - 10 }]);
  });
});

describe("lift classification", () => {
  it("treats a large delta on either axis as a lift", () => {
    expect(isLift(91, 0)).toBe(true);
    expect(isLift(0, 91)).toBe(true);
  });

  it("leaves an ordinary tilt to the hold timer", () => {
    expect(isLift(61, 45)).toBe(false);
  });
});
