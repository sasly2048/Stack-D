import { useMemo } from "react";

/**
 * GitHub-style contribution heatmap for focus sessions.
 * Accepts a map of ISO-date -> minutes.
 */
export function AnimatedHeatmap({ data, weeks = 26 }: { data: Record<string, number>; weeks?: number }) {
  const cells = useMemo(() => {
    const out: Array<{ date: string; minutes: number; col: number; row: number }> = [];
    const today = new Date();
    const start = new Date(today);
    start.setDate(today.getDate() - weeks * 7);
    for (let i = 0; i < weeks * 7; i++) {
      const d = new Date(start); d.setDate(start.getDate() + i);
      const iso = d.toISOString().slice(0, 10);
      out.push({ date: iso, minutes: data[iso] ?? 0, col: Math.floor(i / 7), row: i % 7 });
    }
    return out;
  }, [data, weeks]);
  const max = Math.max(60, ...cells.map((c) => c.minutes));
  const size = 11, gap = 2;
  const w = weeks * (size + gap);
  const h = 7 * (size + gap);
  return (
    <svg width={w} height={h} viewBox={`0 0 ${w} ${h}`}>
      {cells.map((c, i) => {
        const intensity = c.minutes === 0 ? 0 : Math.min(1, c.minutes / max);
        const fill = c.minutes === 0
          ? "rgba(255,255,255,0.04)"
          : `rgba(240,169,104,${0.25 + intensity * 0.65})`;
        return (
          <rect
            key={c.date}
            x={c.col * (size + gap)}
            y={c.row * (size + gap)}
            width={size}
            height={size}
            rx={2}
            fill={fill}
            style={{ animation: `hm-in 0.3s ease-out ${(i * 4)}ms both` }}
          >
            <title>{c.date}: {c.minutes} min</title>
          </rect>
        );
      })}
      <style>{`@keyframes hm-in{from{opacity:0;transform:scale(.7)}to{opacity:1;transform:scale(1)}}`}</style>
    </svg>
  );
}
