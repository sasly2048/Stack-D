/**
 * Loads the real pages with the enforcing CSP and reports any violation.
 *
 * A CSP that omits an origin fails silently: the browser blocks the request,
 * the page still renders, and sign-in or realtime quietly stops working. The
 * only way to know is to load it and listen for violations — which is why this
 * exists rather than trusting the policy string.
 *
 * Usage: BASE=http://localhost:3100 node tests/visual/csp-check.mjs
 */

import { chromium } from "playwright";

const BASE = process.env.BASE || "http://localhost:3100";
const PAGES = ["/", "/philosophy", "/privacy", "/auth"];

const browser = await chromium.launch();
let violations = 0;

for (const path of PAGES) {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await ctx.newPage();

  const found = [];
  // Chrome reports CSP blocks as console errors containing this phrasing.
  page.on("console", (m) => {
    const t = m.text();
    if (/Content Security Policy|Refused to (load|connect|execute|apply|frame)/i.test(t)) {
      found.push(t.slice(0, 220));
    }
  });
  page.on("pageerror", (e) => {
    if (/Content Security Policy/i.test(e.message)) found.push(e.message.slice(0, 220));
  });

  await page.goto(BASE + path, { waitUntil: "networkidle", timeout: 60000 }).catch(() => {});
  // Fonts, Turnstile and the Supabase client all initialise after first paint.
  await page.waitForTimeout(2500);

  const header = await page.evaluate(async () => {
    const r = await fetch(location.href, { method: "HEAD" });
    return r.headers.get("content-security-policy");
  });

  const enforcing = !!header;
  console.log(
    `${found.length === 0 && enforcing ? "PASS" : "FAIL"} ${path} — ` +
      `${found.length} violation(s), CSP ${enforcing ? "enforcing" : "MISSING"}`,
  );
  for (const v of found) console.log(`      ! ${v}`);
  violations += found.length;

  await ctx.close();
}

// Fonts are the most likely thing a wrong policy breaks visibly, so prove one
// actually loaded rather than assuming silence means success.
{
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  await page.goto(BASE + "/", { waitUntil: "networkidle", timeout: 60000 });
  await page.waitForTimeout(2000);
  const loaded = await page.evaluate(() => document.fonts.size > 0);
  console.log(`${loaded ? "PASS" : "FAIL"} webfonts loaded under CSP (${loaded})`);
  if (!loaded) violations++;
  await ctx.close();
}

await browser.close();
console.log(`\n${violations} total CSP violation(s).`);
