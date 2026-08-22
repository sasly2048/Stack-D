import { describe, expect, it } from "vitest";

import { PREMIUM_FEATURES, featureByKey, featuresFor, TIER_TAGLINE } from "@/lib/premium-catalog";

/**
 * The catalog is the single source of truth for entitlements, so these guard
 * the invariants the gates and UI both rely on.
 */
describe("premium catalog integrity", () => {
  it("has unique feature keys", () => {
    const keys = PREMIUM_FEATURES.map((f) => f.key);
    expect(new Set(keys).size).toBe(keys.length);
  });

  it("every feature is pro or elite", () => {
    for (const f of PREMIUM_FEATURES) {
      expect(["pro", "elite"]).toContain(f.requiredTier);
    }
  });

  it("elite includes every pro feature (cumulative)", () => {
    const pro = featuresFor("pro");
    const elite = featuresFor("elite");
    for (const f of pro) expect(elite).toContain(f);
    expect(elite.length).toBeGreaterThan(pro.length);
  });

  it("positions Pro as understand and Elite as optimize", () => {
    expect(TIER_TAGLINE.pro.toLowerCase()).toContain("understand");
    expect(TIER_TAGLINE.elite.toLowerCase()).toContain("optimize");
  });

  it("resolves the keys the server gates actually reference", () => {
    // These four keys are gated server-side today (requireFeature in the
    // corresponding *.functions.ts). If a rename drops one, the gate throws
    // 'Unknown premium feature' at runtime — catch it here instead.
    for (const [key, tier] of [
      ["focus_dna", "pro"],
      ["focus_forecast", "elite"],
      ["vault", "elite"],
      ["time_capsules", "elite"],
    ] as const) {
      const f = featureByKey(key);
      expect(f, key).toBeDefined();
      expect(f?.requiredTier).toBe(tier);
      expect(f?.serverGate).toBe(true);
    }
  });

  it("never marks a feature server-gated without the backend claim being intentional", () => {
    // serverGate:true is a promise that a real gate exists. Keep the set small
    // and explicit so an accidental flip is visible.
    const gated = PREMIUM_FEATURES.filter((f) => f.serverGate)
      .map((f) => f.key)
      .sort();
    expect(gated).toEqual([
      "atlas_coach",
      "elite_weekly_reports",
      "focus_dna",
      "focus_forecast",
      "time_capsules",
      "vault",
    ]);
  });
});
