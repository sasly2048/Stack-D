import { readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

/**
 * P2-19: list endpoints whose breadth is controlled by OTHER users (join
 * requests, moderators, schedule, reactions) must cap their fetch so one
 * lookup can't buffer an unbounded result set. This guards the SQL text; the
 * numeric input caps are enforced by the zod validators in the same files.
 */
function read(file: string): string {
  return readFileSync(join(process.cwd(), "src", "lib", file), "utf8");
}

describe("resource limits on other-user-controlled lists", () => {
  it("caps pending join requests and moderators per room", () => {
    const src = read("rooms2.functions.ts");
    // listJoinRequests + listRoomModerators each end their chain with .limit(N)
    expect(src).toMatch(/room_join_requests[\s\S]*?\.limit\(200\)/);
    expect(src).toMatch(/room_moderators[\s\S]*?\.limit\(100\)/);
  });

  it("caps room schedule events", () => {
    expect(read("room-extras.functions.ts")).toMatch(
      /room_scheduled_events[\s\S]*?\.limit\(100\)/,
    );
  });

  it("caps the per-session reaction fetch", () => {
    expect(read("session-interactions.functions.ts")).toMatch(
      /session_reactions[\s\S]*?\.limit\(2000\)/,
    );
  });

  it("caps the friends list", () => {
    expect(read("friends.functions.ts")).toMatch(/friendships[\s\S]*?\.limit\(1000\)/);
  });

  it("keeps the timeline input limit bounded (zod max 50)", () => {
    expect(read("session-interactions.functions.ts")).toMatch(
      /limit:\s*z\.number\(\)\.int\(\)\.min\(1\)\.max\(50\)/,
    );
  });
});
