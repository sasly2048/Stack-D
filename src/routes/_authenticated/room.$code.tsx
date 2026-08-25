import { createFileRoute, useNavigate, Link } from "@tanstack/react-router";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";
import { supabase } from "@/integrations/supabase/client";
import { Nav } from "@/components/nav";
import { formatDuration } from "@/lib/room";
import {
  useSensors,
  requestSensorPermissions,
  type BreachReason,
  type BreachSeverity,
  type EnforcementMode,
} from "@/hooks/use-sensors";
import { computeFocusScore, type ScoreResult } from "@/lib/focus-score";
import { enqueueFinalize, flushFinalizeQueue } from "@/lib/finalize-queue";
import { ResultsCard } from "@/components/results-card";
import { SessionRecapCard } from "@/components/session-recap-card";
import { Ripple } from "@/components/fx/ripple";
import { LightRays } from "@/components/fx/light-rays";
import { QRCode } from "@/components/qr-code";
import { AmbientPlayer } from "@/components/ambient-player";
import { SessionMetaForm } from "@/components/session-meta-form";
import { RoomHeader, JoinRequestsPanel } from "@/components/rooms/room-header";
import { LiveActivityRail } from "@/components/rooms/live-activity-rail";
import { PresenceRoster } from "@/components/rooms/presence-roster";
import { UserHoverCard } from "@/components/profile/user-hover-card";
import { SessionWorkspace } from "@/components/session-workspace";
import { setActiveSession } from "@/components/floating-timer";
import { haptic } from "@/lib/haptics";
import { copy } from "@/lib/copy";
import { track } from "@/lib/observability";
import { RoomTimeline } from "@/components/rooms/room-timeline";
import { RoomSchedule } from "@/components/rooms/room-schedule";
import { useLockScreenTimer } from "@/hooks/use-lock-screen-timer";

export const Route = createFileRoute("/_authenticated/room/$code")({
  head: ({ params }) => ({
    meta: [
      { title: `Room ${params.code} — Stack'd` },
      {
        name: "description",
        content: `Shared Stack'd focus room ${params.code}: stack your phones, hold the silence together and earn the time back.`,
      },
      { property: "og:title", content: `Room ${params.code} — Stack'd` },
      {
        property: "og:description",
        content: `Shared Stack'd focus room ${params.code}: stack your phones, hold the silence together and earn the time back.`,
      },
    ],
  }),
  component: Room,
});

interface RoomRow {
  id: string;
  code: string;
  host_id: string;
  status: "lobby" | "active" | "complete" | "aborted";
  target_duration_seconds: number;
  started_at: string | null;
  ended_at: string | null;
  /** Server-set. Orders realtime updates so a replayed stale row is rejected. */
  updated_at?: string;
}

interface ParticipantRow {
  id: string;
  room_id: string;
  user_id: string;
  display_name: string;
  integrity: number;
  breached: boolean;
  breach_reason: string | null;
  breach_at?: string | null;
  joined_at: string;
  last_heartbeat?: string | null;
  left_at?: string | null;
}

interface BreakRow {
  id: string;
  user_id: string;
  display_name: string;
  reason: string;
  severity: "minor" | "severe";
  at: string;
}

function readMode(): EnforcementMode {
  if (typeof localStorage === "undefined") return "absolute";
  const v = localStorage.getItem("stackd:mode");
  return v === "gentle" ? "gentle" : "absolute";
}

function Room() {
  const { code } = Route.useParams();
  const navigate = useNavigate();
  const [me, setMe] = useState<{ id: string; name: string } | null>(null);
  const [room, setRoom] = useState<RoomRow | null>(null);
  const [participants, setParticipants] = useState<ParticipantRow[]>([]);
  const [breaks, setBreaks] = useState<BreakRow[]>([]);
  /** Live realtime health, surfaced in the header — see the subscribe callback. */
  const [realtime, setRealtime] = useState<"connecting" | "live" | "reconnecting" | "offline">(
    "connecting",
  );
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [armed, setArmed] = useState(false);
  const [countdown, setCountdown] = useState<number | null>(null);
  const [now, setNow] = useState(Date.now());
  const [result, setResult] = useState<ScoreResult | null>(null);
  const [historyId, setHistoryId] = useState<string | null>(null);

  const [mode] = useState<EnforcementMode>(() => readMode());

  // Optim 02: prevent duplicate completion / finalization writes.
  const completionLockRef = useRef(false);
  const finalizeLockRef = useRef(false);

  // 1s ticker for live timer
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);

  // Load me + room + participants + breaks
  useEffect(() => {
    let mounted = true;
    (async () => {
      const { data: u } = await supabase.auth.getUser();
      if (!u.user) return;
      const { data: p } = await supabase
        .from("profiles")
        .select("display_name")
        .eq("id", u.user.id)
        .maybeSingle();
      if (!mounted) return;
      const displayName = p?.display_name ?? u.user.email?.split("@")[0] ?? "Anon";
      setMe({ id: u.user.id, name: displayName });

      // rooms SELECT is now scoped to host/participants. Use the
      // security-definer RPC to atomically (a) look the room up by code and
      // (b) insert ourselves as a participant, so the follow-up reads below
      // succeed under the tightened RLS.
      const { data: r, error: rErr } = await (
        supabase.rpc as unknown as (
          fn: string,
          args: Record<string, unknown>,
        ) => PromiseLike<{ data: RoomRow | null; error: { message: string } | null }>
      )("claim_room_seat", { _code: code });
      if (rErr) {
        if (rErr.message?.includes("not_found")) {
          setError(`Room ${code} not found.`);
        } else {
          setError(rErr.message);
        }
        setLoading(false);
        return;
      }
      if (!r) {
        setError(`Room ${code} not found.`);
        setLoading(false);
        return;
      }
      if (!mounted) return;
      setRoom(r);

      const { data: parts } = await supabase
        .from("participants")
        .select("*")
        .eq("room_id", r.id)
        .order("joined_at");
      const { data: brks } = await supabase
        .from("breaks")
        .select("id, user_id, display_name, reason, severity, at")
        .eq("room_id", r.id)
        .order("at", { ascending: false })
        .limit(30);
      if (!mounted) return;
      setParticipants((parts ?? []) as ParticipantRow[]);
      setBreaks((brks ?? []) as BreakRow[]);
      setLoading(false);
    })();
    return () => {
      mounted = false;
    };
  }, [code]);

  // Realtime subscriptions
  useEffect(() => {
    if (!room) return;
    const channel = supabase
      .channel(`room:${room.id}`)
      .on(
        "postgres_changes",
        { event: "*", schema: "public", table: "rooms", filter: `id=eq.${room.id}` },
        (payload) => {
          if (!payload.new) return;
          const next = payload.new as RoomRow;
          setRoom((prev) => {
            // Reject stale updates. On reconnect the channel can replay events,
            // and a late-delivered older row would otherwise overwrite newer
            // state — flipping an active room back to "lobby" and resetting
            // everyone's timer. rooms.updated_at is set by the server, so it
            // orders reliably across clients with skewed clocks.
            if (prev && next.updated_at && prev.updated_at && next.updated_at < prev.updated_at) {
              return prev;
            }
            return next;
          });
        },
      )
      .on(
        "postgres_changes",
        { event: "*", schema: "public", table: "participants", filter: `room_id=eq.${room.id}` },
        (payload) => {
          setParticipants((prev) => {
            if (payload.eventType === "DELETE")
              return prev.filter((p) => p.id !== (payload.old as ParticipantRow).id);
            const next = payload.new as ParticipantRow;
            const i = prev.findIndex((p) => p.id === next.id);
            if (i >= 0) {
              // Replayed INSERT for a row already present, or a genuine UPDATE.
              // Either way replace in place — appending would duplicate the
              // participant in the roster after a reconnect.
              const c = [...prev];
              c[i] = next;
              return c;
            }
            return [...prev, next].sort((a, b) => a.joined_at.localeCompare(b.joined_at));
          });
        },
      )
      .on(
        "postgres_changes",
        { event: "INSERT", schema: "public", table: "breaks", filter: `room_id=eq.${room.id}` },
        (payload) => {
          const b = payload.new as BreakRow;
          let isNew = true;
          setBreaks((prev) => {
            // A reconnect can redeliver INSERTs the client already has. Without
            // this the same breach appears twice in the feed and the count the
            // user sees drifts from the count the server holds.
            if (prev.some((x) => x.id === b.id)) {
              isNew = false;
              return prev;
            }
            // Sort by the server's timestamp rather than arrival order, so a
            // batch replayed out of order still reads chronologically.
            return [b, ...prev].sort((x, y) => y.at.localeCompare(x.at)).slice(0, 30);
          });
          // Only announce a breach once, however many times it is delivered.
          if (isNew && b.user_id !== me?.id && b.severity === "severe") {
            toast.error(`${b.display_name} broke the stack`, {
              description: b.reason.toUpperCase(),
            });
          }
        },
      )
      // The status callback was previously ignored, so a dropped channel meant
      // the room quietly stopped updating: the timer kept counting while other
      // people's breaches never arrived, and nothing said so. In a shared
      // accountability session that is the worst possible silent failure.
      .subscribe((status) => {
        if (status === "SUBSCRIBED") setRealtime("live");
        else if (status === "CHANNEL_ERROR" || status === "TIMED_OUT") setRealtime("reconnecting");
        else if (status === "CLOSED") setRealtime("offline");
      });
    return () => {
      supabase.removeChannel(channel);
    };
  }, [room?.id, me?.id]);

  const isHost = !!(me && room && me.id === room.host_id);
  const myPart = useMemo(() => participants.find((p) => p.user_id === me?.id), [participants, me]);

  const elapsed = room?.started_at
    ? Math.max(0, Math.floor((now - new Date(room.started_at).getTime()) / 1000))
    : 0;
  const remaining = room ? Math.max(0, room.target_duration_seconds - elapsed) : 0;

  useLockScreenTimer(!!room && room.status === "active", room?.code ?? "", remaining);

  // Optim 02: Auto-complete when timer hits zero (host only writes, once).
  useEffect(() => {
    if (!isHost || !room || room.status !== "active") return;
    if (remaining > 0) return;
    if (completionLockRef.current) return;
    completionLockRef.current = true;
    (async () => {
      const { error } = await supabase
        .from("rooms")
        .update({ status: "complete", ended_at: new Date().toISOString() })
        .eq("id", room.id)
        .eq("status", "active");
      if (error) completionLockRef.current = false;
    })();
  }, [isHost, room, remaining]);

  // Optim 01 + 03: stable callback wrapped in ref via useSensors; atomic RPC.
  const handleBreach = useCallback(
    async (reason: BreachReason, severity: BreachSeverity) => {
      if (!room || !me || !myPart) return;
      if (severity === "severe" && myPart.breached) return;
      const integrity = Math.max(
        0,
        Math.round((elapsed / (room.target_duration_seconds || 1)) * 100),
      );
      const { error } = await supabase.rpc("record_breach", {
        _room_id: room.id,
        _participant_id: myPart.id,
        _reason: reason,
        _severity: severity,
        _integrity: integrity,
      });
      if (error) {
        toast.error("Breach not recorded — retrying", { description: error.message });
        return;
      }
      if (severity === "severe") {
        setArmed(false);
        toast.error(copy.session.breachSevere(reason));
      } else {
        toast(copy.session.breachMinor(reason));
      }
      track("session.breached", { room_id: room.id, reason, severity });
    },
    [room, me, myPart, elapsed],
  );

  useSensors({
    enabled: armed && room?.status === "active" && !myPart?.breached,
    mode,
    onBreach: handleBreach,
  });

  // Finalize this participant's session into focus_history exactly once.
  // Brief §3: catch network failures and park the payload in localStorage so a
  // later page load can retry; the RPC is one-row-per-(profile,room) safe.
  useEffect(() => {
    if (!room || !me || !myPart) return;
    if (room.status !== "complete" && room.status !== "aborted") return;
    if (finalizeLockRef.current) return;
    finalizeLockRef.current = true;

    const myBreaks = breaks
      .filter((b) => b.user_id === me.id)
      .map((b) => ({ severity: b.severity }));

    // Keep ms-precision through the math — only floor at the DB boundary.
    const startedAtMs = room.started_at ? new Date(room.started_at).getTime() : 0;
    const endedAtMs = room.ended_at ? new Date(room.ended_at).getTime() : Date.now();
    const totalElapsedMs = startedAtMs ? Math.max(0, endedAtMs - startedAtMs) : 0;

    let focusMs = totalElapsedMs;
    let abandonmentMs = 0;
    if (myPart.breached && myPart.breach_at && startedAtMs) {
      const breachAtMs = new Date(myPart.breach_at).getTime();
      focusMs = Math.max(0, breachAtMs - startedAtMs);
      abandonmentMs = Math.max(0, endedAtMs - breachAtMs);
    }

    const res = computeFocusScore({
      targetSeconds: room.target_duration_seconds,
      focusSeconds: focusMs / 1000,
      breaches: myBreaks,
      abandonmentSeconds: abandonmentMs / 1000,
    });
    setResult(res);

    const payload = {
      _room_id: room.id,
      _score: res.score,
      _xp: res.xp,
      _duration_seconds: res.focusSecondsInt,
      _breaches_count: myBreaks.length,
      _tier: res.tier.key,
      // Recorded so a historical score stays interpretable if the formula
      // changes — without it, old and new rows silently mean different things.
      _scoring_version: res.scoringVersion,
      // Server recomputes score/duration/xp authoritatively and only uses this
      // (clamped) to apply the abandonment penalty — it can lower but never
      // raise the score. The client _score/_xp/_duration above are display-only.
      _abandonment_seconds: Math.max(0, Math.round(abandonmentMs / 1000)),
      _owner: me.id,
      _queued_at: Date.now(),
    };

    (async () => {
      try {
        const { data: hid, error } = await supabase.rpc("finalize_focus_session", {
          _room_id: payload._room_id,
          _score: payload._score,
          _xp: payload._xp,
          _duration_seconds: payload._duration_seconds,
          _breaches_count: payload._breaches_count,
          _tier: payload._tier,
          _scoring_version: payload._scoring_version,
          _abandonment_seconds: payload._abandonment_seconds,
        });
        if (!error && typeof hid === "string") {
          setHistoryId(hid);
          track("session.completed", {
            room_id: payload._room_id,
            xp: payload._xp,
            score: payload._score,
            duration_seconds: payload._duration_seconds,
            tier: payload._tier,
          });
          if (typeof window !== "undefined") {
            window.dispatchEvent(
              new CustomEvent("stackd:ceremony", {
                detail: {
                  durationSeconds: payload._duration_seconds,
                  xpEarned: payload._xp,
                  score: payload._score,
                  tier: payload._tier,
                  historyId: hid,
                },
              }),
            );
          }
        }
        if (error) {
          enqueueFinalize(payload);

          finalizeLockRef.current = false;
          console.warn("finalize_queued_for_retry", error.message);
          toast(copy.session.queuedRetry);
        }
      } catch (e) {
        // Offline / network down — park locally and retry later.
        enqueueFinalize(payload);
        finalizeLockRef.current = false;
        console.warn("finalize_offline_queued", e);
        toast(copy.session.queuedOffline);
      }
    })();
  }, [room?.status, me?.id, myPart?.id, myPart?.breached, breaks.length]);

  // Drain any queued finalize records once we know who's signed in.
  useEffect(() => {
    if (!me?.id) return;
    flushFinalizeQueue(me.id).catch(() => {});
  }, [me?.id]);

  // Observability: fire once when this room transitions into an active session.
  const startedTrackedRef = useRef(false);
  useEffect(() => {
    if (!room || !myPart) return;
    if (room.status === "active" && !startedTrackedRef.current) {
      startedTrackedRef.current = true;
      track("session.started", {
        room_id: room.id,
        target_seconds: room.target_duration_seconds,
      });
    }
  }, [room?.status, room?.id, myPart?.id]);

  // Brief §2 (hybrid): reconcile elapsed time with the server on wake — if the
  // OS froze the tab/app, our local clock and realtime stream may have drifted.
  useEffect(() => {
    if (!room) return;
    const refetch = async () => {
      const { data: fresh } = await supabase
        .from("rooms")
        .select("id, code, host_id, status, target_duration_seconds, started_at, ended_at")
        .eq("id", room.id)
        .maybeSingle();
      if (fresh) setRoom(fresh as RoomRow);
      setNow(Date.now());
    };
    const onVis = () => {
      if (document.visibilityState === "visible") refetch();
    };
    const onShow = (e: PageTransitionEvent) => {
      if (e.persisted) refetch();
    };
    document.addEventListener("visibilitychange", onVis);
    window.addEventListener("pageshow", onShow);
    return () => {
      document.removeEventListener("visibilitychange", onVis);
      window.removeEventListener("pageshow", onShow);
    };
  }, [room?.id]);

  // Brief §1 (theme): while inside an active OLED focus run, lock theme so the
  // toggle can't yank us back to a non-black background mid-ritual.
  useEffect(() => {
    if (!room) return;
    const oled = room.status === "active" && !myPart?.breached;
    if (oled) {
      document.documentElement.dataset.themeLock = "1";
      return () => {
        delete document.documentElement.dataset.themeLock;
      };
    }
  }, [room?.status, myPart?.breached]);

  // Sync the global floating-timer pill with this room's active window.
  useEffect(() => {
    if (!room) return;
    if (room.status === "active" && room.started_at) {
      const endsAt = new Date(room.started_at).getTime() + room.target_duration_seconds * 1000;
      setActiveSession({ code: room.code, endsAt });
    } else if (room.status === "complete" || room.status === "aborted") {
      setActiveSession(null);
    }
    return () => {
      // Clear on unmount so navigating away from a lobby never leaves a stale pill.
      if (room.status !== "active") setActiveSession(null);
    };
  }, [room?.status, room?.started_at, room?.target_duration_seconds, room?.code]);

  const startRitual = async () => {
    if (!room || !isHost) return;
    await requestSensorPermissions();
    setCountdown(3);
  };

  useEffect(() => {
    if (countdown === null) return;
    if (countdown <= 0) {
      setCountdown(null);
      if (room && isHost && room.status === "lobby") {
        // The server sets started_at from its own clock. This used to write
        // `new Date().toISOString()` from the host's browser — and since every
        // score derives from that timestamp, the most important input to the
        // reward system came from an unverified client clock. The RPC is also
        // host-checked and idempotent, so a double-tap cannot restart a
        // running session.
        supabase.rpc("start_focus_session", { _room_id: room.id }).then(({ error }) => {
          if (error) toast.error("Couldn't start the session. Try again.");
        });
      }
      setArmed(true);
      try {
        navigator.vibrate?.(300);
      } catch {
        /* noop */
      }
      return;
    }
    try {
      navigator.vibrate?.(100);
    } catch {
      /* noop */
    }
    const t = setTimeout(() => setCountdown((c) => (c ?? 1) - 1), 1000);
    return () => clearTimeout(t);
  }, [countdown, isHost, room?.id, room?.status]);

  useEffect(() => {
    if (!room || isHost) return;
    if (room.status === "active" && !armed && !myPart?.breached) {
      requestSensorPermissions().then(() => setArmed(true));
    }
    if (room.status !== "active" && armed) setArmed(false);
  }, [room?.status, isHost, armed, myPart?.breached]);

  const endRitual = async () => {
    if (!room || !isHost) return;
    if (completionLockRef.current) return;
    completionLockRef.current = true;
    const { error } = await supabase
      .from("rooms")
      .update({ status: "complete", ended_at: new Date().toISOString() })
      .eq("id", room.id)
      .eq("status", "active");
    if (error) completionLockRef.current = false;
  };

  const abortRitual = async () => {
    if (!room || !isHost) return;
    await supabase
      .from("rooms")
      .update({ status: "aborted", ended_at: new Date().toISOString() })
      .eq("id", room.id);
  };

  const leaveRoom = async () => {
    if (!room || !me) return;
    await supabase.from("participants").delete().eq("room_id", room.id).eq("user_id", me.id);
    navigate({ to: "/dashboard" });
  };

  // Inline confirmation as well as the toast: on the room screen the user's
  // attention is on the code itself, and a toast in the corner is easy to miss
  // — leaving them unsure whether the copy actually happened.
  const [copied, setCopied] = useState(false);

  const copyCode = async () => {
    const link = window.location.origin + `/room/${room?.code}`;
    try {
      // Optional chaining used to swallow the absence of the clipboard API
      // entirely, so an insecure context reported "copied" having copied
      // nothing. Fall back to a selectable prompt instead of lying.
      if (!navigator.clipboard) throw new Error("clipboard unavailable");
      await navigator.clipboard.writeText(link);
      setCopied(true);
      toast.success("Invite link copied");
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      toast.error("Couldn't copy automatically", { description: link });
    }
  };

  if (loading) {
    return (
      <Shell>
        <div className="font-mono text-xs text-muted-foreground uppercase tracking-widest">
          Loading session…
        </div>
      </Shell>
    );
  }
  if (error || !room) {
    return (
      <Shell>
        <div className="text-center">
          <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-breach mb-4">
            ROOM_NOT_FOUND
          </div>
          <h1 className="text-3xl font-bold mb-4">No protocol with key {code}.</h1>
          <Link
            to="/start"
            className="bg-silver text-obsidian px-6 py-3 rounded-lg font-mono text-xs uppercase tracking-widest font-bold hover:invert transition-all"
          >
            Forge a new key
          </Link>
        </div>
      </Shell>
    );
  }

  if (countdown !== null) {
    return (
      <div className="fixed inset-0 z-[60] bg-black flex flex-col items-center justify-center">
        <div className="font-mono text-[10px] tracking-[0.4em] uppercase text-muted-foreground mb-8 animate-breathing">
          Prepare the offering
        </div>
        <div className="text-[12rem] font-extrabold tracking-tighter leading-none animate-breathing">
          {countdown === 0 ? "STACK" : countdown}
        </div>
      </div>
    );
  }

  const complete = room.status === "complete";
  const aborted = room.status === "aborted";
  const lobby = room.status === "lobby";
  const active = room.status === "active";
  const totalBreached = participants.filter((p) => p.breached).length;
  const tableIntegrity = participants.length
    ? Math.round(
        participants.reduce(
          (a, p) =>
            a +
            (p.breached
              ? p.integrity
              : Math.min(100, Math.round((elapsed / (room.target_duration_seconds || 1)) * 100))),
          0,
        ) / participants.length,
      )
    : 0;

  // OLED-black during active focus to preserve battery on mobile.
  const oledMode = active && !myPart?.breached;

  return (
    <div
      className={`min-h-screen ${oledMode ? "bg-black" : "bg-obsidian"} text-silver transition-colors`}
    >
      <Nav />
      <main className="pt-24 pb-20 px-6 max-w-2xl mx-auto">
        <div className="mb-10 flex justify-between items-center font-mono text-[10px] tracking-tighter text-muted-foreground">
          <button
            type="button"
            onClick={copyCode}
            aria-label={`Copy invite link for room ${room.code}`}
            className="cursor-pointer rounded transition-all duration-200 ease-[var(--ease-ritual)] hover:text-silver active:scale-[0.99] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember focus-visible:ring-offset-2 focus-visible:ring-offset-obsidian"
          >
            ROOM: {room.code} {copied ? "✓" : "⧉"}
            {/* Announced separately so the confirmation reaches a screen reader,
                which cannot infer it from a glyph swap. */}
            <span role="status" aria-live="polite" className="sr-only">
              {copied ? "Invite link copied" : ""}
            </span>
          </button>
          {copied && (
            <span
              aria-hidden="true"
              className="font-mono text-[10px] uppercase tracking-widest text-pulse"
            >
              Copied
            </span>
          )}
          <span className="flex items-center gap-3">
            {/* Connection health sits next to the session status because the two
                are read together: "LIVE SESSION" while the channel is down
                means the screen is lying about being live. */}
            {realtime !== "live" && (
              <span
                role="status"
                aria-live="polite"
                className={`flex items-center gap-1.5 uppercase tracking-widest ${
                  realtime === "offline" ? "text-breach" : "text-ember"
                }`}
              >
                <span
                  aria-hidden="true"
                  className={`size-1.5 rounded-full ${
                    realtime === "offline" ? "bg-breach" : "bg-ember animate-pulse"
                  }`}
                />
                {realtime === "connecting"
                  ? "Connecting"
                  : realtime === "reconnecting"
                    ? "Reconnecting"
                    : "Disconnected"}
              </span>
            )}
            <span className="uppercase tracking-widest">
              {mode === "gentle" ? "Gentle" : "Absolute"}
            </span>
            <span
              className={`size-1.5 rounded-full ${active ? "bg-pulse animate-pulse" : complete ? "bg-pulse" : aborted ? "bg-breach" : "bg-muted-foreground"}`}
            />
            {active ? "LIVE SESSION" : complete ? "COMPLETE" : aborted ? "ABORTED" : "LOBBY"}
          </span>
        </div>

        <div className="relative aspect-square flex items-center justify-center mb-12">
          {lobby && <Ripple className="opacity-70" rings={5} />}
          {active && !myPart?.breached && <LightRays className="opacity-80" />}
          <div className="absolute inset-0 border border-white/5 rounded-full" />
          <div className="absolute inset-4 border border-white/10 rounded-full" />
          <div
            className={`absolute inset-8 border border-white/5 rounded-full ${active && !myPart?.breached ? "animate-breathing" : ""}`}
          />

          <div className="text-center px-6">
            {lobby && (
              <>
                <div className="text-[10px] font-mono tracking-[0.4em] text-muted-foreground uppercase mb-3">
                  Target
                </div>
                <div className="text-6xl sm:text-7xl font-mono tracking-tighter mb-3">
                  {formatDuration(room.target_duration_seconds)}
                </div>
                <div className="text-[10px] font-mono tracking-[0.4em] text-muted-foreground uppercase">
                  Waiting for {isHost ? "you" : "host"}
                </div>
              </>
            )}
            {active && (
              <>
                <div
                  className={`text-7xl sm:text-8xl font-mono tracking-tighter mb-3 ${myPart?.breached ? "text-breach" : ""}`}
                >
                  {formatDuration(remaining)}
                </div>
                <div className="text-[10px] font-mono tracking-[0.4em] text-muted-foreground uppercase">
                  {myPart?.breached ? "You breached. Don't move others." : "Hold the silence"}
                </div>
              </>
            )}
            {complete && (
              <>
                <div className="text-7xl sm:text-8xl font-mono tracking-tighter mb-3 text-pulse">
                  {formatDuration(elapsed)}
                </div>
                <div className="text-[10px] font-mono tracking-[0.4em] text-pulse uppercase">
                  Protocol Complete · {tableIntegrity}% integrity
                </div>
              </>
            )}
            {aborted && (
              <>
                <div className="text-6xl font-mono tracking-tighter mb-3 text-breach">ABORT</div>
                <div className="text-[10px] font-mono tracking-[0.4em] text-muted-foreground uppercase">
                  Session terminated by host
                </div>
              </>
            )}
          </div>
        </div>

        {lobby && (
          <>
            <RoomHeader roomId={room.id} isHost={isHost} />
            <JoinRequestsPanel roomId={room.id} isModerator={isHost} />
            <div className="mb-10 glass rounded-md p-5 flex items-center gap-5">
              <QRCode
                text={`${typeof window !== "undefined" ? window.location.origin : ""}/room/${room.code}`}
                size={128}
              />
              <div>
                <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">
                  Invite
                </p>
                <p className="mt-2 text-sm text-silver-dim">Scan to join room</p>
                <p className="mt-1 font-mono text-2xl tracking-[0.3em] text-silver">{room.code}</p>
              </div>
            </div>
            <div className="mb-10 grid md:grid-cols-2 gap-4">
              <RoomTimeline roomId={room.id} />
              <RoomSchedule roomId={room.id} canManage={isHost} />
            </div>
            <div className="mb-10">
              <SessionWorkspace roomId={room.id} />
            </div>
          </>
        )}

        {active && !myPart?.breached && (
          <div className="mb-10">
            <AmbientPlayer />
          </div>
        )}

        {result && (complete || aborted) && (
          <div className="mb-10 space-y-6">
            <ResultsCard
              score={result.score}
              xp={result.xp}
              tier={result.tier}
              durationSeconds={elapsed}
              breachesCount={breaks.filter((b) => b.user_id === me?.id).length}
            />
            <SessionRecapCard
              roomId={room.id}
              roomCode={room.code}
              score={result.score}
              xp={result.xp}
              durationSeconds={result.focusSecondsInt}
              breachesCount={breaks.filter((b) => b.user_id === me?.id).length}
              tier={result.tier.key}
              displayName={me?.name}
            />
            {historyId && <SessionMetaForm historyId={historyId} />}
          </div>
        )}

        <div className="mb-10 flex gap-3">
          {lobby && isHost && (
            <button
              onClick={startRitual}
              disabled={participants.length === 0}
              className="flex-1 bg-silver text-obsidian py-4 rounded-lg font-mono text-xs uppercase tracking-widest font-bold hover:invert transition-all disabled:opacity-40"
            >
              Begin Session ({participants.length} present)
            </button>
          )}
          {lobby && !isHost && (
            <div className="flex-1 border border-silver/20 py-4 rounded-lg font-mono text-xs uppercase tracking-widest text-center text-muted-foreground">
              Waiting for host…
            </div>
          )}
          {active && isHost && (
            <>
              <button
                onClick={endRitual}
                className="flex-1 border border-silver/40 py-3 rounded-lg font-mono text-xs uppercase tracking-widest hover:bg-silver hover:text-obsidian transition-all"
              >
                End Now
              </button>
              <button
                onClick={abortRitual}
                className="px-5 border border-breach/40 text-breach py-3 rounded-lg font-mono text-xs uppercase tracking-widest hover:bg-breach hover:text-obsidian transition-all"
              >
                Abort
              </button>
            </>
          )}
          {(complete || aborted) && (
            <Link
              to="/dashboard"
              className="flex-1 bg-silver text-obsidian py-4 rounded-lg text-center font-mono text-xs uppercase tracking-widest font-bold hover:invert transition-all"
            >
              Return to Analytics
            </Link>
          )}
          {lobby && !isHost && (
            <button
              onClick={leaveRoom}
              className="px-5 border border-breach/40 text-breach py-3 rounded-lg font-mono text-xs uppercase tracking-widest hover:bg-breach hover:text-obsidian transition-all"
            >
              Leave
            </button>
          )}
        </div>

        <div className="space-y-3 mb-8">
          <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground mb-4">
            PARTICIPANTS · {participants.length} · {totalBreached} BREACHED
          </div>
          {participants.map((p, idx) => {
            const isMe = p.user_id === me?.id;
            const livePct = p.breached
              ? p.integrity
              : active
                ? Math.min(100, Math.round((elapsed / (room.target_duration_seconds || 1)) * 100))
                : lobby
                  ? 0
                  : 100;
            return (
              <div
                key={p.id}
                className={`flex items-center justify-between p-4 rounded-lg border ${p.breached ? "border-breach/30 bg-breach/5" : "border-white/5 bg-white/5"}`}
              >
                <div className="flex items-center gap-4 min-w-0">
                  <span
                    className={`font-mono text-xs ${p.breached ? "text-breach" : "text-muted-foreground"}`}
                  >
                    {String(idx + 1).padStart(2, "0")}
                  </span>
                  <UserHoverCard profileId={p.user_id}>
                    <button
                      className={`text-sm font-medium uppercase truncate text-left hover:text-ember transition-colors ${p.breached ? "text-breach" : ""}`}
                    >
                      {p.display_name}
                      {isMe ? " · YOU" : ""}
                      {p.user_id === room.host_id ? " · HOST" : ""}
                    </button>
                  </UserHoverCard>
                </div>
                {p.breached ? (
                  <span className="font-mono text-[10px] text-breach uppercase tracking-widest whitespace-nowrap">
                    {(p.breach_reason ?? "BREACH").toUpperCase()}
                  </span>
                ) : (
                  <div className="flex items-center gap-3">
                    <div className="w-20 sm:w-24 h-1 bg-white/10 rounded-full overflow-hidden">
                      <div
                        className="h-full bg-silver transition-all"
                        style={{ width: `${livePct}%` }}
                      />
                    </div>
                    <span className="font-mono text-[10px] text-silver w-8 text-right">
                      {livePct}%
                    </span>
                  </div>
                )}
              </div>
            );
          })}
        </div>

        <div className="mb-8 grid gap-4 lg:grid-cols-2">
          <PresenceRoster
            roomId={room.id}
            participants={participants}
            status={room.status}
            startedAt={room.started_at}
            myUserId={me?.id ?? null}
            targetSeconds={room.target_duration_seconds}
          />
          <LiveActivityRail roomId={room.id} />
        </div>

        {breaks.length > 0 && (
          <div>
            <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground mb-4">
              BREACH_LOG
            </div>
            <div className="space-y-2">
              {breaks.slice(0, 8).map((b) => (
                <div
                  key={b.id}
                  className="flex justify-between items-center text-xs font-mono text-muted-foreground py-2 border-b border-white/5"
                >
                  <span className={b.severity === "severe" ? "text-breach" : "text-silver"}>
                    {b.display_name}
                  </span>
                  <span className="uppercase tracking-widest">
                    {b.severity === "minor" ? "minor · " : ""}
                    {b.reason}
                  </span>
                  <span className="text-[10px]">{new Date(b.at).toLocaleTimeString()}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </main>
    </div>
  );
}

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-obsidian text-silver">
      <Nav />
      <main className="pt-32 pb-20 px-6 max-w-2xl mx-auto">{children}</main>
    </div>
  );
}
