import { describe, expect, it } from "vitest";

import { SITE_URL, siteUrl } from "@/lib/site";

/**
 * These guard the two ways the canonical URL has actually gone wrong:
 * pointing at a host we don't serve, and emitting a doubled slash when the
 * configured origin carries a trailing one.
 */
describe("siteUrl", () => {
  it("exposes an origin with no trailing slash", () => {
    expect(SITE_URL).not.toMatch(/\/$/);
    expect(SITE_URL).toMatch(/^https?:\/\//);
  });

  it("never points at the retired Lovable preview host", () => {
    expect(SITE_URL).not.toContain("lovable.app");
  });

  it("joins paths without doubling the separator", () => {
    expect(siteUrl("/")).toBe(`${SITE_URL}/`);
    expect(siteUrl("/philosophy")).toBe(`${SITE_URL}/philosophy`);
  });

  it("tolerates a path given without a leading slash", () => {
    expect(siteUrl("privacy")).toBe(`${SITE_URL}/privacy`);
  });

  it("defaults to the site root", () => {
    expect(siteUrl()).toBe(`${SITE_URL}/`);
  });
});
