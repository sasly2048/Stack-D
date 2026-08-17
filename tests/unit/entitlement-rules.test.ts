import { describe, expect, it } from "vitest";

import { annualSavingsPct, meetsTier, tierRank } from "@/lib/entitlement-rules";

describe("entitlement tier rules", () => {
  it("orders free < pro < elite", () => {
    expect(tierRank("free")).toBeLessThan(tierRank("pro"));
    expect(tierRank("pro")).toBeLessThan(tierRank("elite"));
  });

  it("gates by minimum required tier", () => {
    expect(meetsTier({ tier: "elite" }, "pro")).toBe(true);
    expect(meetsTier({ tier: "pro" }, "pro")).toBe(true);
    expect(meetsTier({ tier: "free" }, "pro")).toBe(false);
    expect(meetsTier({ tier: "pro" }, "elite")).toBe(false);
    expect(meetsTier({ tier: "elite" }, "elite")).toBe(true);
  });

  it("computes annual savings against 12x monthly", () => {
    // 129/mo -> 1548/yr; 899 annual => 42% off
    expect(annualSavingsPct(129, 899)).toBe(42);
    // 249/mo -> 2988/yr; 1799 annual => 40% off
    expect(annualSavingsPct(249, 1799)).toBe(40);
    expect(annualSavingsPct(0, 100)).toBe(0);
  });
});
