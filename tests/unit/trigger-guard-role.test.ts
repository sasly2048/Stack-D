import { readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

/**
 * P0 (Codex #10,#12): the RAISE-based trigger guards used
 * `current_user IN ('authenticated','anon')`, which is always false inside a
 * SECURITY DEFINER function owned by postgres — so block enforcement and
 * join-request self-approval protection never ran. They now key off auth.uid()
 * (non-null = a real user is behind the request), the same signal
 * friendship_guard already used.
 */
const migration = readFileSync(
  join(
    process.cwd(),
    "supabase",
    "migrations",
    "20260824070000_fix_trigger_guard_role_detection.sql",
  ),
  "utf8",
);

describe("trigger guards use auth.uid(), not current_user", () => {
  it("no guard in this migration still relies on current_user", () => {
    // The header comment names current_user when describing the old bug; the
    // code must not use it as a guard (the guard forms are `IF current_user
    // IN/NOT IN`). Strip comment lines, then assert.
    const code = migration
      .split("\n")
      .filter((l) => !l.trimStart().startsWith("--"))
      .join("\n");
    expect(code).not.toMatch(/current_user/);
  });

  it("block guards enforce when a user is present", () => {
    // three block guards, each gated on auth.uid() IS NOT NULL
    const gated = migration.match(/IF auth\.uid\(\) IS NOT NULL THEN/g) ?? [];
    expect(gated.length).toBeGreaterThanOrEqual(3);
    expect(migration).toMatch(/friendship_block_guard[\s\S]*?blocks_exist/);
    expect(migration).toMatch(/reaction_block_guard[\s\S]*?blocks_exist/);
    expect(migration).toMatch(/mentorship_block_guard[\s\S]*?blocks_exist/);
  });

  it("join-request + mentorship guards bypass only for no-auth (trusted) writes", () => {
    // both use `IF _uid IS NULL THEN RETURN NEW`
    const bypass = migration.match(/IF _uid IS NULL THEN\s*\n?\s*RETURN NEW;/g) ?? [];
    expect(bypass.length).toBeGreaterThanOrEqual(2);
  });

  it("join-request still blocks requester self-approval", () => {
    expect(migration).toMatch(/room_join_request_guard/);
    expect(migration).toMatch(/requester_may_only_cancel/);
    expect(migration).toMatch(/is_room_moderator\(OLD\.room_id, _uid\)/);
  });

  it("mentorship: invitee-only activation + immutable identities, no dropped fn", () => {
    expect(migration).toMatch(/mentorship_freeze_parties/);
    expect(migration).toMatch(/_uid = OLD\.initiator_id[\s\S]*?RAISE EXCEPTION 'inviter_cannot_accept'/);
    expect(migration).not.toMatch(/created_by_uid_placeholder/);
    expect(migration).toMatch(/NEW\.mentor_id\s*:=\s*OLD\.mentor_id/);
  });
});
