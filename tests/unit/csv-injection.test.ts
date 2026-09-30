import { describe, expect, it } from "vitest";

import { escapeCsv } from "@/lib/export.functions";

/**
 * A CSV cell starting with = + - @ is run as a formula by Excel/Google Sheets,
 * so an exported user-controlled value could exfiltrate data or run a command
 * when the file is opened. escapeCsv must neutralise those.
 */
describe("escapeCsv formula-injection guard", () => {
  it("prefixes a quote on formula-trigger characters", () => {
    expect(escapeCsv("=1+1")).toBe("'=1+1");
    expect(escapeCsv("+cmd")).toBe("'+cmd");
    expect(escapeCsv("-2")).toBe("'-2");
    expect(escapeCsv("@SUM(A1)")).toBe("'@SUM(A1)");
  });

  it("still quotes fields with commas / quotes / newlines", () => {
    expect(escapeCsv("a,b")).toBe('"a,b"');
    expect(escapeCsv('he said "hi"')).toBe('"he said ""hi"""');
  });

  it("a formula value that also needs quoting gets both", () => {
    // leading '=' -> prefixed with ' ; the comma -> whole thing quoted.
    expect(escapeCsv("=A1,B1")).toBe(`"'=A1,B1"`);
  });

  it("leaves ordinary values untouched", () => {
    expect(escapeCsv("meeting notes")).toBe("meeting notes");
    expect(escapeCsv(42)).toBe("42");
    expect(escapeCsv(null)).toBe("");
  });
});
