import { readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

/**
 * P1 (Codex #29/#30): a block severs EXISTING relationships and prevents room
 * co-membership, not just new directed interactions.
 */
const trust = readFileSync(join(process.cwd(), "src", "lib", "trust.functions.ts"), "utf8");
const migration = readFileSync(
  join(process.cwd(), "supabase", "migrations", "20260825030000_room_join_block_check.sql"),
  "utf8",
);

describe("block semantics — sever existing + room block", () => {
  it("blockUser severs friendships in both directions (#29)", () => {
    expect(trust).toMatch(/from\("friendships"\)[\s\S]*?\.eq\("requester_id", context\.userId\)[\s\S]*?\.eq\("addressee_id", data\.userId\)/);
    expect(trust).toMatch(/from\("friendships"\)[\s\S]*?\.eq\("requester_id", data\.userId\)[\s\S]*?\.eq\("addressee_id", context\.userId\)/);
  });

  it("blockUser severs mentorships in both role arrangements (#29)", () => {
    expect(trust).toMatch(/from\("mentor_relationships"\)[\s\S]*?\.eq\("mentor_id", context\.userId\)[\s\S]*?\.eq\("mentee_id", data\.userId\)/);
    expect(trust).toMatch(/from\("mentor_relationships"\)[\s\S]*?\.eq\("mentor_id", data\.userId\)[\s\S]*?\.eq\("mentee_id", context\.userId\)/);
  });

  it("claim_room_seat rejects joining when blocked vs the host (#30)", () => {
    expect(migration).toMatch(/blocks_exist\(_uid, _room\.host_id\)[\s\S]*?RAISE EXCEPTION 'blocked'/);
  });

  it("claim_room_seat rejects joining when blocked vs any seated participant (#30)", () => {
    expect(migration).toMatch(
      /participants p[\s\S]*?p\.user_id <> _uid[\s\S]*?blocks_exist\(_uid, p\.user_id\)[\s\S]*?RAISE EXCEPTION 'blocked'/,
    );
  });

  it("the host joining their own room skips the block gate", () => {
    expect(migration).toMatch(/IF _uid <> _room\.host_id THEN/);
  });
});
