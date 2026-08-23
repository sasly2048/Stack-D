import { readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

/**
 * A block must actually block. user_blocks was store-only; this enforces it on
 * the friend-request path via a symmetric block check + BEFORE INSERT trigger
 * on friendships.
 */
const migration = readFileSync(
  join(process.cwd(), "supabase", "migrations", "20260824020000_enforce_blocks_on_friendship.sql"),
  "utf8",
);

describe("block enforcement on friendships", () => {
  it("checks blocks symmetrically (either direction)", () => {
    expect(migration).toMatch(/blocker_id = _a AND blocked_id = _b/);
    expect(migration).toMatch(/blocker_id = _b AND blocked_id = _a/);
  });

  it("rejects a friendship INSERT between blocked users", () => {
    expect(migration).toMatch(/BEFORE INSERT ON public\.friendships/);
    expect(migration).toMatch(/blocks_exist\(NEW\.requester_id, NEW\.addressee_id\)/);
    expect(migration).toMatch(/RAISE EXCEPTION 'blocked'/);
  });

  it("only guards client callers, not SECURITY DEFINER writers", () => {
    expect(migration).toMatch(/current_user IN \('authenticated', 'anon'\)/);
  });

  it("revokes the helper from anon and the guard from everyone", () => {
    expect(migration).toMatch(/REVOKE ALL ON FUNCTION public\.blocks_exist\(uuid, uuid\) FROM PUBLIC, anon/);
    expect(migration).toMatch(
      /REVOKE ALL ON FUNCTION public\.friendship_block_guard\(\) FROM PUBLIC, anon, authenticated/,
    );
  });
});
