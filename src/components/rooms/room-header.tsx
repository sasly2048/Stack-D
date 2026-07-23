import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { toast } from "sonner";
import {
  getRoomMeta,
  updateRoomMeta,
  listRoomModerators,
  listJoinRequests,
  respondToJoinRequest,
  getRoomStats,
  type RoomMeta,
  type RoomModerator,
  type JoinRequest,
  type RoomStats,
} from "@/lib/rooms2.functions";
import { supabase } from "@/integrations/supabase/client";
import { Button } from "@/components/ui/button";

function fmtHours(sec: number): string {
  const h = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  if (h > 0) return `${h}h ${m}m`;
  return `${m}m`;
}

export function RoomHeader({ roomId, isHost }: { roomId: string; isHost: boolean }) {
  const [meta, setMeta] = useState<RoomMeta | null>(null);
  const [stats, setStats] = useState<RoomStats | null>(null);
  const [mods, setMods] = useState<RoomModerator[]>([]);
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({
    title: "",
    description: "",
    pinned_message: "",
    goalHours: 0,
    visibility: "open" as "open" | "request" | "invite",
  });

  const fetchMeta = useServerFn(getRoomMeta);
  const fetchStats = useServerFn(getRoomStats);
  const fetchMods = useServerFn(listRoomModerators);
  const saveMeta = useServerFn(updateRoomMeta);

  useEffect(() => {
    let mounted = true;
    (async () => {
      // meta is by code, but we can look up code from participants
      const { data: r } = await supabase.from("rooms").select("code").eq("id", roomId).maybeSingle();
      if (!r) return;
      const [m, s, mo] = await Promise.all([
        fetchMeta({ data: { code: r.code as string } }),
        fetchStats({ data: { roomId } }),
        fetchMods({ data: { roomId } }),
      ]);
      if (!mounted) return;
      setMeta(m);
      setStats(s);
      setMods(mo);
      if (m) {
        setForm({
          title: m.title ?? "",
          description: m.description ?? "",
          pinned_message: m.pinned_message ?? "",
          goalHours: m.collective_goal_seconds ? Math.round(m.collective_goal_seconds / 3600) : 0,
          visibility: m.visibility,
        });
      }
    })();
    return () => { mounted = false; };
  }, [roomId, fetchMeta, fetchStats, fetchMods]);

  // Realtime: refresh stats when focus_history/participants change (cheap fallback: room_events)
  useEffect(() => {
    const ch = supabase
      .channel(`room-stats:${roomId}`)
      .on("postgres_changes", { event: "*", schema: "public", table: "room_events", filter: `room_id=eq.${roomId}` },
        async () => {
          const s = await fetchStats({ data: { roomId } });
          setStats(s);
        })
      .subscribe();
    return () => { supabase.removeChannel(ch); };
  }, [roomId, fetchStats]);

  async function handleSave() {
    setSaving(true);
    try {
      await saveMeta({
        data: {
          roomId,
          title: form.title,
          description: form.description,
          pinned_message: form.pinned_message,
          collective_goal_seconds: form.goalHours > 0 ? form.goalHours * 3600 : null,
          visibility: form.visibility,
        },
      });
      toast.success("Room updated");
      setEditing(false);
      // refresh meta
      if (meta) {
        setMeta({
          ...meta,
          title: form.title || null,
          description: form.description || null,
          pinned_message: form.pinned_message || null,
          collective_goal_seconds: form.goalHours > 0 ? form.goalHours * 3600 : null,
          visibility: form.visibility,
        });
      }
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Save failed");
    } finally {
      setSaving(false);
    }
  }

  if (!meta) return null;

  return (
    <div className="mb-10 border border-white/10 rounded-xl p-5 bg-white/[0.02]">
      {editing ? (
        <div className="space-y-3">
          <input
            className="w-full bg-black/40 border border-white/10 rounded px-3 py-2 text-sm"
            placeholder="Room title"
            value={form.title}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
            maxLength={80}
          />
          <textarea
            className="w-full bg-black/40 border border-white/10 rounded px-3 py-2 text-sm h-20 resize-none"
            placeholder="What's this room for?"
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
            maxLength={500}
          />
          <input
            className="w-full bg-black/40 border border-white/10 rounded px-3 py-2 text-sm"
            placeholder="Pinned message"
            value={form.pinned_message}
            onChange={(e) => setForm({ ...form, pinned_message: e.target.value })}
            maxLength={400}
          />
          <div className="grid grid-cols-2 gap-3">
            <label className="text-xs">
              <span className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground">Goal (hrs)</span>
              <input
                type="number" min={0} max={720}
                className="w-full mt-1 bg-black/40 border border-white/10 rounded px-3 py-2 text-sm"
                value={form.goalHours}
                onChange={(e) => setForm({ ...form, goalHours: Number(e.target.value) })}
              />
            </label>
            <label className="text-xs">
              <span className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground">Visibility</span>
              <select
                className="w-full mt-1 bg-black/40 border border-white/10 rounded px-3 py-2 text-sm"
                value={form.visibility}
                onChange={(e) => setForm({ ...form, visibility: e.target.value as "open" | "request" | "invite" })}
              >
                <option value="open">Open</option>
                <option value="request">Request to join</option>
                <option value="invite">Invite only</option>
              </select>
            </label>
          </div>
          <div className="flex gap-2 pt-1">
            <Button size="sm" onClick={handleSave} disabled={saving}>{saving ? "Saving…" : "Save"}</Button>
            <Button size="sm" variant="ghost" onClick={() => setEditing(false)}>Cancel</Button>
          </div>
        </div>
      ) : (
        <div className="space-y-3">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <h2 className="text-xl font-bold tracking-tight truncate">{meta.title || "Untitled Room"}</h2>
              {meta.description && (
                <p className="text-xs text-muted-foreground mt-1 leading-relaxed">{meta.description}</p>
              )}
            </div>
            {isHost && (
              <button
                onClick={() => setEditing(true)}
                className="text-[10px] font-mono uppercase tracking-widest text-muted-foreground hover:text-ember shrink-0"
              >
                Edit
              </button>
            )}
          </div>

          {meta.pinned_message && (
            <div className="border-l-2 border-ember pl-3 py-1 bg-ember/5 rounded-r">
              <div className="text-[9px] font-mono uppercase tracking-[0.3em] text-ember mb-0.5">PINNED</div>
              <p className="text-xs italic">{meta.pinned_message}</p>
            </div>
          )}

          {stats && stats.goal_seconds && stats.goal_seconds > 0 && (
            <div>
              <div className="flex justify-between text-[10px] font-mono uppercase tracking-widest text-muted-foreground mb-1">
                <span>Collective goal</span>
                <span>{fmtHours(stats.focus_seconds_total)} / {fmtHours(stats.goal_seconds)}</span>
              </div>
              <div className="h-1.5 bg-white/5 rounded-full overflow-hidden">
                <div className="h-full bg-ember transition-all" style={{ width: `${stats.progress_pct}%` }} />
              </div>
            </div>
          )}

          <div className="flex gap-4 text-[10px] font-mono uppercase tracking-widest text-muted-foreground pt-1">
            <span>Visibility · <span className="text-silver">{meta.visibility}</span></span>
            {mods.length > 0 && <span>Mods · <span className="text-silver">{mods.length}</span></span>}
          </div>
        </div>
      )}
    </div>
  );
}

export function JoinRequestsPanel({ roomId, isModerator }: { roomId: string; isModerator: boolean }) {
  const [reqs, setReqs] = useState<JoinRequest[]>([]);
  const [busyId, setBusyId] = useState<string | null>(null);
  const fetchReqs = useServerFn(listJoinRequests);
  const respond = useServerFn(respondToJoinRequest);

  useEffect(() => {
    if (!isModerator) return;
    let mounted = true;
    const load = () => fetchReqs({ data: { roomId } }).then((r) => { if (mounted) setReqs(r); }).catch(() => {});
    load();
    const ch = supabase
      .channel(`join-reqs:${roomId}`)
      .on("postgres_changes", { event: "*", schema: "public", table: "room_join_requests", filter: `room_id=eq.${roomId}` }, load)
      .subscribe();
    return () => { mounted = false; supabase.removeChannel(ch); };
  }, [roomId, isModerator, fetchReqs]);

  if (!isModerator || reqs.length === 0) return null;

  async function handle(reqId: string, approve: boolean) {
    setBusyId(reqId);
    try {
      await respond({ data: { requestId: reqId, approve } });
      setReqs((prev) => prev.filter((r) => r.id !== reqId));
      toast.success(approve ? "Approved" : "Denied");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Failed");
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className="mb-6 border border-ember/30 rounded-lg p-4 bg-ember/[0.03]">
      <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember mb-3">
        JOIN_REQUESTS · {reqs.length}
      </div>
      <ul className="space-y-2">
        {reqs.map((r) => (
          <li key={r.id} className="flex items-center justify-between gap-2 py-1">
            <div className="min-w-0 flex-1">
              <div className="text-sm truncate">{r.display_name}</div>
              {r.message && <div className="text-[11px] text-muted-foreground truncate italic">&ldquo;{r.message}&rdquo;</div>}
            </div>
            <div className="flex gap-1 shrink-0">
              <button
                onClick={() => handle(r.id, true)}
                disabled={busyId === r.id}
                className="text-[10px] font-mono uppercase tracking-widest px-2 py-1 border border-ember/40 text-ember rounded hover:bg-ember hover:text-obsidian transition-colors disabled:opacity-40"
              >
                Approve
              </button>
              <button
                onClick={() => handle(r.id, false)}
                disabled={busyId === r.id}
                className="text-[10px] font-mono uppercase tracking-widest px-2 py-1 border border-breach/40 text-breach rounded hover:bg-breach hover:text-obsidian transition-colors disabled:opacity-40"
              >
                Deny
              </button>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
