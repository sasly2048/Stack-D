import { describe, expect, it } from "vitest";
import { canonicalUsername, normalizeForModeration } from "@/lib/username/normalize";
import { validateUsername } from "@/lib/username/validate";

const ok = (s: string) => validateUsername(s).ok;
const reason = (s: string) => {
  const r = validateUsername(s);
  return r.ok ? "ok" : r.reason;
};

describe("username format", () => {
  it("accepts well-formed names", () => {
    for (const n of ["ada", "Ada_Lovelace", "raghav-99", "focusMonk"]) {
      expect(ok(n), n).toBe(true);
    }
  });

  it("enforces the shape rules", () => {
    expect(reason("ab")).toBe("too_short");
    expect(reason("a".repeat(21))).toBe("too_long");
    expect(reason("1cool")).toBe("must_start_with_letter");
    expect(reason("_cool")).toBe("must_start_with_letter");
    expect(reason("has space")).toBe("invalid_characters");
    expect(reason("emoji🔥name")).toBe("invalid_characters");
    expect(reason("dots.name")).toBe("invalid_characters");
  });
});

describe("uniqueness canonicalization", () => {
  it("folds case and separators to one key", () => {
    expect(canonicalUsername("Ada_Lovelace")).toBe("adalovelace");
    expect(canonicalUsername("ada-lovelace")).toBe("adalovelace");
    expect(canonicalUsername("ADALOVELACE")).toBe("adalovelace");
  });
});

describe("moderation bypass resistance", () => {
  it("blocks plain prohibited terms", () => {
    expect(reason("fuckyou")).toBe("prohibited");
    expect(reason("bigdick69")).toBe("prohibited");
  });

  it("blocks separator splitting", () => {
    expect(reason("f_u_c_k")).toBe("prohibited");
    expect(reason("fu-ck-er")).toBe("prohibited");
  });

  it("blocks leetspeak and repeats", () => {
    expect(reason("fuuuuck")).toBe("prohibited");
    expect(reason("sh1tlord")).toBe("prohibited");
    expect(reason("b00bs")).toBe("prohibited");
  });

  it("blocks homoglyph and mixed-script attempts", () => {
    expect(reason("fuсk")).not.toBe("ok"); // Cyrillic с
    expect(reason("аdmin")).not.toBe("ok"); // Cyrillic а
  });

  it("blocks invisible characters", () => {
    expect(reason("fu\u200Bck")).toBe("invisible_characters");
  });

  it("blocks reserved and impersonation names without revealing why", () => {
    const r = validateUsername("admin");
    expect(r.ok).toBe(false);
    if (!r.ok) {
      expect(r.reason).toBe("reserved");
      expect(r.message).toBe("That username isn't available.");
    }
    expect(reason("stackd_support")).toBe("reserved");
  });

  it("normalizes to a comparable moderation form", () => {
    expect(normalizeForModeration("F_u.c\u2013k")).toBe("fuck");
  });
});
