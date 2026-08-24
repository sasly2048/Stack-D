import { readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

/**
 * P0 (Codex #1-3,#8,#9,#18): the column-freeze triggers were no-ops because
 * SECURITY DEFINER made `current_user` = the function owner, never
 * 'authenticated'. Protection now uses column-level UPDATE grants, which
 * Postgres enforces regardless of any trigger. This asserts the authority
 * columns are NOT granted and the safe ones ARE.
 */
const migration = readFileSync(
  join(
    process.cwd(),
    "supabase",
    "migrations",
    "20260824050000_column_grants_lock_progression.sql",
  ),
  "utf8",
);

/** The set of columns granted UPDATE for a table in this migration. */
function grantedCols(table: string): string[] {
  // Grab the text between "GRANT UPDATE (" and the matching ") ON public.<table>".
  const anchor = `ON public.${table} TO authenticated`;
  // find the GRANT UPDATE whose ON clause names this table
  let idx = 0;
  while (true) {
    const g = migration.indexOf("GRANT UPDATE (", idx);
    if (g < 0) return [];
    const on = migration.indexOf(anchor, g);
    if (on < 0) {
      idx = g + 1;
      continue;
    }
    // ensure no other GRANT UPDATE sits between g and on (i.e. this is the right pair)
    const between = migration.slice(g + 1, on).indexOf("GRANT UPDATE (");
    if (between >= 0) {
      idx = g + 1;
      continue;
    }
    const cols = migration.slice(g + "GRANT UPDATE (".length, on);
    return cols
      .replace(/--[^\n]*/g, "")
      .replace(/\)\s*$/, "")
      .split(",")
      .map((c) => c.trim())
      .filter(Boolean);
  }
}

describe("column-grant lockdown", () => {
  it("revokes UPDATE from client roles on every protected table", () => {
    for (const t of ["participants", "rooms", "room_join_requests", "profiles"]) {
      expect(migration).toMatch(
        new RegExp(`REVOKE UPDATE ON public\\.${t} FROM authenticated, anon`),
      );
    }
  });

  it("locks participant scoring/identity columns", () => {
    const g = grantedCols("participants");
    for (const c of ["integrity", "breached", "breach_reason", "breach_at", "user_id", "room_id"]) {
      expect(g).not.toContain(c);
    }
    expect(g).toContain("display_name"); // still editable
  });

  it("locks room lifecycle/ownership/aggregate columns", () => {
    const g = grantedCols("rooms");
    for (const c of ["status", "started_at", "ended_at", "host_id", "code", "collective_seconds", "target_duration_seconds"]) {
      expect(g).not.toContain(c);
    }
    expect(g).toContain("title"); // host may still edit meta
  });

  it("locks profile progression columns", () => {
    const g = grantedCols("profiles");
    for (const c of ["lifetime_xp", "current_focus_streak", "best_streak", "total_focus_seconds", "prestige_level", "productivity_dna", "title", "timezone"]) {
      expect(g).not.toContain(c);
    }
    expect(g).toContain("display_name");
    expect(g).toContain("bio");
  });

  it("locks join-request identity but allows the moderator status flow", () => {
    const g = grantedCols("room_join_requests");
    for (const c of ["user_id", "room_id", "display_name"]) {
      expect(g).not.toContain(c);
    }
    expect(g).toContain("status");
    expect(g).toContain("responded_at");
  });

  it("adds a SECURITY DEFINER equip_title that verifies ownership", () => {
    expect(migration).toMatch(/CREATE OR REPLACE FUNCTION public\.equip_title\(_title_id text\)/);
    expect(migration).toMatch(/SECURITY DEFINER/);
    expect(migration).toMatch(/FROM public\.user_titles ut[\s\S]*?WHERE ut\.user_id = _uid AND ut\.title_id = _title_id/);
    expect(migration).toMatch(/RAISE EXCEPTION 'not_owned'/);
    expect(migration).toMatch(/GRANT EXECUTE ON FUNCTION public\.equip_title\(text\) TO authenticated/);
  });
});
