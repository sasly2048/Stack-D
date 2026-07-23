import { useEffect, useState } from "react";
import { Link, useLocation } from "@tanstack/react-router";

/**
 * Persistent mini-timer for an active room session.
 * Room writes { code, endsAt } into localStorage["stackd:activeSession"]
 * when the ritual begins and clears it on complete/abort/leave.
 * The pill renders on every route EXCEPT the room itself.
 */

type ActiveSession = { code: string; endsAt: number };
const KEY = "stackd:activeSession";

function read(): ActiveSession | null {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return null;
    const p = JSON.parse(raw) as ActiveSession;
    if (!p?.code || typeof p.endsAt !== "number") return null;
    if (p.endsAt < Date.now() - 60_000) {
      localStorage.removeItem(KEY);
      return null;
    }
    return p;
  } catch {
    return null;
  }
}

function fmt(ms: number) {
  const s = Math.max(0, Math.floor(ms / 1000));
  const m = Math.floor(s / 60);
  const r = s % 60;
  return `${String(m).padStart(2, "0")}:${String(r).padStart(2, "0")}`;
}

export function FloatingTimer() {
  const [session, setSession] = useState<ActiveSession | null>(null);
  const [now, setNow] = useState(Date.now());
  const { pathname } = useLocation();

  useEffect(() => {
    setSession(read());
    const onStorage = (e: StorageEvent) => {
      if (e.key === KEY) setSession(read());
    };
    const onCustom = () => setSession(read());
    window.addEventListener("storage", onStorage);
    window.addEventListener("stackd:session-change", onCustom);
    return () => {
      window.removeEventListener("storage", onStorage);
      window.removeEventListener("stackd:session-change", onCustom);
    };
  }, []);

  useEffect(() => {
    if (!session) return;
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, [session]);

  if (!session) return null;
  // Hide inside the room the session belongs to — no double timer.
  if (pathname === `/room/${session.code}`) return null;

  const remaining = session.endsAt - now;
  const done = remaining <= 0;

  return (
    <Link
      to="/room/$code"
      params={{ code: session.code }}
      className="fixed bottom-4 right-4 z-50 flex items-center gap-3 rounded-full glass px-4 py-2.5 font-mono text-xs text-silver shadow-[0_10px_30px_-10px_rgba(0,0,0,0.6)] hover:border-ember/40 transition-colors safe-bottom"
      aria-label={`Return to active session ${session.code}`}
    >
      <span
        className={`size-2 rounded-full ${done ? "bg-pulse" : "bg-ember animate-pulse"}`}
        aria-hidden
      />
      <span className="tracking-[0.2em] uppercase text-[10px] text-muted-foreground">
        {done ? "READY" : "LIVE"}
      </span>
      <span className="tabular-nums text-silver">{fmt(remaining)}</span>
      <span className="text-muted-foreground text-[10px] tracking-widest">
        · {session.code}
      </span>
    </Link>
  );
}

/** Called by the room when a ritual starts / ends. */
export function setActiveSession(next: ActiveSession | null) {
  try {
    if (next) localStorage.setItem(KEY, JSON.stringify(next));
    else localStorage.removeItem(KEY);
    window.dispatchEvent(new Event("stackd:session-change"));
  } catch {
    /* noop */
  }
}
