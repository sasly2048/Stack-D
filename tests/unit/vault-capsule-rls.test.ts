import { readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

/**
 * Vault + time-capsule RLS must enforce the Elite entitlement, not only row
 * ownership. Both features are Elite-only (premium-catalog.ts), but their
 * server functions are the only gate — a user with the public anon key and
 * their own JWT can hit PostgREST directly. So the *policies* must require
 * has_tier('elite'), otherwise a free/pro user reads and writes their own
 * vault/capsules by bypassing the server layer.
 *
 * Text-based like db-permissions.test.ts: runs in CI with no database.
 */
const migration = readFileSync(
  join(process.cwd(), "supabase", "migrations", "20260823180000_vault_capsule_elite_rls.sql"),
  "utf8",
);

describe("vault + capsule RLS enforces Elite tier", () => {
  // The CREATE POLICY ... FOR ALL block, from the table name to the trailing
  // semicolon. Both USING and WITH CHECK live inside it; asserting on the whole
  // block sidesteps trying to balance the parens of `auth.uid()` in a regex.
  function policyBlock(table: string): string {
    const m = migration.match(
      new RegExp(`CREATE POLICY[^;]*?ON public\\.${table}\\b[\\s\\S]*?;`),
    );
    expect(m, `${table} policy`).not.toBeNull();
    return m![0];
  }

  it("vault policy requires ownership AND has_tier('elite')", () => {
    const block = policyBlock("memory_vault_items");
    expect(block).toMatch(/USING\s*\(/);
    expect(block).toMatch(/WITH CHECK\s*\(/);
    expect(block).toMatch(/auth\.uid\(\)\s*=\s*user_id/);
    expect(block).toMatch(/has_tier\('elite'\)/);
    // Ownership must be conjoined with the tier check, not an OR that a
    // non-Elite owner could satisfy on the ownership side alone.
    expect(block).toMatch(/user_id\s+AND\s+public\.has_tier\('elite'\)/);
  });

  it("capsule policy requires ownership AND has_tier('elite')", () => {
    const block = policyBlock("time_capsules");
    expect(block).toMatch(/USING\s*\(/);
    expect(block).toMatch(/WITH CHECK\s*\(/);
    expect(block).toMatch(/auth\.uid\(\)\s*=\s*user_id/);
    expect(block).toMatch(/has_tier\('elite'\)/);
    expect(block).toMatch(/user_id\s+AND\s+public\.has_tier\('elite'\)/);
  });

  it("drops the old owner-only policies so they cannot linger", () => {
    expect(migration).toMatch(/DROP POLICY IF EXISTS "vault owner only"/);
    expect(migration).toMatch(/DROP POLICY IF EXISTS "own capsules read"/);
    expect(migration).toMatch(/DROP POLICY IF EXISTS "own capsules write"/);
    expect(migration).toMatch(/DROP POLICY IF EXISTS "own capsules update"/);
    expect(migration).toMatch(/DROP POLICY IF EXISTS "own capsules delete"/);
  });
});
