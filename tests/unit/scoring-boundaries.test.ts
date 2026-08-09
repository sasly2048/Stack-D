import { describe, expect, it } from "vitest";

import { computeFocusScore, SCORING_VERSION, tierForScore } from "@/lib/focus-score";

/**
 * Guards the tier boundaries, where the reward curve is steepest.
 *
 * The bug these lock in: `Math.round(raw)` ran *before* the tier lookup, so a
 * raw score of 84.5 rounded to 85, crossed into "pristine", and doubled the XP
 * multiplier (0.5 → 1.0). Half a point of real performance became a 2x reward.
 * Tier and XP now derive from the unrounded score; only the displayed number
 * is rounded.
 */

/** Focus seconds that produce a given raw score with no breaches. */
const focusFor = (rawScore: number, target: number) => (rawScore / 100) * target;
const TARGET = 3600;

describe("tier boundaries are not crossed by display rounding", () => {
  it("keeps a raw 84.5 in the lower tier, despite displaying as 85", () => {
    const r = computeFocusScore({
      targetSeconds: TARGET,
      focusSeconds: focusFor(84.5, TARGET),
      breaches: [],
    });
    // Displayed value rounds up — that part is fine and expected.
    expect(r.score).toBe(85);
    // But the reward must follow the real performance, not the rounded label.
    expect(r.tier.key).toBe("steady");
  });

  it("awards the higher tier only once the raw score genuinely reaches it", () => {
    const r = computeFocusScore({
      targetSeconds: TARGET,
      focusSeconds: focusFor(85, TARGET),
      breaches: [],
    });
    expect(r.tier.key).toBe("pristine");
  });

  it("does not jump XP discontinuously across the boundary", () => {
    const below = computeFocusScore({
      targetSeconds: TARGET,
      focusSeconds: focusFor(84.99, TARGET),
      breaches: [],
    });
    const at = computeFocusScore({
      targetSeconds: TARGET,
      focusSeconds: focusFor(85.0, TARGET),
      breaches: [],
    });
    // A boundary still exists by design (the multiplier changes), but the
    // *input* either side of it must differ by a hair, not by a rounding step.
    // Before the fix, 84.5 and 85.0 produced identical tiers and XP.
    expect(below.tier.key).not.toBe(at.tier.key);
    expect(at.xp).toBeGreaterThan(below.xp);
  });

  it("is monotonic — more focus never yields less XP", () => {
    let prevXp = -1;
    for (let raw = 0; raw <= 100; raw += 0.5) {
      const r = computeFocusScore({
        targetSeconds: TARGET,
        focusSeconds: focusFor(raw, TARGET),
        breaches: [],
      });
      expect(r.xp).toBeGreaterThanOrEqual(prevXp);
      prevXp = r.xp;
    }
  });

  it("clamps to the valid range at both ends", () => {
    const over = computeFocusScore({
      targetSeconds: TARGET,
      focusSeconds: TARGET * 2,
      breaches: [],
    });
    expect(over.score).toBeLessThanOrEqual(100);

    const under = computeFocusScore({
      targetSeconds: TARGET,
      focusSeconds: 0,
      breaches: [],
    });
    expect(under.score).toBeGreaterThanOrEqual(0);
    expect(under.xp).toBeGreaterThanOrEqual(0);
  });
});

describe("tierForScore accepts fractional input", () => {
  it("does not round its argument internally", () => {
    // If tierForScore rounded, 84.9 would land in "pristine".
    expect(tierForScore(84.9).key).toBe("steady");
    expect(tierForScore(85).key).toBe("pristine");
  });
});

describe("scoring version", () => {
  it("is stamped on every result so history stays interpretable", () => {
    const r = computeFocusScore({
      targetSeconds: TARGET,
      focusSeconds: TARGET,
      breaches: [],
    });
    expect(r.scoringVersion).toBe(SCORING_VERSION);
    expect(SCORING_VERSION).toBeGreaterThanOrEqual(2);
  });
});
