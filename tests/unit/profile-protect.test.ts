import { readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

/**
 * Guards the profile scoring-protection trigger: a client UPDATE must not be
 * able to inflate XP/streaks/prestige, but must still be able to change safe
 * presentation fields and its own username/title.
 */
const migration = readFileSync(
  join(process.cwd(), "supabase", "migrations", "20260823170000_protect_profile_scoring.sql"),
  "utf8",
);

describe("profiles_protect_scoring trigger", () => {
  it("freezes the progression / integrity columns to OLD", () => {
    for (const col of [
      "lifetime_xp",
      "current_focus_streak",
      "best_streak",
      "total_focus_seconds",
      "prestige_level",
      "productivity_dna",
      "scoring_version",
      "created_at",
    ]) {
      expect(migration, col).toMatch(new RegExp(`NEW\\.${col}\\s*:=\\s*OLD\\.${col}`));
    }
  });

  it("does NOT freeze the client-editable fields (would break their flows)", () => {
    // username change + title equip both write via the user's client.
    expect(migration).not.toMatch(/NEW\.username\b\s*:=/);
    expect(migration).not.toMatch(/NEW\.username_canonical\s*:=/);
    expect(migration).not.toMatch(/NEW\.title\s*:=/);
  });

  it("bypasses trusted writers (service role / SECURITY DEFINER)", () => {
    expect(migration).toMatch(/current_user NOT IN \('authenticated', 'anon'\)/);
    expect(migration).toMatch(/RETURN NEW/);
  });

  it("fires as a BEFORE UPDATE trigger on profiles", () => {
    expect(migration).toMatch(/BEFORE UPDATE ON public\.profiles/);
    expect(migration).toMatch(/EXECUTE FUNCTION public\.profiles_protect_scoring/);
  });
});
