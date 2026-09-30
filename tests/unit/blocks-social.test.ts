import { readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

/**
 * P2-17: a block must sever social interaction, not just friend requests.
 * Triggers on session_reactions and mentor_relationships reject inserts
 * between blocked users, reusing the symmetric blocks_exist() helper.
 */
const migration = readFileSync(
  join(process.cwd(), "supabase", "migrations", "20260824040000_enforce_blocks_social.sql"),
  "utf8",
);

describe("block enforcement on social interactions", () => {
  it("guards session reactions by the session owner", () => {
    expect(migration).toMatch(/BEFORE INSERT ON public\.session_reactions/);
    // owner is resolved from focus_history, then checked symmetrically
    expect(migration).toMatch(/SELECT profile_id INTO _owner FROM public\.focus_history/);
    expect(migration).toMatch(/blocks_exist\(NEW\.user_id, _owner\)/);
  });

  it("guards mentorship pairing in either role", () => {
    expect(migration).toMatch(/BEFORE INSERT ON public\.mentor_relationships/);
    expect(migration).toMatch(/blocks_exist\(NEW\.mentor_id, NEW\.mentee_id\)/);
  });

  it("only guards client callers, not trusted writers", () => {
    const guards = migration.match(/current_user IN \('authenticated', 'anon'\)/g) ?? [];
    expect(guards.length).toBe(2); // one per trigger fn
  });

  it("locks down both guard functions", () => {
    expect(migration).toMatch(
      /REVOKE ALL ON FUNCTION public\.reaction_block_guard\(\) FROM PUBLIC, anon, authenticated/,
    );
    expect(migration).toMatch(
      /REVOKE ALL ON FUNCTION public\.mentorship_block_guard\(\) FROM PUBLIC, anon, authenticated/,
    );
  });

  it("raises a clean 'blocked' error, not a leak", () => {
    const raises = migration.match(/RAISE EXCEPTION 'blocked'/g) ?? [];
    expect(raises.length).toBe(2);
  });
});
