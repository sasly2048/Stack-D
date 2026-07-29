import { describe, it, expect } from "vitest";
import { formatDuration, formatHours, generateRoomCode } from "@/lib/room";
import { computeTier } from "@/lib/nav-tier";

describe("generateRoomCode", () => {
  it("emits six characters from the unambiguous alphabet", () => {
    for (let i = 0; i < 200; i++) {
      const code = generateRoomCode();
      expect(code).toHaveLength(6);
      expect(code).toMatch(/^[A-HJ-NP-Z2-9]{6}$/);
      expect(code).not.toMatch(/[01IO]/);
    }
  });

  it("is not trivially repeating", () => {
    const seen = new Set(Array.from({ length: 100 }, generateRoomCode));
    expect(seen.size).toBeGreaterThan(90);
  });
});

describe("formatDuration", () => {
  it("uses mm:ss below an hour", () => {
    expect(formatDuration(0)).toBe("00:00");
    expect(formatDuration(59)).toBe("00:59");
    expect(formatDuration(600)).toBe("10:00");
    expect(formatDuration(3599)).toBe("59:59");
  });

  it("switches to hh:mm:ss at an hour", () => {
    expect(formatDuration(3600)).toBe("01:00:00");
    expect(formatDuration(3661)).toBe("01:01:01");
  });

  it("clamps negatives instead of rendering junk", () => {
    expect(formatDuration(-30)).toBe("00:00");
  });
});

describe("formatHours", () => {
  it("keeps one decimal under ten hours and rounds above", () => {
    expect(formatHours(1800)).toBe("0.5h");
    expect(formatHours(3600)).toBe("1.0h");
    expect(formatHours(36_000)).toBe("10h");
    expect(formatHours(48_600)).toBe("14h");
  });
});

describe("computeTier", () => {
  it("starts everyone at starter", () => {
    expect(computeTier(0, 0, 0)).toBe("starter");
    expect(computeTier(299, 2, 4)).toBe("starter");
  });

  it("promotes on any single intermediate signal", () => {
    expect(computeTier(300, 0, 0)).toBe("intermediate");
    expect(computeTier(0, 3, 0)).toBe("intermediate");
    expect(computeTier(0, 0, 5)).toBe("intermediate");
  });

  it("promotes on any single advanced signal", () => {
    expect(computeTier(2000, 0, 0)).toBe("advanced");
    expect(computeTier(0, 7, 0)).toBe("advanced");
    expect(computeTier(0, 0, 20)).toBe("advanced");
  });

  it("never demotes a high-XP user because streak reset", () => {
    expect(computeTier(5000, 0, 0)).toBe("advanced");
  });
});
