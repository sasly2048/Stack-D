import { readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

/**
 * Guards the friendship self-accept fix. The requester must never be able to
 * move their own pending request to 'accepted' (that would self-grant access to
 * friends-only data via are_friends). Enforcement is a trigger; assert its
 * invariants at the migration level so a later edit can't quietly remove them.
 */
const migration = readFileSync(
  join(process.cwd(), "supabase", "migrations", "20260819060000_fix_friendship_self_accept.sql"),
  "utf8",
);

describe("friendship acceptance guard", () => {
  it("blocks a non-addressee from moving a pending request to accepted", () => {
    expect(migration).toMatch(/OLD\.status = 'pending'/);
    expect(migration).toMatch(/NEW\.status = 'accepted'/);
    expect(migration).toMatch(/_uid <> OLD\.addressee_id/);
    expect(migration).toMatch(/RAISE EXCEPTION/);
  });

  it("fires as a BEFORE UPDATE trigger on friendships", () => {
    expect(migration).toMatch(/BEFORE UPDATE ON public\.friendships/);
    expect(migration).toMatch(/EXECUTE FUNCTION public\.friendship_guard/);
  });

  it("freezes the two party ids on update (can't repoint a friendship)", () => {
    expect(migration).toMatch(/NEW\.requester_id := OLD\.requester_id/);
    expect(migration).toMatch(/NEW\.addressee_id := OLD\.addressee_id/);
  });
});
