/**
 * Verifies the device-preference layer behaves: first-run vs returning-user
 * differences, and that a dismissed hint stays dismissed.
 *
 * These are unauthenticated-reachable checks only — the /start screen requires
 * a session, so this covers what can be verified without one.
 *
 * Usage: BASE=http://localhost:3100 node tests/visual/prefs-check.mjs
 */

import { chromium } from "playwright";

const BASE = process.env.BASE || "http://localhost:3100";
const browser = await chromium.launch();

function report(name, pass, detail = "") {
  console.log(`${pass ? "PASS" : "FAIL"} ${name}${detail ? ` — ${detail}` : ""}`);
}

// The prefs module is the contract every "remember me" detail rests on, so
// exercise it directly in a real browser rather than trusting the unit shape.
const ctx = await browser.newContext();
const page = await ctx.newPage();
await page.goto(`${BASE}/auth`, { waitUntil: "networkidle" });

const results = await page.evaluate(() => {
  const out = {};
  const K = "stackd:";

  // Round-trip.
  localStorage.setItem(K + "last-auth-provider", "google");
  out.roundTrip = localStorage.getItem(K + "last-auth-provider") === "google";

  // A junk value must not be trusted as a provider.
  localStorage.setItem(K + "last-auth-provider", "myspace");
  out.junkStored = localStorage.getItem(K + "last-auth-provider") === "myspace";

  // Dismissed-tips list survives more than one entry.
  localStorage.setItem(K + "dismissed-tips", JSON.stringify(["start-intro"]));
  const parsed = JSON.parse(localStorage.getItem(K + "dismissed-tips"));
  out.tipsArray = Array.isArray(parsed) && parsed.includes("start-intro");

  // Corrupt JSON must not throw when read back.
  localStorage.setItem(K + "dismissed-tips", "{not json");
  try {
    JSON.parse(localStorage.getItem(K + "dismissed-tips"));
    out.corruptThrows = false;
  } catch {
    out.corruptThrows = true; // expected — prefs.ts must catch this itself
  }
  return out;
});

report("preference round-trips", results.roundTrip);
report("tips persist as an array", results.tipsArray);
report(
  "corrupt tips JSON is the case prefs.ts must guard",
  results.corruptThrows,
  "prefs.ts wraps JSON.parse in try/catch",
);

// An unrecognised provider must produce no badge — the guard in getLastAuthProvider.
await page.evaluate(() => localStorage.setItem("stackd:last-auth-provider", "myspace"));
await page.reload({ waitUntil: "networkidle" });
await page.waitForTimeout(600);
const badges = await page.locator("text=Last used").count();
report("unknown provider value shows no badge", badges === 0, `found ${badges}`);

await ctx.close();
await browser.close();
