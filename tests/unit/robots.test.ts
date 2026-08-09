import { readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

/**
 * robots.txt is a static file, so nothing typechecks it and nothing rendered it
 * during review — a conflict-marker block from a cherry-pick shipped to
 * production and sat there being served to crawlers. Google's parser ignores
 * unrecognised lines, so it failed silently rather than loudly, which is
 * exactly why it needed a test rather than a careful reader.
 */
const robots = readFileSync(join(process.cwd(), "public", "robots.txt"), "utf8");

describe("robots.txt", () => {
  it("contains no merge conflict markers", () => {
    // Anchored to line starts: "=======" alone would match a divider in prose.
    expect(robots).not.toMatch(/^<{7}/m);
    expect(robots).not.toMatch(/^={7}/m);
    expect(robots).not.toMatch(/^>{7}/m);
  });

  it("has only directives, comments and blank lines", () => {
    const VALID =
      /^(user-agent|allow|disallow|sitemap|crawl-delay|host)\s*:/i;
    const offenders = robots
      .split(/\r?\n/)
      .map((line, i) => ({ line: line.trim(), n: i + 1 }))
      .filter(({ line }) => line !== "" && !line.startsWith("#"))
      .filter(({ line }) => !VALID.test(line));
    expect(offenders).toEqual([]);
  });

  it("declares a user-agent before any rule", () => {
    const firstRule = robots.split(/\r?\n/).findIndex((l) => /^(allow|disallow)\s*:/i.test(l.trim()));
    const firstAgent = robots.split(/\r?\n/).findIndex((l) => /^user-agent\s*:/i.test(l.trim()));
    expect(firstAgent).toBeGreaterThanOrEqual(0);
    expect(firstAgent).toBeLessThan(firstRule);
  });

  it("points at the sitemap on the live origin", () => {
    expect(robots).toMatch(/^Sitemap:\s*https:\/\/stackd\.raghav\.studio\/sitemap\.xml\s*$/m);
    expect(robots).not.toContain("lovable.app");
  });

  it("blocks the authenticated surfaces that only redirect to /auth", () => {
    for (const path of ["/dashboard", "/start", "/room/", "/profile", "/api/"]) {
      expect(robots).toMatch(new RegExp(`^Disallow:\\s*${path.replace(/\//g, "\\/")}`, "m"));
    }
  });
});
