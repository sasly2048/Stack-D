import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { supabase } from "@/integrations/supabase/client";
import { listMilestones, type Milestone } from "@/lib/room-extras.functions";

function relTime(iso: string) {
  const diff = Math.floor((Date.now() - new Date(iso).getTime()) / 1000);
  if (diff < 60) return `${diff}s ago`;
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
  return `${Math.floor(diff / 86400)}d ago`;
}

export function RoomTimeline({ roomId }: { roomId: string }) {
  const load = useServerFn(listMilestones);
  const [rows, setRows] = useState<Milestone[]>([]);

  useEffect(() => {
    load({ data: { roomId } }).then((r) => setRows(r.rows));
    const ch = supabase
      .channel(`milestones:${roomId}`)
      .on(
        "postgres_changes",
        { event: "INSERT", schema: "public", table: "room_milestones", filter: `room_id=eq.${roomId}` },
        (payload) => {
          const m = payload.new as Milestone;
          setRows((prev) => [m, ...prev].slice(0, 30));
        },
      )
      .subscribe();
    return () => {
      supabase.removeChannel(ch);
    };
  }, [roomId]);

  if (rows.length === 0) {
    return (
      <div className="border border-white/10 rounded-md p-4 bg-black/30">
        <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">Milestones</p>
        <p className="mt-3 text-sm text-silver-dim">The room's story writes itself once it begins.</p>
      </div>
    );
  }

  return (
    <div className="border border-white/10 rounded-md p-4 bg-black/30">
      <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Milestones</p>
      <ol className="mt-3 space-y-2 max-h-64 overflow-y-auto">
        {rows.map((m) => (
          <li key={m.id} className="flex gap-3 text-sm">
            <span className="size-1.5 mt-2 rounded-full bg-ember shrink-0" />
            <div className="flex-1">
              <p className="text-silver">{m.label}</p>
              <p className="text-[10px] font-mono text-silver-dim uppercase tracking-widest">
                {m.kind} · {relTime(m.reached_at)}
              </p>
            </div>
          </li>
        ))}
      </ol>
    </div>
  );
}
