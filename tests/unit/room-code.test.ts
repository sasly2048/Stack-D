import { describe, it, expect, beforeEach } from "vitest";
import {
  ERROR_COPY,
  POSITIVE_TTL_MS,
  _clearPositiveCache,
  getPositive,
  isValidRoomCode,
  normalizeCode,
  setPositive,
} from "@/lib/room-code";

describe("normalizeCode", () => {
  it("uppercases and strips separators users actually paste", () => {
    expect(normalizeCode("ab3-d9f")).toBe("AB3D9F");
    expect(normalizeCode(" a b c 1 2 3 ")).toBe("ABC123");
    expect(normalizeCode("abc123\n")).toBe("ABC123");
  });

  it("truncates past six characters instead of rejecting", () => {
    expect(normalizeCode("ABC123XYZ")).toBe("ABC123");
  });

  it("folds full-width glyphs via NFKC (mobile keyboards, autofill)", () => {
    expect(normalizeCode("ＡＢＣ１２３")).toBe("ABC123");
  });

  it("returns empty string for non-string input", () => {
    expect(normalizeCode(undefined)).toBe("");
    expect(normalizeCode(null)).toBe("");
    expect(normalizeCode(123456)).toBe("");
    expect(normalizeCode({})).toBe("");
  });
});

describe("isValidRoomCode", () => {
  it("accepts exactly six alphanumerics after normalization", () => {
    expect(isValidRoomCode("abc123")).toBe(true);
    expect(isValidRoomCode("ABC-123")).toBe(true);
  });

  it("rejects short codes and codes with no alphanumerics", () => {
    expect(isValidRoomCode("ABC12")).toBe(false);
    expect(isValidRoomCode("")).toBe(false);
    expect(isValidRoomCode("-----")).toBe(false);
  });
});

describe("positive cache", () => {
  beforeEach(() => _clearPositiveCache());

  it("returns a stored entry inside the TTL", () => {
    const now = 1_000_000;
    setPositive("ABC123", "lobby", now);
    expect(getPositive("ABC123", now + 1_000)?.status).toBe("lobby");
  });

  it("expires entries once the TTL elapses", () => {
    const now = 1_000_000;
    setPositive("ABC123", "active", now);
    expect(getPositive("ABC123", now + POSITIVE_TTL_MS + 1)).toBeNull();
  });

  it("misses unknown codes so negatives are never cached", () => {
    expect(getPositive("ZZZZZZ")).toBeNull();
  });
});

describe("ERROR_COPY", () => {
  it("marks only transient failures retryable", () => {
    expect(ERROR_COPY.rate_limited.canRetry).toBe(true);
    expect(ERROR_COPY.server_error.canRetry).toBe(true);
    expect(ERROR_COPY.network.canRetry).toBe(true);
    expect(ERROR_COPY.invalid_format.canRetry).toBe(false);
    expect(ERROR_COPY.not_found.canRetry).toBe(false);
    expect(ERROR_COPY.closed.canRetry).toBe(false);
  });

  it("provides message, retry guidance and loading copy for every code", () => {
    for (const [code, copy] of Object.entries(ERROR_COPY)) {
      expect(copy.msg, code).toBeTruthy();
      expect(copy.retry, code).toBeTruthy();
      expect(copy.loading, code).toBeTruthy();
    }
  });
});
