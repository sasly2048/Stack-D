import { describe, expect, it } from "vitest";

import { isHttpScheme } from "@/lib/safe-url";
import { httpUrl } from "@/lib/zod-url";

/**
 * P2 #33/#34: user-supplied links must be http(s) only. z.string().url()
 * accepts javascript:/data:/file:, which become XSS when rendered as href.
 */
describe("http-only URL validation", () => {
  it("accepts http and https", () => {
    expect(isHttpScheme("https://example.com/x")).toBe(true);
    expect(isHttpScheme("http://example.com")).toBe(true);
    expect(httpUrl.safeParse("https://example.com").success).toBe(true);
  });

  it("rejects dangerous schemes", () => {
    for (const u of [
      "javascript:alert(1)",
      "data:text/html,<script>alert(1)</script>",
      "file:///etc/passwd",
      "vbscript:msgbox(1)",
    ]) {
      expect(isHttpScheme(u)).toBe(false);
      expect(httpUrl.safeParse(u).success).toBe(false);
    }
  });

  it("rejects garbage", () => {
    expect(isHttpScheme("not a url")).toBe(false);
    expect(httpUrl.safeParse("nope").success).toBe(false);
  });
});
