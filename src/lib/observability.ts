/**
 * Lightweight client-side observability. Captures product events into
 * `activity_events` (already RLS-scoped to the user) so we can build
 * funnel/retention analytics without a third-party SDK.
 *
 * Silently no-ops when signed out or when the network fails — never
 * throws into UI code.
 */
import { supabase } from "@/integrations/supabase/client";

type EventName =
  | "session.started"
  | "session.completed"
  | "session.breached"
  | "room.created"
  | "room.joined"
  | "atlas.recommendation_shown"
  | "atlas.recommendation_dismissed"
  | "low_power.toggled"
  | "integration.viewed";

const buffer: Array<{ name: EventName; payload: Record<string, unknown>; at: number }> = [];
let flushing = false;

async function flush() {
  if (flushing || buffer.length === 0) return;
  flushing = true;
  try {
    const { data: u } = await supabase.auth.getUser();
    if (!u.user) { flushing = false; return; }
    const batch = buffer.splice(0, buffer.length);
    await supabase.from("activity_events").insert(
      batch.map((e) => ({
        user_id: u.user!.id,
        kind: e.name,
        payload: { ...e.payload, ts: e.at },
      })),
    );
  } catch { /* swallow */ }
  finally { flushing = false; }
}

export function track(name: EventName, payload: Record<string, unknown> = {}) {
  buffer.push({ name, payload, at: Date.now() });
  if (buffer.length >= 5) void flush();
  else setTimeout(flush, 2500);
}

if (typeof window !== "undefined") {
  window.addEventListener("beforeunload", () => { void flush(); });
}
