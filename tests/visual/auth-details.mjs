/**
 * Verifies the auth page's contextual details actually render:
 * the "Last used" badge for the remembered provider, inline blur validation,
 * the password reveal, and the required-field markers.
 *
 * Usage: BASE=http://localhost:3100 node tests/visual/auth-details.mjs
 */

import { chromium } from "playwright";

const BASE = process.env.BASE || "http://localhost:3100";
const OUT = process.env.OUT || ".";
const browser = await chromium.launch();

function report(name, pass, detail = "") {
  console.log(`${pass ? "PASS" : "FAIL"} ${name}${detail ? ` — ${detail}` : ""}`);
  return pass;
}

// 1. First visit: no provider remembered, so no badge anywhere.
{
  const ctx = await browser.newContext({ viewport: { width: 420, height: 900 } });
  const page = await ctx.newPage();
  await page.goto(`${BASE}/auth`, { waitUntil: "networkidle" });
  await page.waitForTimeout(600);
  const badges = await page.locator("text=Last used").count();
  report("first visit shows no Last used badge", badges === 0, `found ${badges}`);
  await ctx.close();
}

// 2. Returning user: seed the pref the way a real sign-in would.
for (const provider of ["google", "email"]) {
  const ctx = await browser.newContext({ viewport: { width: 420, height: 900 } });
  await ctx.addInitScript(
    (p) => localStorage.setItem("stackd:last-auth-provider", p),
    provider,
  );
  const page = await ctx.newPage();
  await page.goto(`${BASE}/auth`, { waitUntil: "networkidle" });
  await page.waitForTimeout(600);

  const count = await page.locator("text=Last used").count();
  report(`${provider}: exactly one Last used badge`, count === 1, `found ${count}`);

  if (provider === "google") {
    // The badge must sit inside the Google button, not merely somewhere.
    const inButton = await page
      .locator('button[aria-label*="Google"]:has-text("Last used")')
      .count();
    report("google: badge is inside the Google button", inButton === 1);
    const label = await page.locator('button[aria-label*="Google"]').getAttribute("aria-label");
    report(
      "google: aria-label mentions previous use",
      /previously used/i.test(label ?? ""),
      label ?? "(none)",
    );

    // The badge was originally absolutely positioned, which reserved no space
    // and let it sit on top of the button's own label on narrow screens.
    const overlap = await page.evaluate(() => {
      const btn = document.querySelector('button[aria-label*="Google"]');
      const text = btn?.querySelector("span:not(.sr-only)");
      const badge = [...(btn?.querySelectorAll("span") ?? [])].find((s) =>
        s.textContent?.includes("Last used"),
      );
      if (!text || !badge) return null;
      const t = text.getBoundingClientRect();
      const b = badge.getBoundingClientRect();
      return { overlaps: t.right > b.left + 1, textRight: Math.round(t.right), badgeLeft: Math.round(b.left) };
    });
    report(
      "google: badge does not overlap the label",
      overlap ? !overlap.overlaps : false,
      overlap ? `text ends ${overlap.textRight}, badge starts ${overlap.badgeLeft}` : "not measured",
    );
  }

  await page.screenshot({ path: `${OUT}/auth-lastused-${provider}.png` });
  await ctx.close();
}

// 3. Inline validation + password reveal + required markers.
{
  const ctx = await browser.newContext({ viewport: { width: 420, height: 900 } });
  const page = await ctx.newPage();
  await page.goto(`${BASE}/auth`, { waitUntil: "networkidle" });
  await page.waitForTimeout(600);

  const email = page.locator("#auth-email");
  const pw = page.locator("#auth-password");

  // Typing alone must not accuse the user of anything.
  await email.fill("not-an-email");
  const duringTyping = await page.locator("#auth-email-hint").count();
  report("no validation error while still typing", duringTyping === 0);

  // Leaving the field is what triggers it.
  await pw.click();
  await page.waitForTimeout(250);
  const afterBlur = await page.locator("#auth-email-hint").count();
  const invalid = await email.getAttribute("aria-invalid");
  report("email error appears on blur", afterBlur === 1);
  report("email marked aria-invalid", invalid === "true", String(invalid));

  const alertRole = await page.locator("#auth-email-hint").getAttribute("role");
  report("validation hint is announced", alertRole === "alert", String(alertRole));

  // Submit is blocked while a field is invalid.
  const submitDisabled = await page.locator('button[type="submit"]').isDisabled();
  report("submit disabled while invalid", submitDisabled);

  // Correcting the field clears the error and re-enables submit.
  await email.fill("real@example.com");
  await pw.fill("longenough");
  await page.locator("h1").click();
  await page.waitForTimeout(250);
  const cleared = await page.locator("#auth-email-hint").count();
  const enabled = await page.locator('button[type="submit"]').isEnabled();
  report("error clears once corrected", cleared === 0);
  report("submit re-enabled once valid", enabled);

  // Password reveal toggles the input type.
  const before = await pw.getAttribute("type");
  await page.locator('button:has-text("Show")').click();
  await page.waitForTimeout(150);
  const after = await pw.getAttribute("type");
  report("password reveal toggles type", before === "password" && after === "text", `${before}→${after}`);

  const req = await page.locator('label:has-text("Email") .sr-only').textContent();
  report("required field is announced", /required/i.test(req ?? ""), req ?? "(none)");

  await page.screenshot({ path: `${OUT}/auth-validation.png` });
  await ctx.close();
}

await browser.close();
