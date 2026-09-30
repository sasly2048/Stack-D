import { describe, expect, it } from "vitest";

import { sanitizeVaultSearch } from "@/lib/memory-vault.functions";

/**
 * The vault search term is interpolated into a PostgREST .or() filter string.
 * These guard that a crafted term can't break out of its ilike clause or inject
 * extra filter conditions.
 */
describe("sanitizeVaultSearch", () => {
  it("passes an ordinary word through", () => {
    expect(sanitizeVaultSearch("meeting")).toBe("meeting");
  });

  it("strips PostgREST filter control characters", () => {
    // Commas separate .or() conditions; parens/dots build operators. A term
    // like this must not survive with those intact.
    const out = sanitizeVaultSearch("a,body.ilike.%x%,title.eq.(b)");
    // No comma/paren/dot/backslash-as-escape survives to build a new operator —
    // 'ilike' may remain as a harmless plain word, but 'body.ilike' can't.
    expect(out).not.toMatch(/[,()]/);
    expect(out).not.toMatch(/\w\.\w/); // no dotted operator like body.ilike
  });

  it("escapes ilike wildcards so they are literal", () => {
    expect(sanitizeVaultSearch("100%")).toBe("100\\%");
    expect(sanitizeVaultSearch("a_b")).toBe("a\\_b");
  });

  it("returns empty when nothing searchable remains", () => {
    expect(sanitizeVaultSearch(",,,")).toBe("");
    expect(sanitizeVaultSearch("   ")).toBe("");
  });

  it("caps length", () => {
    expect(sanitizeVaultSearch("x".repeat(500)).length).toBe(100);
  });
});
