import { readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

/**
 * P0 (Codex #17): record_room_event let any participant emit any kind,
 * forging authoritative audit events. Privileged kinds now require host/mod,
 * unknown kinds are rejected.
 */
const migration = readFileSync(
  join(process.cwd(), "supabase", "migrations", "20260825010000_restrict_room_event_kinds.sql"),
  "utf8",
);

describe("record_room_event kind restriction", () => {
  it("treats the authoritative kinds as privileged", () => {
    for (const k of ["moderator_added", "moderator_removed", "join_approved", "join_denied", "completed", "started"]) {
      expect(migration).toContain(`'${k}'`);
    }
    expect(migration).toMatch(/_privileged CONSTANT TEXT\[\]/);
  });

  it("requires host or moderator for privileged kinds", () => {
    expect(migration).toMatch(
      /_kind = ANY\(_privileged\)[\s\S]*?is_room_host\(_room_id, _uid\) OR public\.is_room_moderator\(_room_id, _uid\)[\s\S]*?RAISE EXCEPTION 'not_authorized_for_event_kind'/,
    );
  });

  it("rejects unknown / free-form kinds", () => {
    expect(migration).toMatch(/NOT \(_kind = ANY\(_member_ok\)\)[\s\S]*?RAISE EXCEPTION 'unknown_event_kind'/);
  });

  it("still requires the caller to be in the room at all", () => {
    expect(migration).toMatch(/is_room_participant\(_room_id, _uid\)[\s\S]*?RAISE EXCEPTION 'not_room_member'/);
  });
});
