/**
 * Mobile layout audit — measures horizontal overflow and names the offending
 * elements at phone widths.
 *
 * Horizontal scroll on a phone is the defect this catches: it is invisible on a
 * desktop viewport, trivially introduced by one fixed-width child, and it makes
 * a page feel broken in a way users notice immediately.
 *
 * Usage:  BASE=http://localhost:3100 node tests/visual/mobile-audit.mjs
 * Requires: npm install --no-save playwright && npx playwright install chromium
 */

import { chromium } from "playwright";

const BASE = process.env.BASE || "http://localhost:3100";
const OUT = process.env.OUT || ".";

// 375 is the realistic floor (iPhone SE/13 mini); 320 the absolute worst case;
// 768 confirms the desktop pinned-scroll path still engages.
const VIEWPORTS = [
  { name: "375", width: 375, height: 812 },
  { name: "320", width: 320, height: 640 },
  { name: "768", width: 768, height: 1024 },
];

const PAGES = ["/", "/philosophy", "/auth", "/privacy", "/sdk", "/catalog"];

const browser = await chromium.launch();
const results = [];

for (const vp of VIEWPORTS) {
  for (const path of PAGES) {
    const ctx = await browser.newContext({
      viewport: { width: vp.width, height: vp.height },
      deviceScaleFactor: 2,
      isMobile: vp.width < 768,
      hasTouch: vp.width < 768,
      userAgent:
        vp.width < 768
          ? "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
          : undefined,
    });
    const page = await ctx.newPage();

    const consoleErrors = [];
    page.on("console", (m) => {
      if (m.type() === "error") consoleErrors.push(m.text().slice(0, 160));
    });
    page.on("pageerror", (e) => consoleErrors.push("PAGEERROR: " + e.message.slice(0, 160)));

    await page.goto(BASE + path, { waitUntil: "networkidle", timeout: 60000 }).catch(() => {});
    await page.waitForTimeout(1200);

    const metrics = await page.evaluate(() => {
      const de = document.documentElement;
      const vw = de.clientWidth;
      const offenders = [];
      for (const el of document.querySelectorAll("body *")) {
        const r = el.getBoundingClientRect();
        if (r.width === 0 || r.height === 0) continue;
        const st = getComputedStyle(el);
        if (st.visibility === "hidden" || st.display === "none") continue;
        const overhang = Math.round(Math.max(r.right - vw, -r.left));
        if (overhang > 1) {
          offenders.push({
            tag: el.tagName.toLowerCase(),
            cls: (el.className?.toString?.() || "").slice(0, 90),
            left: Math.round(r.left),
            right: Math.round(r.right),
            overhang,
          });
        }
      }
      offenders.sort((a, b) => b.overhang - a.overhang);
      return {
        scrollW: de.scrollWidth,
        clientW: vw,
        overflowPx: de.scrollWidth - vw,
        docHeight: de.scrollHeight,
        offenders: offenders.slice(0, 6),
      };
    });

    results.push({ vp: vp.name, path, ...metrics, consoleErrors });

    await page.screenshot({
      path: `${OUT}/shot-${vp.name}-${path.replace(/\//g, "_") || "home"}.png`,
      fullPage: false,
    });
    await ctx.close();
  }
}

await browser.close();

let bad = 0;
for (const r of results) {
  const flag = r.overflowPx > 1 ? "OVERFLOW" : "ok";
  if (r.overflowPx > 1) bad++;
  console.log(
    `[${flag}] ${r.vp.padEnd(4)} ${r.path.padEnd(13)} scrollW=${r.scrollW} clientW=${r.clientW} overflow=${r.overflowPx}px h=${r.docHeight} errs=${r.consoleErrors.length}`,
  );
  for (const o of r.offenders) {
    console.log(`        -> ${o.tag}.${o.cls} L=${o.left} R=${o.right} over=${o.overhang}`);
  }
  for (const e of r.consoleErrors.slice(0, 3)) console.log(`        ! ${e}`);
}
console.log(`\n${bad} of ${results.length} page/viewport combos overflow horizontally.`);
