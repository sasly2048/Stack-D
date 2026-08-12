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

describe("false positives", () => {
  // Regression: leetspeak-undoing this yields "...abo..." which naive
  // substring matching flags as a slur.
  it("accepts Sasly204800", () => {
    expect(reason("Sasly204800")).toBe("ok");
  });

  it("accepts alphanumeric names with long numeric suffixes", () => {
    for (const n of [
      "Sasly204800", "sasly2048", "Milan13370", "Tarun500700", "kiran8008",
      "Neha1010101", "arjun404", "Bhavya1337", "dev8000", "zoya007",
    ]) {
      expect(reason(n), n).toBe("ok");
    }
  });

  it("accepts legitimate names that embed blocked stems", () => {
    for (const n of [
      "Cassandra", "cassandra99", "Scunthorpe", "grasshopper", "bassist",
      "Hancock", "peacock_dev", "cocktail99", "Dickens", "analyst",
      "therapist", "classical", "Titanium", "Assange", "Sussex",
      "Mongolia", "pakistan", "japanese", "raccoon99", "unisex_lab",
      "debugger", "scrapyard", "Lynch", "Randy", "Fannie", "grapevine",
      "sextant", "spice_dev", "negroni", "firecracker", "homosapien",
      "Nagasaki", "Curry_House", "camel_case", "abolition", "Lundberg",
    ]) {
      expect(reason(n), n).toBe("ok");
    }
  });

  it("accepts legitimate names against every category", () => {
    for (const n of ["Adminah", "teammate", "Helpful", "Rooman", "Sexton"]) {
      expect(reason(n), n).toBe("ok");
    }
  });
});

describe("moderation bypass resistance", () => {
  it("blocks plain prohibited terms", () => {
    expect(reason("fuckyou")).toBe("prohibited");
    expect(reason("bigdick69")).toBe("prohibited");
    expect(reason("pornlover")).toBe("prohibited");
    expect(reason("nigger1")).toBe("prohibited");
  });

  it("blocks separator splitting", () => {
    expect(reason("f_u_c_k")).toBe("prohibited");
    expect(reason("fu-ck-er")).toBe("prohibited");
    expect(reason("n_i_g_g_e_r")).toBe("prohibited");
  });

  it("blocks leetspeak and repeats", () => {
    expect(reason("fuuuuck")).toBe("prohibited");
    expect(reason("sh1tlord")).toBe("prohibited");
    expect(reason("b00bs")).toBe("prohibited");
    expect(reason("p0rnking")).toBe("prohibited");
  });

  it("blocks homoglyph and mixed-script attempts", () => {
    expect(reason("fuсk")).not.toBe("ok"); // Cyrillic с
    expect(reason("аdmin")).not.toBe("ok"); // Cyrillic а
  });

  it("blocks invisible characters", () => {
    expect(reason("fu\u200Bck")).toBe("invisible_characters");
  });

  it("blocks each moderation category", () => {
    expect(reason("shitpost")).toBe("prohibited"); // profanity
    expect(reason("hentaifan")).toBe("prohibited"); // nsfw
    expect(reason("faggot99")).toBe("prohibited"); // slurs
    expect(reason("admin")).toBe("reserved"); // reserved
    expect(reason("stackd_support")).toBe("reserved"); // impersonation
  });

  it("blocks reserved names without revealing why", () => {
    const r = validateUsername("admin");
    expect(r.ok).toBe(false);
    if (!r.ok) {
      expect(r.reason).toBe("reserved");
      expect(r.message).toBe("That username isn't available.");
      expect(r.message).not.toContain("admin");
    }
  });

  it("keeps matched terms out of user-facing messages", () => {
    const r = validateUsername("fuckyou");
    expect(r.ok).toBe(false);
    if (!r.ok) {
      expect(r.message).toBe("That username breaks our naming rules. Try something else.");
      expect(r.debug?.category).toBe("profanity");
    }
  });

  it("normalizes to a comparable moderation form", () => {
    expect(normalizeForModeration("F_u.c\u2013k")).toBe("fuck");
  });
});
