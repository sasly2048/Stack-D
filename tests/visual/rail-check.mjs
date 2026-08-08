/**
 * Confirms the landing ReactionRail is hidden on phones.
 *
 * It had no mobile treatment (unlike BreachToast and XpChip, which both scale
 * and reposition at sm), so ~210px of unconstrained width sat on top of the
 * room roster at narrow widths.
 *
 * Usage: BASE=http://localhost:3100 node tests/visual/rail-check.mjs
 */

import { chromium } from "playwright";

const BASE = process.env.BASE || "http://localhost:3100";
const OUT = process.env.OUT || ".";
const browser = await chromium.launch();

for (const width of [375, 1280]) {
  const ctx = await browser.newContext({
    viewport: { width, height: 900 },
    isMobile: width < 768,
    hasTouch: width < 768,
  });
  const page = await ctx.newPage();
  await page.goto(BASE + "/", { waitUntil: "networkidle", timeout: 60000 });
  await page.waitForTimeout(2000);

  const rails = await page.evaluate(() => {
    // Match the rail specifically. A plain `bottom-10` substring also catches
    // XpChip's `sm:-bottom-10`, which is a different element with its own
    // (correct) mobile treatment.
    const found = [...document.querySelectorAll("div")].filter((d) => {
      const c = d.className?.toString?.() || "";
      return c.includes("absolute") && /(^|\s)bottom-10(\s|$)/.test(c) && c.includes("z-20");
    });
    return found.map((el) => {
      const s = getComputedStyle(el);
      const b = el.getBoundingClientRect();
      return {
        display: s.display,
        rendered: b.width > 0 && b.height > 0,
        left: Math.round(b.left),
        width: Math.round(b.width),
      };
    });
  });

  const anyRendered = rails.some((r) => r.rendered);
  const expectHidden = width < 768;
  const pass = expectHidden ? !anyRendered : true;
  console.log(
    `${pass ? "PASS" : "FAIL"} ${width}px — rails found=${rails.length} rendered=${anyRendered} ` +
      `(expected ${expectHidden ? "hidden" : "visible"})`,
  );
  for (const r of rails) console.log(`      ${JSON.stringify(r)}`);

  // Frame the room-preview region so the roster can be eyeballed for overlap.
  await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight * 0.42));
  await page.waitForTimeout(1200);
  await page.screenshot({ path: `${OUT}/rail-${width}.png` });
  await ctx.close();
}

await browser.close();
