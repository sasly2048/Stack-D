import { readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

/**
 * P0 (Codex #4): finalize_focus_session must derive score + duration
 * server-side from timestamps and breach data, not trust the client. The one
 * client input (abandonment) is clamped so it can only lower the score.
 */
const migration = readFileSync(
  join(
    process.cwd(),
    "supabase",
    "migrations",
    "20260825060000_finalize_server_authoritative_score.sql",
  ),
  "utf8",
);

describe("server-authoritative finalize", () => {
  it("derives duration from timestamps, not the client value", () => {
    expect(migration).toMatch(/_started := GREATEST\(COALESCE\(_room\.started_at, _part\.joined_at\)/);
    expect(migration).toMatch(/_ended\s*:= COALESCE\(_part\.left_at, _room\.ended_at, now\(\)\)/);
    expect(migration).toMatch(/EXTRACT\(EPOCH FROM \(_ended - _started\)\)/);
    // the client _duration_seconds param is documented as ignored
    expect(migration).toMatch(/_duration_seconds integer,\s*--\s*IGNORED/);
  });

  it("counts breaches by severity server-side with the documented penalties", () => {
    expect(migration).toMatch(/COUNT\(\*\) FILTER \(WHERE severity = 'minor'\)/);
    expect(migration).toMatch(/COUNT\(\*\) FILTER \(WHERE severity = 'severe'\)/);
    expect(migration).toMatch(/\(_minor \* 10\) \+ \(_severe \* 40\)/); // 10 minor / 40 severe
  });

  it("clamps client abandonment so it can only lower the score", () => {
    expect(migration).toMatch(/_abandon := LEAST\(GREATEST\(COALESCE\(_abandonment_seconds, 0\), 0\), _duration\)/);
    expect(migration).toMatch(/GREATEST\(_abandon - 15, 0\)/); // 15s grace
  });

  it("computes score + xp from the server-derived values, ignoring client _score/_xp", () => {
    expect(migration).toMatch(/_score integer,\s*--\s*IGNORED/);
    expect(migration).toMatch(/_xp integer,\s*--\s*IGNORED/);
    expect(migration).toMatch(/_raw := LEAST\(GREATEST\(\(_duration::NUMERIC \/ _target\) \* 100 - _penalty, 0\), 100\)/);
    expect(migration).toMatch(/_accept_xp := GREATEST\(FLOOR\(_raw \* \(_duration::NUMERIC \/ 60\) \* _multiplier\)/);
  });

  it("drops the older overloads so the call is unambiguous", () => {
    expect(migration).toMatch(/DROP FUNCTION IF EXISTS public\.finalize_focus_session\(uuid, integer, integer, integer, integer, text\)/);
    expect(migration).toMatch(/DROP FUNCTION IF EXISTS public\.finalize_focus_session\(uuid, integer, integer, integer, integer, text, smallint\)/);
  });

  it("stays idempotent (one history row per profile+room)", () => {
    expect(migration).toMatch(/SELECT id INTO _history_id FROM public\.focus_history WHERE profile_id = _uid AND room_id = _room_id/);
    expect(migration).toMatch(/IF _history_id IS NOT NULL THEN RETURN _history_id/);
  });
});
