import { afterAll, beforeAll, describe, expect, it } from "vitest";

import { adminClient, anonClient, makeUser, stackIsUp, type TestUser } from "./helpers";

/**
 * Real RLS/IDOR tests against a local `supabase start` stack. Unlike the
 * text-based migration tests (which check policy SHAPE), these run actual
 * policies with real JWTs: user B tries to read/mutate user A's rows and must
 * be denied by Postgres, not by the client.
 *
 * Skips entirely (does not fail) when the local stack isn't reachable, so
 * `npm test` stays green without Docker. Run with: npm run test:rls
 */
const up = await stackIsUp();
const admin = up ? adminClient() : null;

describe.skipIf(!up)("RLS / IDOR — cross-user access is denied", () => {
  let alice: TestUser;
  let bob: TestUser;

  beforeAll(async () => {
    alice = await makeUser("alice");
    bob = await makeUser("bob");
  });

  afterAll(async () => {
    if (!admin) return;
    // Best-effort cleanup of the two throwaway users.
    for (const u of [alice, bob]) {
      if (u?.id) await admin.auth.admin.deleteUser(u.id).catch(() => {});
    }
  });

  it("anon cannot read profiles (restrict_anon_reads)", async () => {
    const { data, error } = await anonClient().from("profiles").select("id").limit(1);
    // Either an explicit error or an empty set — never another user's row.
    expect(error !== null || (data ?? []).length === 0).toBe(true);
  });

  it("an authenticated user cannot UPDATE another user's participant scoring", async () => {
    // Seed a room + Alice's participant row with service role (bypasses RLS).
    const { data: room } = await admin!
      .from("rooms")
      .insert({ host_id: alice.id, code: `IDOR${Math.floor(performance.now()) % 100000}`, status: "lobby" })
      .select("id")
      .single();
    await admin!
      .from("participants")
      .insert({ room_id: room!.id, user_id: alice.id, integrity: 100, breached: false });

    // Bob tries to tamper with Alice's integrity. Must affect zero rows.
    const { data: updated } = await bob.client
      .from("participants")
      .update({ integrity: 0, breached: true })
      .eq("room_id", room!.id)
      .eq("user_id", alice.id)
      .select("user_id");
    expect(updated ?? []).toHaveLength(0);

    // Confirm Alice's row is untouched (read back with admin).
    const { data: check } = await admin!
      .from("participants")
      .select("integrity, breached")
      .eq("room_id", room!.id)
      .eq("user_id", alice.id)
      .single();
    expect(check!.integrity).toBe(100);
    expect(check!.breached).toBe(false);
  });

  it("even the owner cannot forge their own participant integrity via UPDATE", async () => {
    // The column-freeze trigger fires for client callers regardless of RLS.
    const { data: room } = await admin!
      .from("rooms")
      .insert({ host_id: alice.id, code: `FRZ${Math.floor(performance.now()) % 100000}`, status: "lobby" })
      .select("id")
      .single();
    await admin!
      .from("participants")
      .insert({ room_id: room!.id, user_id: alice.id, integrity: 100, breached: false });

    await alice.client
      .from("participants")
      .update({ integrity: 999, breached: false })
      .eq("room_id", room!.id)
      .eq("user_id", alice.id);

    const { data: check } = await admin!
      .from("participants")
      .select("integrity")
      .eq("room_id", room!.id)
      .eq("user_id", alice.id)
      .single();
    // Frozen to OLD by the trigger — the client value 999 must not stick.
    expect(check!.integrity).toBe(100);
  });

  it("a non-host cannot change a room's lifecycle", async () => {
    const { data: room } = await admin!
      .from("rooms")
      .insert({ host_id: alice.id, code: `LC${Math.floor(performance.now()) % 100000}`, status: "lobby" })
      .select("id")
      .single();

    await bob.client.from("rooms").update({ status: "ended" }).eq("id", room!.id);

    const { data: check } = await admin!
      .from("rooms")
      .select("status")
      .eq("id", room!.id)
      .single();
    expect(check!.status).toBe("lobby");
  });

  it("a blocked user cannot create a friendship with the blocker", async () => {
    // Alice blocks Bob.
    await admin!.from("user_blocks").insert({ blocker_id: alice.id, blocked_id: bob.id });

    // Bob tries to friend Alice — the friendship_block_guard trigger must reject.
    const { error } = await bob.client
      .from("friendships")
      .insert({ requester_id: bob.id, addressee_id: alice.id, status: "pending" });
    expect(error).not.toBeNull();
  });
});
