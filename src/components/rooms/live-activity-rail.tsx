import { useEffect, useState } from "react";
import { supabase } from "@/integrations/supabase/client";
import { useServerFn } from "@tanstack/react-start";
import { listRoomEvents, type RoomEvent } from "@/lib/rooms2.functions";

const KIND_LABEL: Record<string, string> = {
  joined: "joined the room",
  left: "left the room",
  started: "started the session",
  paused: "paused",
  resumed: "resumed",
  breach: "broke the stack",
  completed: "completed a session",
  pinned: "pinned a message",
  goal_hit: "hit the collective goal",
  moderator_added: "was promoted to moderator",
  moderator_removed: "was demoted",
  join_requested: "requested to join",
  join_approved: "was approved to join",
  join_denied: "was denied",
};

const KIND_GLYPH: Record<string, string> = {
  joined: "→",
  left: "←",
  started: "▶",
  paused: "‖",
  resumed: "▶",
  breach: "✕",
  completed: "◆",
  pinned: "★",
  goal_hit: "◎",
  moderator_added: "+",
  moderator_removed: "−",
  join_requested: "?",
  join_approved: "✓",
  join_denied: "✕",
};

function timeAgo(iso: string): string {
  const s = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000));
  if (s < 60) return `${s}s`;
  if (s < 3600) return `${Math.floor(s / 60)}m`;
  if (s < 86400) return `${Math.floor(s / 3600)}h`;
  return `${Math.floor(s / 86400)}d`;
}

export function LiveActivityRail({ roomId }: { roomId: string }) {
  const [events, setEvents] = useState<RoomEvent[]>([]);
  const [now, setNow] = useState(Date.now());
  const fetchEvents = useServerFn(listRoomEvents);

  // Poll clock for relative labels (matches useNow ergonomics)
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 15_000);
    return () => clearInterval(t);
  }, []);

  // Initial paint
  useEffect(() => {
    let mounted = true;
    fetchEvents({ data: { roomId, limit: 30 } })
      .then((rows) => { if (mounted) setEvents(rows); })
      .catch(() => {});
    return () => { mounted = false; };
  }, [roomId, fetchEvents]);

  // Realtime subscription
  useEffect(() => {
    const channel = supabase
      .channel(`room-events:${roomId}`)
      .on(
        "postgres_changes",
        { event: "INSERT", schema: "public", table: "room_events", filter: `room_id=eq.${roomId}` },
        (payload) => {
          const ev = payload.new as RoomEvent;
          setEvents((prev) => [ev, ...prev].slice(0, 30));
        },
      )
      .subscribe();
    return () => { supabase.removeChannel(channel); };
  }, [roomId]);

  if (events.length === 0) {
    return (
      <div className="border border-white/5 rounded-lg p-4">
        <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground mb-3">
          LIVE_FEED
        </div>
        <p className="text-xs text-muted-foreground">The room is quiet. Activity will appear here in real time.</p>
      </div>
    );
  }

  return (
    <div className="border border-white/5 rounded-lg p-4" aria-live="polite">
      <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground mb-3 flex items-center gap-2">
        <span className="size-1.5 rounded-full bg-ember animate-pulse" />
        LIVE_FEED
      </div>
      <ul className="space-y-2 max-h-64 overflow-y-auto pr-1">
        {events.map((e) => {
          void now; // ensure re-render on tick
          return (
            <li key={e.id} className="flex items-start gap-3 text-xs font-mono">
              <span className={`w-4 text-center shrink-0 ${e.kind === "breach" ? "text-breach" : e.kind === "goal_hit" || e.kind === "completed" ? "text-ember" : "text-muted-foreground"}`}>
                {KIND_GLYPH[e.kind] ?? "·"}
              </span>
              <span className="flex-1 text-silver-dim">
                <span className="text-silver">{e.actor_name ?? "Someone"}</span>{" "}
                {KIND_LABEL[e.kind] ?? e.kind}
                {e.kind === "pinned" && typeof e.payload?.message === "string" && (
                  <span className="block text-[10px] text-muted-foreground mt-0.5 italic">
                    &ldquo;{e.payload.message}&rdquo;
                  </span>
                )}
              </span>
              <span className="text-[10px] text-muted-foreground shrink-0">{timeAgo(e.created_at)}</span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
