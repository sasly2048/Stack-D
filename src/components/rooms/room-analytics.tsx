import { formatHours } from "@/lib/room";

interface RoomStats {
  collectiveSeconds: number;
  members: number;
  averageSessionSeconds: number;
  mostActive: string | null;
  peakHour: number | null;
}

export function RoomAnalytics({ stats }: { stats: RoomStats }) {
  const items: Array<[string, string]> = [
    ["Collective Hours", formatHours(stats.collectiveSeconds)],
    ["Members", String(stats.members)],
    ["Avg. Session", `${Math.round(stats.averageSessionSeconds / 60)}m`],
    ["Most Active", stats.mostActive ?? "—"],
    ["Peak Time", stats.peakHour != null ? `${stats.peakHour}:00` : "—"],
  ];
  return (
    <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
      {items.map(([k, v]) => (
        <div key={k} className="glass rounded-xl p-4">
          <div className="font-mono text-[10px] uppercase tracking-[0.3em] text-muted-foreground">{k}</div>
          <div className="mt-1 text-lg font-bold text-silver truncate">{v}</div>
        </div>
      ))}
    </div>
  );
}
