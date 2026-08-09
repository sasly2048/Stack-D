/**
 * Verifies the X account is wired up everywhere it should be: the footer link,
 * the Twitter card attribution tags, and the Organization sameAs.
 *
 * Usage: BASE=http://localhost:3100 node tests/visual/social-check.mjs
 */

import { chromium } from "playwright";

const BASE = process.env.BASE || "http://localhost:3100";
const OUT = process.env.OUT || ".";
const HANDLE = "StackD_HQ";
const URL_ = `https://x.com/${HANDLE}`;
const LINKEDIN = "https://www.linkedin.com/company/stackd_hq";

const browser = await chromium.launch();

function report(name, pass, detail = "") {
  console.log(`${pass ? "PASS" : "FAIL"} ${name}${detail ? ` — ${detail}` : ""}`);
}

const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
const page = await ctx.newPage();
await page.goto(BASE + "/", { waitUntil: "networkidle" });
await page.waitForTimeout(600);

// --- meta / structured data ---
const meta = await page.evaluate(() => {
  const get = (n) => document.querySelector(`meta[name="${n}"]`)?.content ?? null;
  const ld = [...document.querySelectorAll('script[type="application/ld+json"]')]
    .map((s) => {
      try {
        return JSON.parse(s.textContent);
      } catch {
        return null;
      }
    })
    .filter(Boolean);
  return { site: get("twitter:site"), creator: get("twitter:creator"), ld };
});

report("twitter:site attributes the account", meta.site === `@${HANDLE}`, String(meta.site));
report("twitter:creator attributes the account", meta.creator === `@${HANDLE}`, String(meta.creator));

const org = meta.ld
  .flatMap((d) => d["@graph"] ?? [d])
  .find((n) => n?.["@type"] === "Organization");
report(
  "Organization sameAs links the X profile",
  Array.isArray(org?.sameAs) && org.sameAs.includes(URL_),
  JSON.stringify(org?.sameAs ?? null),
);
report(
  "Organization sameAs links the LinkedIn page",
  Array.isArray(org?.sameAs) && org.sameAs.includes(LINKEDIN),
  JSON.stringify(org?.sameAs ?? null),
);
// A personal profile in a product footer is a different claim than a company
// page — /in/ would mean the wrong one got wired up.
report(
  "LinkedIn points at the company page, not a personal profile",
  !JSON.stringify(org?.sameAs ?? []).includes("linkedin.com/in/"),
);

// --- footer link ---
await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
await page.waitForTimeout(400);

const probe = async (url) =>
  page.evaluate((u) => {
    const a = [...document.querySelectorAll("a")].find((el) => el.getAttribute("href") === u);
    if (!a) return null;
    const r = a.getBoundingClientRect();
    // Visible text excludes sr-only spans — the icon is already the wordmark, so
    // a visible "X" beside it renders as "X X". Checking only the accessible
    // name missed exactly that.
    const visible = [...a.childNodes]
      .filter((n) => !(n.nodeType === 1 && n.classList?.contains("sr-only")))
      .map((n) => n.textContent ?? "")
      .join("")
      .replace(/\s+/g, " ")
      .trim();
    return {
      target: a.getAttribute("target"),
      rel: a.getAttribute("rel"),
      text: a.textContent.replace(/\s+/g, " ").trim(),
      visible,
      hasSvg: !!a.querySelector("svg"),
      inViewport: r.right <= document.documentElement.clientWidth + 1 && r.width > 0,
    };
  }, url);

for (const [label, url, platform] of [
  ["X", URL_, "X"],
  ["LinkedIn", LINKEDIN, "LinkedIn"],
]) {
  const l = await probe(url);
  report(`footer links to the ${label} profile`, !!l);
  if (!l) continue;
  report(`${label}: opens in a new tab`, l.target === "_blank", String(l.target));
  // noopener stops the opened page reaching back via window.opener; rel=me is
  // the identity convention for a site's own account.
  report(`${label}: carries rel=me and noopener`, /me/.test(l.rel) && /noopener/.test(l.rel), l.rel);
  report(
    `${label}: announces the platform and new tab`,
    new RegExp(platform).test(l.text) && /new tab/i.test(l.text),
    l.text,
  );
  report(`${label}: renders its mark`, l.hasSvg);
  // The icon already carries the platform name; a visible label repeating it
  // reads as "X X".
  report(
    `${label}: visible label does not duplicate the mark`,
    l.visible !== platform && !new RegExp(`^${platform}\\s+${platform}\\b`).test(l.visible),
    l.visible,
  );
  report(`${label}: stays inside the viewport`, l.inViewport);
}

// "Press" must not have come back.
const press = await page.getByRole("link", { name: /^press$/i }).count();
report("no Press link", press === 0, `found ${press}`);

await page.screenshot({ path: `${OUT}/social-footer.png` });
await ctx.close();

// Narrow width: the row gained an icon and an arrow, so re-check it fits.
const mob = await browser.newContext({ viewport: { width: 320, height: 800 }, isMobile: true });
const mp = await mob.newPage();
await mp.goto(BASE + "/", { waitUntil: "networkidle" });
await mp.waitForTimeout(500);
await mp.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
await mp.waitForTimeout(400);
const overflow = await mp.evaluate(
  () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
);
report("no horizontal overflow at 320px", overflow <= 1, `${overflow}px`);
await mp.screenshot({ path: `${OUT}/social-footer-320.png` });
await mob.close();

await browser.close();
