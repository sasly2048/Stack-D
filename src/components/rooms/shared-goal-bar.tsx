export function SharedGoalBar({ collectiveSeconds, goalHours }: { collectiveSeconds: number; goalHours: number }) {
  if (!goalHours || goalHours <= 0) return null;
  const goalSec = goalHours * 3600;
  const pct = Math.min(100, (collectiveSeconds / goalSec) * 100);
  const hoursDone = Math.floor(collectiveSeconds / 3600);
  return (
    <div className="rounded-xl border border-white/10 p-4 bg-white/[0.02]">
      <div className="flex items-baseline justify-between text-xs font-mono uppercase tracking-widest">
        <span className="text-muted-foreground">Shared Goal</span>
        <span className="text-silver">{hoursDone}h / {goalHours}h</span>
      </div>
      <div className="mt-3 h-2 rounded-full bg-white/5 overflow-hidden">
        <div className="h-full rounded-full transition-[width] duration-700"
             style={{ width: `${pct}%`, background: "linear-gradient(90deg,#F0A968,#F5C892)" }} />
      </div>
      {pct >= 100 && <div className="mt-2 text-xs text-ember">🎉 Goal reached — set a new one.</div>}
    </div>
  );
}
