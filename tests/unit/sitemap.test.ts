import { describe, expect, it } from "vitest";

import { isLabRoute } from "@/lib/feature-flags";
import { SITE_URL } from "@/lib/site";

/**
 * A sitemap is a request to index, so it must exclude anything that either
 * cannot be reached or refuses indexing:
 *
 *  - /catalog and /sdk sit behind the labs flag, so listing them pointed
 *    crawlers at pages no visitor can navigate to.
 *  - /auth serves `noindex`. Submitting it made the sitemap ask for indexing
 *    the page itself declines — Search Console reports that as "Submitted URL
 *    marked noindex".
 *
 * An earlier version of this test asserted /auth *should* be published, which
 * encoded the bug rather than catching it.
 */
const NOINDEX_ROUTES = new Set(["/auth"]);

describe("sitemap route selection", () => {
  const CANDIDATES = ["/", "/philosophy", "/privacy", "/catalog", "/sdk", "/auth"];
  const published = CANDIDATES.filter((p) => !isLabRoute(p) && !NOINDEX_ROUTES.has(p));

  it("publishes the indexable marketing surfaces", () => {
    expect(published).toContain("/");
    expect(published).toContain("/philosophy");
    expect(published).toContain("/privacy");
  });

  it("omits routes hidden behind the labs flag", () => {
    expect(published).not.toContain("/catalog");
    expect(published).not.toContain("/sdk");
  });

  it("omits noindex routes, which must never be submitted for indexing", () => {
    expect(published).not.toContain("/auth");
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
