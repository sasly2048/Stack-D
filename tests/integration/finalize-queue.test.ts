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
  it("parks a payload and counts it for its owner only", () => {
    enqueueFinalize(payload());
    expect(getQueueSize(ALICE)).toBe(1);
    expect(getQueueSize(BOB)).toBe(0);
  });

  it("dedupes repeat queues of the same (owner, room)", () => {
    enqueueFinalize(payload({ _score: 50 }));
    enqueueFinalize(payload({ _score: 91 }));
    expect(getQueueSize(ALICE)).toBe(1);
  });

  it("keeps distinct rooms separate", () => {
    enqueueFinalize(payload({ _room_id: "room-1" }));
    enqueueFinalize(payload({ _room_id: "room-2" }));
    expect(getQueueSize(ALICE)).toBe(2);
  });

  it("survives corrupt localStorage instead of throwing", () => {
    localStorage.setItem("stackd:finalize-queue", "{not json");
    expect(getQueueSize(ALICE)).toBe(0);
    enqueueFinalize(payload());
    expect(getQueueSize(ALICE)).toBe(1);
  });

  it("notifies subscribers on change", () => {
    const cb = vi.fn();
    const off = subscribeQueue(cb);
    enqueueFinalize(payload());
    expect(cb).toHaveBeenCalled();
    off();
    cb.mockReset();
    enqueueFinalize(payload({ _room_id: "room-9" }));
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
    enqueueFinalize(payload({ _room_id: "room-1" }));
    enqueueFinalize(payload({ _room_id: "room-2" }));

    await flushFinalizeQueue(ALICE);

    expect(rpc).toHaveBeenCalledTimes(2);
    expect(rpc.mock.calls[0][0]).toBe("finalize_focus_session");
    // The owner stamp is a client-side concern, never sent to the RPC.
    expect(rpc.mock.calls[0][1]).not.toHaveProperty("_owner");
    expect(getQueueSize(ALICE)).toBe(0);
  });

  it("retains rows the RPC rejected so the next flush retries", async () => {
    rpc.mockResolvedValue({ error: { message: "offline" } });
    enqueueFinalize(payload());

    await flushFinalizeQueue(ALICE);
    expect(getQueueSize(ALICE)).toBe(1);

    rpc.mockResolvedValue({ error: null });
    await flushFinalizeQueue(ALICE);
    expect(getQueueSize(ALICE)).toBe(0);
  });

  it("keeps partial failures without dropping the successes", async () => {
    rpc
      .mockResolvedValueOnce({ error: null })
      .mockResolvedValueOnce({ error: { message: "boom" } });
    enqueueFinalize(payload({ _room_id: "room-1" }));
    enqueueFinalize(payload({ _room_id: "room-2" }));

    await flushFinalizeQueue(ALICE);
    expect(getQueueSize(ALICE)).toBe(1);
  });

  it("never replays another account's payload after sign-out", async () => {
    rpc.mockResolvedValue({ error: null });
    enqueueFinalize(payload({ _owner: ALICE, _room_id: "room-1" }));
    enqueueFinalize(payload({ _owner: BOB, _room_id: "room-2" }));

    await flushFinalizeQueue(ALICE);

    expect(rpc).toHaveBeenCalledTimes(1);
    expect(getQueueSize(BOB)).toBe(1);
  });

  it("fires the ceremony event for delayed finalizes", async () => {
    rpc.mockResolvedValue({ error: null });
    const seen: CustomEvent[] = [];
    const handler = (e: Event) => seen.push(e as CustomEvent);
    window.addEventListener("stackd:ceremony", handler);

    enqueueFinalize(payload({ _xp: 250, _tier: "obsidian" }));
    await flushFinalizeQueue(ALICE);
    window.removeEventListener("stackd:ceremony", handler);

    expect(seen).toHaveLength(1);
    expect(seen[0].detail).toMatchObject({ xpEarned: 250, tier: "obsidian" });
  });
});
