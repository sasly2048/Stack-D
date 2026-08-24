import { readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

/**
 * P1 (Codex #23): createSubscription must not spawn a new Razorpay subscription
 * when the user already has an active one, and rapid re-clicks must be
 * rate-limited — else each click creates a provider sub and double-charges.
 * (#24 out-of-order/duplicate webhooks are handled at the DB: subscriptions
 * user_id PK + provider_ref unique + idempotent ON CONFLICT upsert.)
 */
const fn = readFileSync(join(process.cwd(), "src", "lib", "razorpay.functions.ts"), "utf8");

describe("razorpay duplicate-checkout guard", () => {
  it("blocks a new checkout when an active subscription exists", () => {
    expect(fn).toMatch(/from\("subscriptions"\)[\s\S]*?\.eq\("user_id", context\.userId\)/);
    expect(fn).toMatch(/source === "lifetime"/);
    expect(fn).toMatch(/current_period_end[\s\S]*?> new Date\(\)/);
    expect(fn).toMatch(/already have an active subscription/i);
  });

  it("rate-limits checkout starts per user", () => {
    expect(fn).toMatch(/isRateLimited\(`checkout:\$\{context\.userId\}`/);
    expect(fn).toMatch(/Too many checkout attempts/i);
  });

  it("still stamps user_id into notes for webhook mapping (unchanged)", () => {
    expect(fn).toMatch(/notes:\s*\{\s*user_id:\s*context\.userId\s*\}/);
  });
});
