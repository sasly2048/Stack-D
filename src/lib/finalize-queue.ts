/**
 * Offline-tolerant finalize queue — Brief §3.
 *
 * If the device is offline (or a network blip eats our `finalize_focus_session`
 * RPC), the payload is parked on disk and replayed later. The RPC is
 * one-row-per-(profile, room) idempotent, so replaying is always safe.
 *
 * Storage is IndexedDB, with localStorage as a fallback. localStorage was the
 * original home and is a poor fit for reward-critical data: it is synchronous
 * and shared across the whole origin, so unrelated code filling the ~5MB quota
 * makes `setItem` throw — silently dropping a completed session's XP. Anything
 * already queued under the old key is migrated on first read.
 */
import { idb } from "@/lib/idb-store";
import { notifyXpChanged } from "@/lib/xp-sync";
import { supabase } from "@/integrations/supabase/client";

const KEY = "stackd:finalize-queue";
const EVT = "stackd:finalize-queue:change";
const STORE = "finalize-queue";

/** Give up after this many attempts. */
const MAX_ATTEMPTS = 8;
/** Base for exponential backoff; doubles per attempt, capped. */
const BACKOFF_BASE_MS = 30_000;
const BACKOFF_MAX_MS = 6 * 60 * 60 * 1000; // 6h

export interface FinalizePayload {
  _room_id: string;
  _score: number;
  _xp: number;
  _duration_seconds: number;
  _breaches_count: number;
  _tier: string;
  /**
   * Scoring ruleset in force when this session completed. Optional because
   * entries queued before this field existed are still on disk and must
   * replay rather than crash.
   */
  _scoring_version?: number;
  /** owner stamp so we don't replay another account's payload after sign-out */
  _owner: string;
  _queued_at: number;
  /** Retry bookkeeping. Absent on rows written before backoff existed. */
  _attempts?: number;
  _next_attempt_at?: number;
}

/** IndexedDB needs a keyPath; (owner, room) is the queue's natural identity. */
type StoredPayload = FinalizePayload & { id: string };
const idFor = (p: FinalizePayload) => `${p._owner}:${p._room_id}`;

/* -------------------------------------------------------------------------- */
/*  Storage — IndexedDB with a localStorage fallback                           */
/* -------------------------------------------------------------------------- */

let useIdb: boolean | null = null;
async function idbUsable(): Promise<boolean> {
  if (useIdb === null) useIdb = await idb.available();
  return useIdb;
}

function readLocal(): FinalizePayload[] {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function writeLocal(rows: FinalizePayload[]) {
  try {
    localStorage.setItem(KEY, JSON.stringify(rows));
    window.dispatchEvent(new CustomEvent(EVT));
  } catch {
    /* quota / private mode — nothing better available on this path */
  }
}

/** Moves anything left under the old localStorage key into IndexedDB, once. */
async function migrateLegacy(): Promise<void> {
  const legacy = readLocal();
  if (legacy.length === 0) return;
  for (const row of legacy) {
    await idb.put<StoredPayload>(STORE, { ...row, id: idFor(row) });
  }
  try {
    localStorage.removeItem(KEY);
  } catch {
    /* ignore */
  }
}

async function readAll(): Promise<FinalizePayload[]> {
  if (await idbUsable()) {
    await migrateLegacy();
    return await idb.getAll<StoredPayload>(STORE);
  }
  return readLocal();
}

async function putRow(row: FinalizePayload): Promise<void> {
  if (await idbUsable()) {
    await idb.put<StoredPayload>(STORE, { ...row, id: idFor(row) });
    window.dispatchEvent(new CustomEvent(EVT));
    return;
  }
  const rows = readLocal().filter((r) => idFor(r) !== idFor(row));
  writeLocal([...rows, row]);
}

async function removeRow(row: FinalizePayload): Promise<void> {
  if (await idbUsable()) {
    await idb.remove(STORE, idFor(row));
    window.dispatchEvent(new CustomEvent(EVT));
    return;
  }
  writeLocal(readLocal().filter((r) => idFor(r) !== idFor(row)));
}

/* -------------------------------------------------------------------------- */
/*  Public API                                                                */
/* -------------------------------------------------------------------------- */

export async function getQueueSize(ownerId: string): Promise<number> {
  return (await readAll()).filter((r) => r._owner === ownerId).length;
}

export function subscribeQueue(cb: () => void): () => void {
  const handler = () => cb();
  const storage = (e: StorageEvent) => {
    if (e.key === KEY) cb();
  };
  window.addEventListener(EVT, handler);
  window.addEventListener("storage", storage);
  return () => {
    window.removeEventListener(EVT, handler);
    window.removeEventListener("storage", storage);
  };
}

/**
 * Parks a payload for later replay.
 *
 * Returns the write promise so a caller that needs the row to be durable
 * before continuing can await it. Fire-and-forget remains fine for the room
 * screen, which enqueues on a path it is about to navigate away from.
 */
export function enqueueFinalize(payload: FinalizePayload): Promise<void> {
  // Keyed by (owner, room), so a repeat enqueue for the same session replaces
  // rather than piles up.
  return putRow({ ...payload, _attempts: 0, _next_attempt_at: 0 });
}

/** Exponential backoff, so a persistently failing row stops hammering the API. */
export function nextAttemptDelay(attempts: number): number {
  return Math.min(BACKOFF_BASE_MS * 2 ** Math.max(0, attempts - 1), BACKOFF_MAX_MS);
}

/** Whether a row is due for another attempt. Exported for tests. */
export function isDue(row: FinalizePayload, now: number): boolean {
  if ((row._attempts ?? 0) >= MAX_ATTEMPTS) return false;
  return now >= (row._next_attempt_at ?? 0);
}

/**
 * Replays queued finalizations.
 *
 * `force` bypasses the backoff schedule. Automatic flushes (mount, reconnect)
 * must respect it so a permanently failing row stops hammering the API, but a
 * user pressing "Retry" is asking for an attempt *now* — honouring backoff
 * there would make the button appear broken.
 */
export async function flushFinalizeQueue(
  ownerId: string,
  { force = false }: { force?: boolean } = {},
): Promise<void> {
  const now = Date.now();
  const rows = (await readAll()).filter(
    (r) => r._owner === ownerId && (force || isDue(r, now)),
  );
  if (rows.length === 0) return;

  for (const r of rows) {
    const { data: hid, error } = await supabase.rpc("finalize_focus_session", {
      _room_id: r._room_id,
      _score: r._score,
      _xp: r._xp,
      _duration_seconds: r._duration_seconds,
      _breaches_count: r._breaches_count,
      _tier: r._tier,
      // A queued session may flush days later, possibly after the formula has
      // changed. Replaying it under today's rules would rewrite history, so
      // the version captured at completion travels with the payload.
      _scoring_version: r._scoring_version,
    });

    if (error) {
      // Keep it, but back off. Previously every failure retried at the same
      // cadence forever, so a permanently rejected row (deleted room, revoked
      // access) retried on every mount for the life of the install.
      const attempts = (r._attempts ?? 0) + 1;
      await putRow({
        ...r,
        _attempts: attempts,
        _next_attempt_at: Date.now() + nextAttemptDelay(attempts),
      });
      continue;
    }

    await removeRow(r);
    if (typeof window !== "undefined") {
      notifyXpChanged();
      // Delayed finalizes still deserve a moment — smaller, non-blocking.
      window.dispatchEvent(
        new CustomEvent("stackd:ceremony", {
          detail: {
            durationSeconds: r._duration_seconds,
            xpEarned: r._xp,
            score: r._score,
            tier: r._tier,
            historyId: typeof hid === "string" ? hid : undefined,
          },
        }),
      );
    }
  }
}
