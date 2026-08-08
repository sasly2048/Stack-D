/**
 * Verifies PinnedHorizontal degrades to native swipe on phones.
 *
 * The bug this guards: the section pinned on every viewport, so a phone user
 * lost vertical scroll control for several viewports of forced horizontal
 * travel. The fix swaps to native overflow scrolling below 768px.
 *
 * Usage: BASE=http://localhost:3100 node tests/visual/pin-check.mjs
 */

import { chromium } from "playwright";

const BASE = process.env.BASE || "http://localhost:3100";

const browser = await chromium.launch();

for (const width of [375, 1280]) {
  const ctx = await browser.newContext({
    viewport: { width, height: width < 768 ? 812 : 800 },
    isMobile: width < 768,
    hasTouch: width < 768,
  });
  const page = await ctx.newPage();
  await page.goto(BASE + "/", { waitUntil: "networkidle", timeout: 60000 });
  await page.waitForTimeout(2500);

  const info = await page.evaluate(() => {
    // The pin box is the only h-screen w-full overflow-hidden flex container.
    const pins = [...document.querySelectorAll("div")].filter((d) => {
      const c = d.className?.toString?.() || "";
      return c.includes("h-screen") && c.includes("w-full") && c.includes("flex items-center");
    });
    if (!pins.length) return { found: false };
    const pin = pins[0];
    const st = getComputedStyle(pin);
    const wrap = pin.parentElement;
    return {
      found: true,
      overflowX: st.overflowX,
      pinHeightCss: st.height,
      // Set imperatively to reserve scroll distance for the pin. In the mobile
      // branch it must be cleared, or the page keeps a multi-viewport blank gap.
      wrapInlineHeight: wrap?.style.height || "(none)",
      trackScrollW: pin.firstElementChild?.scrollWidth ?? null,
      pinClientW: pin.clientWidth,
      canScrollNatively: pin.scrollWidth > pin.clientWidth,
      docHeight: document.documentElement.scrollHeight,
    };
  });

  const mode = width < 768 ? "MOBILE (expect native scroll)" : "DESKTOP (expect pin)";
  console.log(`\n=== ${width}px — ${mode} ===`);
  console.log(JSON.stringify(info, null, 2));

  if (info.found) {
    if (width < 768) {
      console.log(
        info.overflowX === "auto" && info.canScrollNatively
          ? "PASS: pin box scrolls natively, no scroll-jack"
          : "FAIL: mobile fallback not engaged",
      );
      console.log(
        info.wrapInlineHeight === "" || info.wrapInlineHeight === "(none)"
          ? "PASS: wrapper height cleared (no blank gap)"
          : `FAIL: wrapper still has reserved height ${info.wrapInlineHeight}`,
      );
    } else {
      console.log(
        info.overflowX === "hidden"
          ? "PASS: pin engaged on desktop"
          : "FAIL: desktop pin was disabled",
      );
      console.log(
        info.wrapInlineHeight && info.wrapInlineHeight !== "(none)"
          ? `PASS: wrapper reserves pin distance (${info.wrapInlineHeight})`
          : "FAIL: no pin distance reserved",
      );
    }
  }

  await ctx.close();
}

await browser.close();
