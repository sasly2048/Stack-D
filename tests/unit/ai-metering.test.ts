import { readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

/**
 * AI metering is the cost-control boundary. The real logic is SQL, so these
 * pin the policy numbers and the invariants that keep the cap enforceable
 * (row lock, atomic check-then-increment, tier allowances, unlimited bypass).
 */
const migration = readFileSync(
  join(process.cwd(), "supabase", "migrations", "20260823120000_ai_usage_metering.sql"),
  "utf8",
);

describe("AI usage metering (migration)", () => {
  it("sets the agreed per-tier allowances: pro 20, elite 200, free 0", () => {
    expect(migration).toMatch(/WHEN 'pro' THEN 20/);
    expect(migration).toMatch(/WHEN 'elite' THEN 200/);
    expect(migration).toMatch(/ELSE 0\b/); // free
  });

  it("locks the usage row before deciding (no overspend under concurrency)", () => {
    expect(migration).toMatch(/FROM public\.ai_usage WHERE user_id = _uid FOR UPDATE/);
  });

  it("checks the cap BEFORE incrementing", () => {
    const capIdx = migration.indexOf("action_count >= _allow");
    const incIdx = migration.indexOf("action_count = action_count + 1");
    expect(capIdx).toBeGreaterThan(-1);
    expect(incIdx).toBeGreaterThan(-1);
    expect(capIdx).toBeLessThan(incIdx);
  });

  it("lets admin and lifetime bypass the meter (unlimited)", () => {
    expect(migration).toMatch(/is_admin OR _ent\.source = 'lifetime'/);
  });

  it("resets the counter when the billing period advances", () => {
    expect(migration).toMatch(/period_end = _pend, action_count = 0/);
  });

  it("keeps the meter functions server-only (revoked from anon)", () => {
    for (const fn of ["ai_meter", "ai_usage_status", "ai_allowance"]) {
      expect(migration).toMatch(new RegExp(`REVOKE ALL ON FUNCTION public\\.${fn}[\\s\\S]*?anon`));
    }
  });
});
