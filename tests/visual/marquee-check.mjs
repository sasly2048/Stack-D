/**
 * Confirms the testimonial marquee cards fit inside the viewport.
 *
 * They were a hard 340px, wider than a 320px phone, so the quote was clipped by
 * the marquee's edge mask. Now w-[min(340px,82vw)].
 *
 * Usage: BASE=http://localhost:3100 node tests/visual/marquee-check.mjs
 */

import { chromium } from "playwright";

const BASE = process.env.BASE || "http://localhost:3100";
const browser = await chromium.launch();

for (const width of [320, 375, 1280]) {
  const ctx = await browser.newContext({
    viewport: { width, height: 800 },
    isMobile: width < 768,
    hasTouch: width < 768,
  });
  const page = await ctx.newPage();
  await page.goto(BASE + "/", { waitUntil: "networkidle", timeout: 60000 });
  await page.waitForTimeout(2000);

  const r = await page.evaluate(() => {
    const figs = [...document.querySelectorAll("figure")].filter((f) =>
      (f.className?.toString?.() || "").includes("shrink-0"),
    );
    if (!figs.length) return null;
    const w = figs[0].getBoundingClientRect().width;
    return { cardWidth: Math.round(w), count: figs.length, viewport: window.innerWidth };
  });

  if (!r) {
    console.log(`${width}px: no marquee cards found`);
  } else {
    const pass = r.cardWidth <= r.viewport;
    console.log(
      `${pass ? "PASS" : "FAIL"} ${width}px: card=${r.cardWidth}px viewport=${r.viewport}px (${r.count} cards)`,
    );
  }
  await ctx.close();
}

await browser.close();
