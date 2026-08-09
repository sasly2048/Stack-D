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

// --- footer link ---
await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
await page.waitForTimeout(400);

const link = await page.evaluate((url) => {
  const a = [...document.querySelectorAll("a")].find((el) => el.getAttribute("href") === url);
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
}, URL_);

report("footer links to the X profile", !!link);
if (link) {
  report("opens in a new tab", link.target === "_blank", String(link.target));
  // noopener stops the opened page reaching back via window.opener; rel=me is
  // the identity convention for a site's own account.
  report("carries rel=me and noopener", /me/.test(link.rel) && /noopener/.test(link.rel), link.rel);
  report("announces the handle and new tab", /StackD_HQ/.test(link.text) && /new tab/i.test(link.text), link.text);
  report("renders the X mark", link.hasSvg);
  // The icon already says "X"; a visible label repeating it reads as "X X".
  report(
    "visible label does not duplicate the mark",
    !/^X\s+X\b/.test(link.visible) && link.visible !== "X",
    link.visible,
  );
  report("stays inside the viewport", link.inViewport);
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
