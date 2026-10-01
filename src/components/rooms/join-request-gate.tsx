import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { requestToJoinRoom } from "@/lib/rooms2.functions";

type GateStatus = "idle" | "sending" | "pending" | "denied" | "error";

/**
 * Shown when claim_room_seat says needs_approval: file a join request, then
 * wait for the host. Approval makes claim_room_seat succeed, so onApproved
 * just re-runs the room load.
 */
export function JoinRequestGate({ code, onApproved }: { code: string; onApproved: () => void }) {
  const request = useServerFn(requestToJoinRoom);
  const [status, setStatus] = useState<GateStatus>("idle");
  const [message, setMessage] = useState("");

  const apply = (s: "open" | "pending" | "approved" | "denied") => {
    if (s === "approved" || s === "open") onApproved();
    else setStatus(s);
  };

  const send = async () => {
    setStatus("sending");
    try {
      const r = await request({ data: { code, message: message.trim() || undefined } });
      apply(r.status);
    } catch {
      setStatus("error");
    }
  };

  // ponytail: 10s poll of the same idempotent RPC; switch to a realtime
  // subscription on the caller's request row if hosts expect instant entry.
  useEffect(() => {
    if (status !== "pending") return;
    const t = setInterval(async () => {
      try {
        apply((await request({ data: { code } })).status);
      } catch {
        /* keep waiting */
      }
    }, 10_000);
    return () => clearInterval(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, code]);

  return (
    <div className="text-center max-w-md mx-auto">
      <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground mb-4">
        APPROVAL_REQUIRED
      </div>
      <h1 className="text-3xl font-bold mb-3">Room {code} is invite-by-request.</h1>
      {status === "pending" ? (
        <p className="text-muted-foreground">
          Request sent. You'll enter automatically once the host approves.
        </p>
      ) : status === "denied" ? (
        <p className="text-muted-foreground">The host declined this request.</p>
      ) : (
        <>
          <p className="text-muted-foreground mb-5">Ask the host to let you in.</p>
          <textarea
            value={message}
            onChange={(e) => setMessage(e.target.value.slice(0, 280))}
            placeholder="Optional note for the host"
            rows={2}
            className="w-full rounded-lg bg-white/5 border border-white/10 p-3 text-sm mb-4"
          />
          <button
            type="button"
            onClick={send}
            disabled={status === "sending"}
            className="bg-silver text-obsidian px-6 py-3 rounded-lg font-mono text-xs uppercase tracking-widest font-bold hover:invert transition-all disabled:opacity-50"
          >
            {status === "sending" ? "Sending…" : "Request to join"}
          </button>
          {status === "error" && (
            <p className="text-breach text-sm mt-3">Couldn't send the request. Try again.</p>
          )}
        </>
      )}
    </div>
  );
}
