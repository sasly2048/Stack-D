import { readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

/**
 * participants_protect_scoring must guard the scoring/breach columns on INSERT
 * as well as UPDATE, so a direct-REST join can't seed integrity/breached — the
 * INSERT RLS policy only checks ownership, not column values.
 */
const migration = readFileSync(
  join(process.cwd(), "supabase", "migrations", "20260823190000_participants_protect_insert.sql"),
  "utf8",
);

describe("participants_protect_scoring INSERT guard", () => {
  it("fires on INSERT as well as UPDATE", () => {
    expect(migration).toMatch(/BEFORE INSERT OR UPDATE ON public\.participants/);
  });

  it("forces safe baseline scoring columns on a client INSERT", () => {
    const insertBranch = migration.match(/TG_OP = 'INSERT' THEN([\s\S]*?)ELSE/);
    expect(insertBranch, "INSERT branch").not.toBeNull();
    const body = insertBranch![1];
    expect(body).toMatch(/NEW\.integrity\s*:=\s*100/);
    expect(body).toMatch(/NEW\.breached\s*:=\s*false/);
    expect(body).toMatch(/NEW\.breach_reason\s*:=\s*NULL/);
    expect(body).toMatch(/NEW\.breach_at\s*:=\s*NULL/);
  });

  it("still freezes the authority columns to OLD on UPDATE", () => {
    const updateBranch = migration.match(/ELSE([\s\S]*?)END IF;/);
    expect(updateBranch, "UPDATE branch").not.toBeNull();
    const body = updateBranch![1];
    for (const col of ["integrity", "breached", "breach_reason", "breach_at", "user_id", "room_id"]) {
      expect(body, col).toMatch(new RegExp(`NEW\\.${col}\\s*:=\\s*OLD\\.${col}`));
    }
  });

  it("only guards client roles, not SECURITY DEFINER writers", () => {
    expect(migration).toMatch(/current_user IN \('authenticated', 'anon'\)/);
  });
});
