import { describe, expect, it } from "vitest";
import {
  computeFocusScore,
  tierForScore,
  MINOR_PENALTY,
  SEVERE_PENALTY,
  ABANDONMENT_GRACE_SECONDS,
} from "@/lib/focus-score";

describe("tierForScore", () => {
  it("maps boundaries to the right tier", () => {
    expect(tierForScore(100).key).toBe("flow");
    expect(tierForScore(95).key).toBe("flow");
    expect(tierForScore(94).key).toBe("pristine");
    expect(tierForScore(85).key).toBe("pristine");
    expect(tierForScore(84).key).toBe("steady");
    expect(tierForScore(70).key).toBe("steady");
    expect(tierForScore(69).key).toBe("fragmented");
    expect(tierForScore(40).key).toBe("fragmented");
    expect(tierForScore(39).key).toBe("compromised");
    expect(tierForScore(0).key).toBe("compromised");
  });
});

describe("computeFocusScore", () => {
  it("awards a perfect clean session", () => {
    const r = computeFocusScore({ targetSeconds: 1800, focusSeconds: 1800, breaches: [] });
    expect(r.score).toBe(100);
    expect(r.tier.key).toBe("flow");
    expect(r.xp).toBe(Math.floor(100 * 30 * 1.5));
  });

  it("applies minor and severe breach penalties", () => {
    const r = computeFocusScore({
      targetSeconds: 600,
      focusSeconds: 600,
      breaches: [{ severity: "minor" }, { severity: "severe" }],
    });
    expect(r.penalty).toBe(MINOR_PENALTY + SEVERE_PENALTY);
    expect(r.score).toBe(100 - MINOR_PENALTY - SEVERE_PENALTY);
  });

  it("only penalises abandonment past the grace window", () => {
    const within = computeFocusScore({
      targetSeconds: 600,
      focusSeconds: 600,
      breaches: [],
      abandonmentSeconds: ABANDONMENT_GRACE_SECONDS,
    });
    expect(within.abandonmentPenalty).toBe(0);

    const past = computeFocusScore({
      targetSeconds: 600,
      focusSeconds: 600,
      breaches: [],
      abandonmentSeconds: ABANDONMENT_GRACE_SECONDS + 12,
    });
    expect(past.abandonmentPenalty).toBe(12);
    expect(past.score).toBe(88);
  });

  it("clamps focus time to the target and never goes negative", () => {
    const over = computeFocusScore({ targetSeconds: 300, focusSeconds: 9000, breaches: [] });
    expect(over.focusSecondsInt).toBe(300);
    expect(over.score).toBe(100);

    const wrecked = computeFocusScore({
      targetSeconds: 300,
      focusSeconds: 300,
      breaches: Array.from({ length: 5 }, () => ({ severity: "severe" as const })),
    });
    expect(wrecked.score).toBe(0);
    expect(wrecked.xp).toBe(0);
  });

  it("keeps ms precision internally and floors seconds at the DB boundary", () => {
    const r = computeFocusScore({ targetSeconds: 600, focusSeconds: 599.87, breaches: [] });
    expect(r.focusSecondsInt).toBe(599);
    expect(Number.isInteger(r.xp)).toBe(true);
  });

  it("gives zero XP for non-earning tiers", () => {
    const r = computeFocusScore({
      targetSeconds: 600,
      focusSeconds: 600,
      breaches: [{ severity: "severe" }],
    });
    expect(r.tier.key).toBe("fragmented");
    expect(r.xp).toBe(0);
  });
});
