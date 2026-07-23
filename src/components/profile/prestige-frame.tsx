export function PrestigeFrame({ level, children }: { level: number; children: React.ReactNode }) {
  if (level <= 0) return <>{children}</>;
  const rings = Math.min(3, level);
  return (
    <div className="relative inline-block">
      {[...Array(rings)].map((_, i) => (
        <div key={i} className="absolute inset-0 rounded-full pointer-events-none"
             style={{
               border: `1px solid rgba(240,169,104,${0.9 - i * 0.25})`,
               transform: `scale(${1 + (i + 1) * 0.09})`,
               boxShadow: i === 0 ? "0 0 22px rgba(240,169,104,0.35)" : undefined,
             }} />
      ))}
      {children}
    </div>
  );
}

export function TitleBadge({ title }: { title: string | null | undefined }) {
  if (!title) return null;
  return <span className="inline-block mt-1 font-mono text-[10px] uppercase tracking-[0.3em] text-ember">{title}</span>;
}
