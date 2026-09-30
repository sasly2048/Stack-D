import { readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

/**
 * Guards the lifetime coupon cap. The real enforcement is SQL (redeem_lifetime
 * is row-locked and checks redeemed_count >= max_redemptions), which can't run
 * without a DB — so this asserts the migration keeps the invariants that make
 * the cap correct, catching an accidental edit at review time:
 *   1. the seat cap is 500,
 *   2. redemption is serialized with FOR UPDATE (no oversell race),
 *   3. sold_out is returned once the cap is hit,
 *   4. a bad code returns before the counter increments (only successful
 *      redemptions consume a seat).
 */
const migration = readFileSync(
  join(
    process.cwd(),
    "supabase",
    "migrations",
    "20260817050000_subscriptions_entitlements.sql",
  ),
  "utf8",
);

describe("lifetime coupon cap", () => {
  it("seeds and defaults the cap to 500", () => {
    expect(migration).toMatch(/max_redemptions INTEGER NOT NULL DEFAULT 500/);
    expect(migration).toMatch(/VALUES \(1, NULL, 500, false\)/);
  });

  it("locks the promo row before redeeming (no oversell race)", () => {
    expect(migration).toMatch(/FROM public\.lifetime_promo WHERE id = 1 FOR UPDATE/);
  });

  it("returns sold_out once the cap is reached", () => {
    expect(migration).toMatch(/redeemed_count >= _p\.max_redemptions/);
    expect(migration).toMatch(/RETURN 'sold_out'/);
  });

  it("rejects a bad code before touching the counter", () => {
    // bad_code must be returned before the redeemed_count UPDATE, so a wrong
    // code never consumes a seat.
    const badCodeIdx = migration.indexOf("RETURN 'bad_code'");
    const incrementIdx = migration.indexOf("SET redeemed_count = redeemed_count + 1");
    expect(badCodeIdx).toBeGreaterThan(-1);
    expect(incrementIdx).toBeGreaterThan(-1);
    expect(badCodeIdx).toBeLessThan(incrementIdx);
  });
});
