import { readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

/**
 * room_join_request_guard must stop a requester approving their own join
 * request. The UPDATE policy OR-joins "own row" with "is moderator", which
 * would otherwise let a user set status='approved' on their own request and
 * bypass the approval gate for request-only rooms.
 */
const migration = readFileSync(
  join(
    process.cwd(),
    "supabase",
    "migrations",
    "20260823210000_join_request_no_self_approve.sql",
  ),
  "utf8",
);

describe("room_join_request_guard trigger", () => {
  it("lets the requester only move pending -> cancelled", () => {
    // The requester branch keys off auth.uid() = OLD.user_id and permits
    // exactly the cancel transition, raising otherwise.
    expect(migration).toMatch(/_uid = OLD\.user_id/);
    expect(migration).toMatch(/OLD\.status = 'pending' AND NEW\.status = 'cancelled'/);
    expect(migration).toMatch(/requester_may_only_cancel/);
  });

  it("lets a moderator move pending -> approved/denied", () => {
    expect(migration).toMatch(/is_room_moderator\(OLD\.room_id, _uid\)/);
    expect(migration).toMatch(/NEW\.status IN \('approved', 'denied'\)/);
  });

  it("does not silently allow a self-approve (must RAISE)", () => {
    // There must be no code path where a requester reaches approved without an
    // exception: the only requester-branch RETURN is the cancel case.
    // Capture from the requester IF up to the moderator IF — the whole
    // requester branch, including its trailing RAISE.
    const requesterBranch = migration.match(
      /IF _uid = OLD\.user_id THEN([\s\S]*?)IF public\.is_room_moderator/,
    );
    expect(requesterBranch, "requester branch").not.toBeNull();
    const body = requesterBranch![1];
    expect(body).not.toMatch(/approved/);
    expect(body).toMatch(/RAISE EXCEPTION 'requester_may_only_cancel'/);
  });

  it("bypasses SECURITY DEFINER / service-role writers", () => {
    expect(migration).toMatch(/current_user NOT IN \('authenticated', 'anon'\)/);
  });

  it("allows status-unchanged metadata updates", () => {
    expect(migration).toMatch(/NEW\.status IS NOT DISTINCT FROM OLD\.status/);
  });
});
