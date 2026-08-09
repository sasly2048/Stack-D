/**
 * Verifies the contact address is correct and reachable everywhere it appears,
 * and that "Press" — a media-enquiry link for a press function that does not
 * exist — is gone.
 *
 * Usage: BASE=http://localhost:3100 node tests/visual/contact-check.mjs
 */

import { chromium } from "playwright";

const BASE = process.env.BASE || "http://localhost:3100";
const OUT = process.env.OUT || ".";
const EMAIL = "raghavendrasujith204800@gmail.com";

const browser = await chromium.launch();

function report(name, pass, detail = "") {
  console.log(`${pass ? "PASS" : "FAIL"} ${name}${detail ? ` — ${detail}` : ""}`);
}

for (const [path, width] of [
  ["/", 1280],
  ["/", 375],
  ["/privacy", 1280],
  ["/privacy", 375],
]) {
  const ctx = await browser.newContext({ viewport: { width, height: 900 } });
  const page = await ctx.newPage();
  await page.goto(BASE + path, { waitUntil: "networkidle" });
  await page.waitForTimeout(500);
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
  await page.waitForTimeout(400);

  const mailtos = await page.evaluate(() =>
    [...document.querySelectorAll('a[href^="mailto:"]')].map((a) => ({
      href: a.getAttribute("href"),
      text: a.textContent.trim(),
    })),
  );

  const label = `${path} @${width}`;

  report(
    `${label}: every mailto uses the real address`,
    mailtos.length > 0 && mailtos.every((m) => m.href === `mailto:${EMAIL}`),
    mailtos.map((m) => m.href).join(", ") || "none found",
  );

  const stale = await page.locator("text=/hello@|press@/i").count();
  report(`${label}: no placeholder addresses remain`, stale === 0, `found ${stale}`);

  const press = await page.getByRole("link", { name: /^press$/i }).count();
  report(`${label}: Press link removed`, press === 0, `found ${press}`);

  // The address is long and the footer column is narrow; make sure it neither
  // overflows its own container nor gets clipped.
  const clipped = await page.evaluate(() => {
    const a = document.querySelector('a[href^="mailto:"]');
    if (!a) return null;
    const r = a.getBoundingClientRect();
    return { overflowsViewport: r.right > document.documentElement.clientWidth + 1, width: Math.round(r.width) };
  });
  if (clipped) {
    report(`${label}: address stays inside the viewport`, !clipped.overflowsViewport, `${clipped.width}px wide`);
  }

  await page.screenshot({ path: `${OUT}/contact-${path.replace(/\//g, "_") || "home"}-${width}.png` });
  await ctx.close();
}

await browser.close();
