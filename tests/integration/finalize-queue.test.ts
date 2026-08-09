/**
 * Integration test for the offline-tolerant finalize queue.
 *
 * Covers the real failure mode this exists for: a session ends, the
 * `finalize_focus_session` RPC fails, the payload survives in localStorage,
 * and a later flush replays it exactly once — without leaking another
 * account's rows.
 */
import { describe, it, expect, beforeEach, vi } from "vitest";

const rpc = vi.fn();
vi.mock("@/integrations/supabase/client", () => ({
  supabase: { rpc: (...args: unknown[]) => rpc(...args) },
}));

import {
  enqueueFinalize,
  flushFinalizeQueue,
  getQueueSize,
  isDue,
  nextAttemptDelay,
  subscribeQueue,
  type FinalizePayload,
} from "@/lib/finalize-queue";

const ALICE = "alice-uuid";
const BOB = "bob-uuid";

function payload(over: Partial<FinalizePayload> = {}): FinalizePayload {
  return {
    _room_id: "room-1",
    _score: 88,
    _xp: 120,
    _duration_seconds: 1500,
    _breaches_count: 0,
    _tier: "gold",
    _owner: ALICE,
    _queued_at: Date.now(),
    ...over,
  };
}

beforeEach(() => {
  localStorage.clear();
  rpc.mockReset();
});

describe("queue persistence", () => {
  it("parks a payload and counts it for its owner only", async () => {
    await enqueueFinalize(payload());
    expect(await getQueueSize(ALICE)).toBe(1);
    expect(await getQueueSize(BOB)).toBe(0);
  });

  it("dedupes repeat queues of the same (owner, room)", async () => {
    await enqueueFinalize(payload({ _score: 50 }));
    await enqueueFinalize(payload({ _score: 91 }));
    expect(await getQueueSize(ALICE)).toBe(1);
  });

  it("keeps distinct rooms separate", async () => {
    await enqueueFinalize(payload({ _room_id: "room-1" }));
    await enqueueFinalize(payload({ _room_id: "room-2" }));
    expect(await getQueueSize(ALICE)).toBe(2);
  });

  it("survives corrupt localStorage instead of throwing", async () => {
    localStorage.setItem("stackd:finalize-queue", "{not json");
    expect(await getQueueSize(ALICE)).toBe(0);
    await enqueueFinalize(payload());
    expect(await getQueueSize(ALICE)).toBe(1);
  });

  it("notifies subscribers on change", async () => {
    const cb = vi.fn();
    const off = subscribeQueue(cb);
    await enqueueFinalize(payload());
    expect(cb).toHaveBeenCalled();
    off();
    cb.mockReset();
    await enqueueFinalize(payload({ _room_id: "room-9" }));
    expect(cb).not.toHaveBeenCalled();
  });
});

describe("flush", () => {
  it("no-ops without a network call when the queue is empty", async () => {
    await flushFinalizeQueue(ALICE);
    expect(rpc).not.toHaveBeenCalled();
  });

  it("replays queued rows and clears them on success", async () => {
    rpc.mockResolvedValue({ error: null });
    await enqueueFinalize(payload({ _room_id: "room-1" }));
    await enqueueFinalize(payload({ _room_id: "room-2" }));

    await flushFinalizeQueue(ALICE);

    expect(rpc).toHaveBeenCalledTimes(2);
    expect(rpc.mock.calls[0][0]).toBe("finalize_focus_session");
    // The owner stamp is a client-side concern, never sent to the RPC.
    expect(rpc.mock.calls[0][1]).not.toHaveProperty("_owner");
    expect(await getQueueSize(ALICE)).toBe(0);
  });

  it("retains rows the RPC rejected so the next flush retries", async () => {
    rpc.mockResolvedValue({ error: { message: "offline" } });
    await enqueueFinalize(payload());

    await flushFinalizeQueue(ALICE);
    expect(await getQueueSize(ALICE)).toBe(1);

    // A failed row backs off, so an *automatic* flush moments later correctly
    // skips it — that is what stops a dead row hammering the API.
    rpc.mockResolvedValue({ error: null });
    await flushFinalizeQueue(ALICE);
    expect(await getQueueSize(ALICE)).toBe(1);

    // An explicit retry (the queue badge's button) ignores backoff.
    await flushFinalizeQueue(ALICE, { force: true });
    expect(await getQueueSize(ALICE)).toBe(0);
  });

  it("keeps partial failures without dropping the successes", async () => {
    rpc
      .mockResolvedValueOnce({ error: null })
      .mockResolvedValueOnce({ error: { message: "boom" } });
    await enqueueFinalize(payload({ _room_id: "room-1" }));
    await enqueueFinalize(payload({ _room_id: "room-2" }));

    await flushFinalizeQueue(ALICE);
    expect(await getQueueSize(ALICE)).toBe(1);
  });

  it("never replays another account's payload after sign-out", async () => {
    rpc.mockResolvedValue({ error: null });
    await enqueueFinalize(payload({ _owner: ALICE, _room_id: "room-1" }));
    await enqueueFinalize(payload({ _owner: BOB, _room_id: "room-2" }));

    await flushFinalizeQueue(ALICE);

    expect(rpc).toHaveBeenCalledTimes(1);
    expect(await getQueueSize(BOB)).toBe(1);
  });

  it("fires the ceremony event for delayed finalizes", async () => {
    rpc.mockResolvedValue({ error: null });
    const seen: CustomEvent[] = [];
    const handler = (e: Event) => seen.push(e as CustomEvent);
    window.addEventListener("stackd:ceremony", handler);

    await enqueueFinalize(payload({ _xp: 250, _tier: "obsidian" }));
    await flushFinalizeQueue(ALICE);
    window.removeEventListener("stackd:ceremony", handler);

    expect(seen).toHaveLength(1);
    expect(seen[0].detail).toMatchObject({ xpEarned: 250, tier: "obsidian" });
  });
});

/**
 * Backoff exists so a permanently-failing row (deleted room, revoked access)
 * stops retrying on every mount for the life of the install.
 */
describe("retry backoff", () => {
  it("grows the delay with each attempt", () => {
    expect(nextAttemptDelay(1)).toBeLessThan(nextAttemptDelay(2));
    expect(nextAttemptDelay(2)).toBeLessThan(nextAttemptDelay(3));
  });

  it("caps the delay so a row still retries eventually", () => {
    const capped = nextAttemptDelay(99);
    expect(capped).toBe(nextAttemptDelay(100));
    expect(capped).toBeLessThanOrEqual(6 * 60 * 60 * 1000);
  });

  it("holds a row back until its next attempt is due", () => {
    const now = 1_000_000;
    const row = { _attempts: 1, _next_attempt_at: now + 30_000 } as never;
    expect(isDue(row, now)).toBe(false);
    expect(isDue(row, now + 30_000)).toBe(true);
  });

  it("stops retrying after the attempt ceiling, rather than forever", () => {
    const row = { _attempts: 99, _next_attempt_at: 0 } as never;
    expect(isDue(row, Date.now())).toBe(false);
  });

  it("treats rows queued before backoff existed as due immediately", () => {
    // Entries already on disk have neither field and must not be stranded.
    const legacy = {} as never;
    expect(isDue(legacy, Date.now())).toBe(true);
  });
});
