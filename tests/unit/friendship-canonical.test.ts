import { readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

/**
 * A friendship is one unordered pair, but the table only had
 * UNIQUE (requester_id, addressee_id) — directional — so A->B and B->A were two
 * rows. The migration must dedupe existing reciprocal pairs and add a canonical
 * unique index on LEAST/GREATEST so a reciprocal request is rejected.
 */
const migration = readFileSync(
  join(process.cwd(), "supabase", "migrations", "20260824010000_friendship_canonical_pair.sql"),
  "utf8",
);

describe("friendship canonical-pair constraint", () => {
  it("adds a unique index on the unordered pair", () => {
    expect(migration).toMatch(/CREATE UNIQUE INDEX/i);
    expect(migration).toMatch(/LEAST\(requester_id, addressee_id\)/);
    expect(migration).toMatch(/GREATEST\(requester_id, addressee_id\)/);
  });

  it("dedupes existing reciprocal rows before building the index", () => {
    // DELETE must appear before CREATE UNIQUE INDEX, or the build fails on
    // pre-existing duplicates.
    const delIdx = migration.search(/DELETE FROM public\.friendships/);
    const idxIdx = migration.search(/CREATE UNIQUE INDEX/);
    expect(delIdx).toBeGreaterThanOrEqual(0);
    expect(idxIdx).toBeGreaterThan(delIdx);
  });

  it("keeps the most-progressed row per pair (accepted, then oldest)", () => {
    expect(migration).toMatch(/status = 'accepted'\)\s+DESC/);
    expect(migration).toMatch(/created_at ASC/);
  });

  it("partitions dedup by the unordered pair", () => {
    expect(migration).toMatch(
      /PARTITION BY LEAST\(requester_id, addressee_id\), GREATEST\(requester_id, addressee_id\)/,
    );
  });
});
