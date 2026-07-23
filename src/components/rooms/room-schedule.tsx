import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { toast } from "sonner";
import { createScheduledEvent, listSchedule, type ScheduledEvent } from "@/lib/room-extras.functions";

export function RoomSchedule({ roomId, canManage }: { roomId: string; canManage: boolean }) {
  const load = useServerFn(listSchedule);
  const add = useServerFn(createScheduledEvent);
  const [rows, setRows] = useState<ScheduledEvent[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [title, setTitle] = useState("");
  const [starts, setStarts] = useState("");
  const [mins, setMins] = useState(60);

  useEffect(() => {
    load({ data: { roomId } }).then((r) => setRows(r.rows));
  }, [roomId]);

  const submit = async () => {
    if (!title.trim() || !starts) return toast.error("Title and time required");
    try {
      await add({ data: { roomId, title, startsAt: new Date(starts).toISOString(), durationMinutes: mins } });
      setTitle("");
      setStarts("");
      const r = await load({ data: { roomId } });
      setRows(r.rows);
      setShowForm(false);
      toast.success("Scheduled");
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "Failed");
    }
  };

  return (
    <div className="border border-white/10 rounded-md p-4 bg-black/30">
      <div className="flex items-center justify-between">
        <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Schedule</p>
        {canManage && (
          <button
            onClick={() => setShowForm((v) => !v)}
            className="text-[10px] font-mono uppercase tracking-widest text-silver-dim hover:text-silver"
          >
            {showForm ? "Cancel" : "+ Add"}
          </button>
        )}
      </div>
      {showForm && (
        <div className="mt-3 space-y-2">
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="Title"
            className="w-full bg-black/50 border border-white/10 rounded px-3 py-2 text-sm text-silver"
          />
          <div className="flex gap-2">
            <input
              type="datetime-local"
              value={starts}
              onChange={(e) => setStarts(e.target.value)}
              className="flex-1 bg-black/50 border border-white/10 rounded px-3 py-2 text-sm text-silver"
            />
            <input
              type="number"
              value={mins}
              onChange={(e) => setMins(parseInt(e.target.value) || 60)}
              className="w-24 bg-black/50 border border-white/10 rounded px-3 py-2 text-sm text-silver"
              min={5}
              max={480}
            />
          </div>
          <button
            onClick={submit}
            className="w-full py-2 rounded border border-ember/50 text-ember font-mono text-xs uppercase tracking-widest hover:bg-ember/10"
          >
            Schedule Session
          </button>
        </div>
      )}
      {rows.length === 0 ? (
        <p className="mt-3 text-xs text-silver-dim">No upcoming sessions.</p>
      ) : (
        <ol className="mt-3 space-y-2">
          {rows.map((e) => (
            <li key={e.id} className="border-l-2 border-ember/40 pl-3 py-1">
              <p className="text-sm text-silver">{e.title}</p>
              <p className="text-[10px] font-mono text-silver-dim uppercase tracking-widest">
                {new Date(e.starts_at).toLocaleString()} · {e.duration_minutes}m
              </p>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
