import { readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

/**
 * rooms_protect_lifecycle freezes the lifecycle/authority columns against
 * direct client UPDATEs. The "Only host can update room" RLS policy lets the
 * host write any column, but started_at / status / target_duration_seconds
 * feed the scoring system and must only be written by the server-owned RPCs.
 * Cosmetic columns (title, visibility, ...) stay host-editable.
 */
const migration = readFileSync(
  join(process.cwd(), "supabase", "migrations", "20260823200000_rooms_protect_lifecycle.sql"),
  "utf8",
);

describe("rooms_protect_lifecycle trigger", () => {
  it("freezes the lifecycle / authority columns to OLD", () => {
    for (const col of [
      "status",
      "started_at",
      "ended_at",
      "target_duration_seconds",
      "host_id",
      "code",
      "created_at",
    ]) {
      expect(migration, col).toMatch(new RegExp(`NEW\\.${col}\\s*:=\\s*OLD\\.${col}`));
    }
  });

  it("does NOT freeze the cosmetic columns updateRoomMeta writes", () => {
    for (const col of [
      "title",
      "description",
      "banner_url",
      "pinned_message",
      "collective_goal_seconds",
      "visibility",
    ]) {
      expect(migration, col).not.toMatch(new RegExp(`NEW\\.${col}\\s*:=`));
    }
  });

  it("only guards client roles, not SECURITY DEFINER writers", () => {
    expect(migration).toMatch(/current_user IN \('authenticated', 'anon'\)/);
  });

  it("fires as a BEFORE UPDATE trigger on rooms", () => {
    expect(migration).toMatch(/BEFORE UPDATE ON public\.rooms/);
    expect(migration).toMatch(/EXECUTE FUNCTION public\.rooms_protect_lifecycle/);
  });
});
