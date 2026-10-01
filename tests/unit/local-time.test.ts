import { describe, expect, it } from "vitest";
import { hourInZone } from "@/lib/proactive-ai.functions";
import { humanizeKey } from "@/lib/utils";

describe("hourInZone", () => {
  it("bins by the user's zone, not UTC", () => {
    // 07:30 UTC is 13:00 in India — the peak users actually see.
    expect(hourInZone("Asia/Kolkata")("2026-09-08T07:30:00Z")).toBe(13);
    expect(hourInZone("UTC")("2026-09-08T07:30:00Z")).toBe(7);
  });

  it("falls back to UTC for a missing or bogus zone", () => {
    expect(hourInZone(undefined)("2026-09-08T23:10:00Z")).toBe(23);
    expect(hourInZone("Not/AZone")("2026-09-08T23:10:00Z")).toBe(23);
  });

  it("reports midnight as 0, not 24", () => {
    expect(hourInZone("UTC")("2026-09-08T00:05:00Z")).toBe(0);
  });
});

describe("humanizeKey", () => {
  it("turns payload ids into display text", () => {
    expect(humanizeKey("no_breach")).toBe("No Breach");
    expect(humanizeKey("flow-state")).toBe("Flow State");
    expect(humanizeKey("first_stack")).toBe("First Stack");
  });
});
