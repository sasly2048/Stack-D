import { describe, expect, it } from "vitest";

import { generateRoomCode, ROOM_CODE_ALPHABET } from "@/lib/rooms2.functions";

/**
 * P2-21: room codes are invite tokens. They must be unguessable (crypto RNG,
 * no Math.random), unbiased (alphabet size divides 256), and free of confusable
 * characters so a leaked/typed code is unambiguous.
 */
describe("room code generation", () => {
  it("uses a 32-symbol alphabet with no confusable characters", () => {
    expect(ROOM_CODE_ALPHABET).toHaveLength(32); // 256 % 32 === 0 → unbiased
    for (const c of "IO01") {
      expect(ROOM_CODE_ALPHABET).not.toContain(c);
    }
    // no duplicate symbols
    expect(new Set(ROOM_CODE_ALPHABET).size).toBe(32);
  });

  it("produces 6-char codes drawn only from the alphabet", () => {
    for (let i = 0; i < 200; i++) {
      const code = generateRoomCode();
      expect(code).toHaveLength(6);
      for (const ch of code) expect(ROOM_CODE_ALPHABET).toContain(ch);
    }
  });

  it("is not obviously repetitive (crypto RNG, not a constant)", () => {
    const codes = new Set(Array.from({ length: 200 }, () => generateRoomCode()));
    // 200 draws from ~1e9 space should essentially never collide.
    expect(codes.size).toBeGreaterThan(195);
  });
});
