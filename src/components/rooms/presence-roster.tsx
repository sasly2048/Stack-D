import { useEffect, useMemo, useRef, useState } from "react";
import { supabase } from "@/integrations/supabase/client";
import { haptic } from "@/lib/haptics";

export type PresenceState = "idle" | "ready" | "stacking" | "broke" | "disconnected";

export interface PresenceParticipant {
  id: string;
  user_id: string;
  display_name: string;
  breached: boolean;
  last_heartbeat?: string | null;
  left_at?: string | null;
}

const DISCONNECT_MS = 45_000;

const STATE_META: Record<PresenceState, { label: string; dot: string; text: string }> = {
  idle: { label: "Waiting", dot: "bg-white/25", text: "text-silver-dim" },
  ready: { label: "Ready", dot: "bg-ember", text: "text-ember" },
  stacking: { label: "Stacking", dot: "bg-ember animate-pulse", text: "text-silver" },
  broke: { label: "Broke stack", dot: "bg-breach", text: "text-breach" },
  disconnected: { label: "Disconnected", dot: "bg-amber-400/70", text: "text-amber-400/80" },
};

/**
 * Live presence roster. Distinguishes a dropped connection (no heartbeat while
 * the session runs) from an intentional leave, and resolves to a satisfying
 * "Everyone Ready → Starting…" beat plus a synchronized 3-2-1 countdown.
 */
export function PresenceRoster({
  roomId,
  participants,
  status,
  startedAt,
  myUserId,
  targetSeconds,
}: {
  roomId: string;
  participants: PresenceParticipant[];
  status: "lobby" | "active" | "complete" | "aborted";
  startedAt: string | null;
  myUserId: string | null;
  targetSeconds?: number;
}) {
  const [ready, setReady] = useState<Set<string>>(new Set());
  const [now, setNow] = useState(Date.now());
  const [pending, setPending] = useState(false);
  const allReadyAnnounced = useRef(false);

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);

  // Ready signals ride the existing room_events stream.
  useEffect(() => {
    const channel = supabase
      .channel(`room-ready:${roomId}`)
      .on(
        "postgres_changes",
        { event: "INSERT", schema: "public", table: "room_events", filter: `room_id=eq.${roomId}` },
        (payload) => {
          const ev = payload.new as { kind: string; actor_id: string | null };
          if (!ev.actor_id) return;
          if (ev.kind === "ready") {
            setReady((p) => new Set(p).add(ev.actor_id!));
          } else if (ev.kind === "unready" || ev.kind === "left") {
            setReady((p) => {
              const n = new Set(p);
              n.delete(ev.actor_id!);
              return n;
            });
          }
        },
      )
      .subscribe();
    return () => {
      supabase.removeChannel(channel);
    };
  }, [roomId]);

  // Publish our own heartbeat while the session runs, otherwise every roster
  // row would go stale (and read as "Disconnected") shortly after joining.
  useEffect(() => {
    if (status !== "active" || !myUserId) return;
    let cancelled = false;
    const beat = async () => {
      if (cancelled) return;
      try {
        await supabase
          .from("participants")
          .update({ last_heartbeat: new Date().toISOString() })
          .eq("room_id", roomId)
          .eq("user_id", myUserId);
      } catch {
        /* transient; next tick retries */
      }
    };
    void beat();
    const t = setInterval(beat, 15_000);
    return () => {
      cancelled = true;
      clearInterval(t);
    };
  }, [status, myUserId, roomId]);

  const present = useMemo(() => participants.filter((p) => !p.left_at), [participants]);

  const stateOf = (p: PresenceParticipant): PresenceState => {
    if (p.breached) return "broke";
    const hb = p.last_heartbeat ? new Date(p.last_heartbeat).getTime() : 0;
    if (status === "active" && hb && now - hb > DISCONNECT_MS) return "disconnected";
    if (status === "active") return "stacking";
    return ready.has(p.user_id) ? "ready" : "idle";
  };

  const everyoneReady =
    status === "lobby" && present.length > 0 && present.every((p) => ready.has(p.user_id));

  useEffect(() => {
    if (everyoneReady && !allReadyAnnounced.current) {
      allReadyAnnounced.current = true;
      haptic("success");
    }
    if (!everyoneReady) allReadyAnnounced.current = false;
  }, [everyoneReady]);

  // Synchronized countdown, driven off started_at so every client agrees.
  const countdown = useMemo(() => {
    if (status !== "active" || !startedAt) return null;
    const elapsed = (now - new Date(startedAt).getTime()) / 1000;
    if (elapsed >= 0 || elapsed < -4) return null;
    return Math.ceil(-elapsed);
  }, [status, startedAt, now]);

  const lastTick = useRef<number | null>(null);
  useEffect(() => {
    if (countdown !== null && countdown !== lastTick.current) {
      lastTick.current = countdown;
      haptic("tap");
    }
  }, [countdown]);

  const toggleReady = async () => {
    if (!myUserId || pending) return;
    const isReady = ready.has(myUserId);
    setPending(true);
    // Optimistic — the realtime echo reconciles.
    setReady((p) => {
      const n = new Set(p);
      if (isReady) n.delete(myUserId);
      else n.add(myUserId);
      return n;
    });
    haptic("select");
    try {
      await supabase.rpc("record_room_event", {
        _room_id: roomId,
        _kind: isReady ? "unready" : "ready",
        _payload: {},
      });
    } catch {
      /* the roster stays optimistic; next event reconciles */
    } finally {
      setPending(false);
    }
  };

  return (
    <div className="border border-white/5 rounded-lg p-4" aria-live="polite">
      <div className="flex items-center justify-between mb-3">
        <span className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground">
          Presence
        </span>
        <span className="font-mono text-[10px] tracking-[0.2em] uppercase text-silver-dim">
          {present.length} in room
        </span>
      </div>

      {countdown !== null ? (
        <div className="py-8 text-center">
          <p className="font-serif text-7xl text-ember tabular-nums leading-none animate-scale-in">
            {countdown}
          </p>
          <p className="mt-3 font-mono text-[10px] tracking-[0.35em] uppercase text-silver-dim">
            Hold still
          </p>
        </div>
      ) : everyoneReady ? (
        <div className="py-6 text-center animate-scale-in">
          <p className="font-serif text-3xl text-silver leading-none">Everyone Ready</p>
          <p className="mt-2 font-mono text-[10px] tracking-[0.35em] uppercase text-ember animate-pulse">
            Starting…
          </p>
        </div>
      ) : null}

      <ul className="space-y-2">
        {present.map((p) => {
          const s = stateOf(p);
          const meta = STATE_META[s];
          return (
            <li key={p.id} className="flex items-center gap-3 text-xs font-mono">
              <span className={`size-1.5 rounded-full shrink-0 ${meta.dot}`} aria-hidden />
              <span className="flex-1 truncate text-silver">
                {p.display_name}
                {p.user_id === myUserId && <span className="text-muted-foreground"> · you</span>}
              </span>
              <span className={`text-[10px] tracking-[0.2em] uppercase ${meta.text}`}>
                {meta.label}
              </span>
            </li>
          );
        })}
      </ul>

      {status === "lobby" && myUserId && (
        <button
          type="button"
          onClick={toggleReady}
          disabled={pending}
          aria-pressed={ready.has(myUserId)}
          className={`mt-4 w-full rounded-full border py-2.5 font-mono text-[11px] tracking-[0.3em] uppercase transition-colors disabled:opacity-50 ${
            ready.has(myUserId)
              ? "border-ember text-ember bg-ember/10"
              : "border-white/15 text-silver hover:border-ember hover:text-ember"
          }`}
        >
          {ready.has(myUserId) ? "Ready" : "I'm ready"}
        </button>
      )}

      {targetSeconds ? (
        <p className="mt-3 text-center font-mono text-[9px] tracking-[0.25em] uppercase text-muted-foreground">
          Target · {Math.round(targetSeconds / 60)} min
        </p>
      ) : null}
    </div>
  );
}
