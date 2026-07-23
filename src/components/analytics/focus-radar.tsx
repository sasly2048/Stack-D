interface Radar { consistency: number; deepWork: number; streak: number; breaks: number; duration: number; }

export function FocusRadar({ data, size = 220 }: { data: Radar; size?: number }) {
  const axes: Array<[keyof Radar, string]> = [
    ["consistency", "Consistency"],
    ["deepWork", "Deep Work"],
    ["streak", "Streak"],
    ["breaks", "Restraint"],
    ["duration", "Duration"],
  ];
  const cx = size / 2, cy = size / 2, r = size / 2 - 30;
  const point = (i: number, v: number) => {
    const a = (Math.PI * 2 * i) / axes.length - Math.PI / 2;
    const rr = (v / 100) * r;
    return [cx + Math.cos(a) * rr, cy + Math.sin(a) * rr] as const;
  };
  const poly = axes.map((_, i) => point(i, Math.max(0, Math.min(100, data[axes[i][0]])))).map((p) => p.join(",")).join(" ");
  const ringPoly = (frac: number) => axes.map((_, i) => point(i, 100 * frac)).map((p) => p.join(",")).join(" ");
  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="text-silver">
      {[0.25, 0.5, 0.75, 1].map((f) => (
        <polygon key={f} points={ringPoly(f)} fill="none" stroke="rgba(255,255,255,0.06)" />
      ))}
      {axes.map((_, i) => {
        const [x, y] = point(i, 100);
        return <line key={i} x1={cx} y1={cy} x2={x} y2={y} stroke="rgba(255,255,255,0.06)" />;
      })}
      <polygon points={poly} fill="rgba(240,169,104,0.25)" stroke="#F0A968" strokeWidth={2} />
      {axes.map(([, label], i) => {
        const [x, y] = point(i, 118);
        return <text key={label} x={x} y={y} textAnchor="middle" fontSize={9} fill="currentColor" opacity={0.7} className="font-mono uppercase">{label}</text>;
      })}
    </svg>
  );
}
