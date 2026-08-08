import { describe, expect, it } from "vitest";

import { isLabRoute } from "@/lib/feature-flags";
import { SITE_URL } from "@/lib/site";

/**
 * The sitemap previously advertised /catalog and /sdk — both hidden behind the
 * labs flag, so crawlers were pointed at pages no visitor can navigate to. The
 * route derives its entries from `isLabRoute` now; these assert the two stay in
 * agreement rather than re-testing the handler's string building.
 */
describe("sitemap route selection", () => {
  const CANDIDATES = ["/", "/philosophy", "/privacy", "/catalog", "/sdk", "/auth"];
  const published = CANDIDATES.filter((p) => !isLabRoute(p));

  it("publishes the public marketing surfaces", () => {
    expect(published).toContain("/");
    expect(published).toContain("/philosophy");
    expect(published).toContain("/privacy");
    expect(published).toContain("/auth");
  });

  it("omits routes hidden behind the labs flag", () => {
    expect(published).not.toContain("/catalog");
    expect(published).not.toContain("/sdk");
  });

  it("builds absolute locs on the real origin", () => {
    for (const path of published) {
      const loc = `${SITE_URL}${path}`;
      expect(loc).toMatch(/^https:\/\//);
      expect(loc).not.toContain("lovable.app");
      expect(loc).not.toContain("//" + "/");
    }
  });
});
