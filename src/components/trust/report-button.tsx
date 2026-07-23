import { useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { toast } from "sonner";
import { blockUser, fileReport } from "@/lib/trust.functions";

export function ReportButton({ targetUserId, targetRoomId }: { targetUserId?: string; targetRoomId?: string }) {
  const [open, setOpen] = useState(false);
  const [kind, setKind] = useState("harassment");
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const report = useServerFn(fileReport);
  const block = useServerFn(blockUser);

  const submit = async () => {
    setBusy(true);
    try {
      await report({ data: { targetUserId, targetRoomId, kind, reason } });
      toast.success("Report filed. Thank you.");
      setOpen(false);
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "Failed");
    } finally {
      setBusy(false);
    }
  };

  const doBlock = async () => {
    if (!targetUserId) return;
    try {
      await block({ data: { userId: targetUserId } });
      toast.success("Blocked");
      setOpen(false);
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "Failed");
    }
  };

  if (!open) {
    return (
      <button
        onClick={() => setOpen(true)}
        className="text-[10px] font-mono uppercase tracking-widest text-silver-dim hover:text-breach"
      >
        Report
      </button>
    );
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" onClick={() => setOpen(false)}>
      <div className="bg-obsidian border border-white/10 rounded-md p-6 max-w-md w-full" onClick={(e) => e.stopPropagation()}>
        <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-breach">Report</p>
        <h3 className="mt-2 text-xl font-serif text-silver">Tell us what happened</h3>
        <select
          value={kind}
          onChange={(e) => setKind(e.target.value)}
          className="mt-4 w-full bg-black/50 border border-white/10 rounded px-3 py-2 text-sm text-silver"
        >
          <option value="harassment">Harassment</option>
          <option value="spam">Spam</option>
          <option value="impersonation">Impersonation</option>
          <option value="inappropriate">Inappropriate content</option>
          <option value="other">Other</option>
        </select>
        <textarea
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="Optional detail…"
          rows={3}
          className="mt-2 w-full bg-black/50 border border-white/10 rounded px-3 py-2 text-sm text-silver"
        />
        <div className="mt-4 flex gap-2 justify-between">
          {targetUserId && (
            <button
              onClick={doBlock}
              className="px-4 py-2 rounded border border-white/20 text-silver-dim font-mono text-[10px] uppercase tracking-widest hover:text-breach hover:border-breach/50"
            >
              Block user
            </button>
          )}
          <div className="flex gap-2 ml-auto">
            <button
              onClick={() => setOpen(false)}
              className="px-4 py-2 rounded border border-white/20 text-silver-dim font-mono text-[10px] uppercase tracking-widest"
            >
              Cancel
            </button>
            <button
              onClick={submit}
              disabled={busy}
              className="px-4 py-2 rounded bg-breach text-obsidian font-mono text-[10px] uppercase tracking-widest disabled:opacity-50"
            >
              {busy ? "Filing…" : "File report"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
