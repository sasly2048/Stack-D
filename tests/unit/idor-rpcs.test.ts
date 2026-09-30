import { readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

/**
 * P0/P1 (Codex #14,#15,#16,#19): IDOR-able / over-exposed RPCs are locked, and
 * time-capsule opening is server-authoritative.
 */
const migration = readFileSync(
  join(process.cwd(), "supabase", "migrations", "20260824060000_lock_idor_rpcs_and_capsule.sql"),
  "utf8",
);

describe("IDOR RPC lockdown + capsule lifecycle", () => {
  it("refresh_personality rejects a non-self target for client callers (#14)", () => {
    expect(migration).toMatch(
      /refresh_personality[\s\S]*?current_user IN \('authenticated', 'anon'\) AND _user_id IS DISTINCT FROM auth\.uid\(\)[\s\S]*?RAISE EXCEPTION 'forbidden'/,
    );
  });

  it("user_timezone rejects a non-self target for client callers (#15)", () => {
    expect(migration).toMatch(
      /user_timezone[\s\S]*?_user_id IS DISTINCT FROM auth\.uid\(\)[\s\S]*?RAISE EXCEPTION 'forbidden'/,
    );
  });

  it("blocks_exist is revoked from clients — no block enumeration (#16)", () => {
    expect(migration).toMatch(
      /REVOKE ALL ON FUNCTION public\.blocks_exist\(uuid, uuid\) FROM PUBLIC, anon, authenticated/,
    );
    expect(migration).not.toMatch(
      /GRANT EXECUTE ON FUNCTION public\.blocks_exist\(uuid, uuid\) TO authenticated/,
    );
  });

  it("time_capsules UPDATE is revoked and opening goes through a gated RPC (#19)", () => {
    expect(migration).toMatch(/REVOKE UPDATE ON public\.time_capsules FROM authenticated, anon/);
    expect(migration).toMatch(/CREATE OR REPLACE FUNCTION public\.open_capsule\(_id uuid\)/);
    // the RPC enforces the open_at gate server-side
    expect(migration).toMatch(/IF _open_at > now\(\) THEN RAISE EXCEPTION 'not_yet'/);
  });
});
