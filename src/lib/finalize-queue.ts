/**
 * Offline-tolerant finalize queue — Brief §3.
 *
 * If the device is offline (or the network blip eats our `finalize_focus_session`
 * RPC), the payload is parked in localStorage. On next mount of the dashboard /
 * room, `flushFinalizeQueue()` retries every queued record idempotently
 * (the RPC itself is one-row-per-(profile,room) safe).
 */
import { supabase } from "@/integrations/supabase/client";

const KEY = "stackd:finalize-queue";
const EVT = "stackd:finalize-queue:change";

export interface FinalizePayload {
  _room_id: string;
  _score: number;
  _xp: number;
  _duration_seconds: number;
  _breaches_count: number;
  _tier: string;
  /** owner stamp so we don't replay another account's payload after sign-out */
  _owner: string;
  _queued_at: number;
}

function read(): FinalizePayload[] {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function write(rows: FinalizePayload[]) {
  try {
    localStorage.setItem(KEY, JSON.stringify(rows));
    window.dispatchEvent(new CustomEvent(EVT));
  } catch { /* quota / private mode */ }
}

export function getQueueSize(ownerId: string): number {
  return read().filter((r) => r._owner === ownerId).length;
}

export function subscribeQueue(cb: () => void): () => void {
  const handler = () => cb();
  const storage = (e: StorageEvent) => { if (e.key === KEY) cb(); };
  window.addEventListener(EVT, handler);
  window.addEventListener("storage", storage);
  return () => {
    window.removeEventListener(EVT, handler);
    window.removeEventListener("storage", storage);
  };
}

export function enqueueFinalize(payload: FinalizePayload) {
  const rows = read();
  // Dedupe by (owner, room) so multiple retries don't pile up.
  const filtered = rows.filter((r) => !(r._owner === payload._owner && r._room_id === payload._room_id));
  filtered.push(payload);
  write(filtered);
}

export async function flushFinalizeQueue(ownerId: string): Promise<void> {
  const rows = read().filter((r) => r._owner === ownerId);
  if (rows.length === 0) return;
  const survivors: FinalizePayload[] = [];
  for (const r of rows) {
    const { error } = await supabase.rpc("finalize_focus_session", {
      _room_id: r._room_id,
      _score: r._score,
      _xp: r._xp,
      _duration_seconds: r._duration_seconds,
      _breaches_count: r._breaches_count,
      _tier: r._tier,
    });
    if (error) {
      survivors.push(r);
    } else if (typeof window !== "undefined") {
      // Delayed finalizes still deserve a moment — smaller, non-blocking.
      window.dispatchEvent(new CustomEvent("stackd:ceremony", {
        detail: {
          durationSeconds: r._duration_seconds,
          xpEarned: r._xp,
          score: r._score,
          tier: r._tier,
        },
      }));
    }
  }
  // Preserve other owners' rows untouched.
  const others = read().filter((r) => r._owner !== ownerId);
  write([...others, ...survivors]);
}
