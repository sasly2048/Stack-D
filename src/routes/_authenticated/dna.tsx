import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { Nav } from "@/components/nav";
import { getProductivityDna, type DnaProfile } from "@/lib/dna.functions";

export const Route = createFileRoute("/_authenticated/dna")({
  head: () => ({
    meta: [
      { title: "Productivity DNA — Stack'd" },
      { name: "description", content: "Your unique focus signature." },
    ],
  }),
  component: DnaPage,
});

function Radar({ traits }: { traits: { label: string; value: number }[] }) {
  const size = 320;
  const cx = size / 2;
  const cy = size / 2;
  const R = 120;
  const n = traits.length;
  const points = traits.map((t, i) => {
    const angle = (Math.PI * 2 * i) / n - Math.PI / 2;
    const r = (t.value / 100) * R;
    return [cx + Math.cos(angle) * r, cy + Math.sin(angle) * r];
  });
  const poly = points.map((p) => p.join(",")).join(" ");
  const rings = [0.25, 0.5, 0.75, 1];

  return (
    <svg width={size} height={size} className="mx-auto">
      {rings.map((r) => (
        <polygon
          key={r}
          points={traits
            .map((_, i) => {
              const angle = (Math.PI * 2 * i) / n - Math.PI / 2;
              return `${cx + Math.cos(angle) * R * r},${cy + Math.sin(angle) * R * r}`;
            })
            .join(" ")}
          fill="none"
          stroke="rgba(255,255,255,0.08)"
        />
      ))}
      <polygon points={poly} fill="rgba(240,169,104,0.25)" stroke="#F0A968" strokeWidth={2} />
      {traits.map((t, i) => {
        const angle = (Math.PI * 2 * i) / n - Math.PI / 2;
        const x = cx + Math.cos(angle) * (R + 20);
        const y = cy + Math.sin(angle) * (R + 20);
        return (
          <text
            key={t.label}
            x={x}
            y={y}
            textAnchor="middle"
            dominantBaseline="middle"
            className="fill-muted-foreground"
            style={{ fontSize: 10, fontFamily: "monospace", textTransform: "uppercase", letterSpacing: "0.15em" }}
          >
            {t.label}
          </text>
        );
      })}
    </svg>
  );
}

function DnaPage() {
  const load = useServerFn(getProductivityDna);
  const [dna, setDna] = useState<DnaProfile | null>(null);

  useEffect(() => {
    load().then(setDna).catch(() => {});
  }, []);

  return (
    <div className="min-h-screen bg-obsidian text-silver">
      <Nav />
      <div className="pt-24 max-w-4xl mx-auto px-6 pb-24">
        <div className="text-[10px] font-mono uppercase tracking-widest text-muted-foreground">Productivity DNA</div>
        <h1 className="text-4xl font-serif mt-2 mb-8">Your focus signature</h1>

        {!dna ? (
          <div className="text-sm text-muted-foreground">Analyzing 60 days of focus…</div>
        ) : (
          <div className="grid md:grid-cols-2 gap-8">
            <div className="glass rounded-2xl p-6">
              <Radar traits={dna.traits} />
            </div>
            <div className="space-y-6">
              <div>
                <div className="text-[10px] font-mono uppercase tracking-widest text-muted-foreground">Archetype</div>
                <div className="text-3xl font-serif text-ember mt-1">{dna.archetype}</div>
              </div>
              <div>
                <div className="text-[10px] font-mono uppercase tracking-widest text-muted-foreground">Signature</div>
                <div className="text-2xl font-mono tracking-[0.4em] mt-1">{dna.signature}</div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <Stat label="Peak Hour" value={`${dna.peakHour.toString().padStart(2, "0")}:00`} />
                <Stat label="Consistency" value={`${dna.consistencyScore}%`} />
                <Stat label="Sessions" value={dna.totalSessions.toString()} />
                <Stat label="Traits" value={dna.traits.length.toString()} />
              </div>
              <div className="space-y-2 pt-2">
                {dna.traits.map((t) => (
                  <div key={t.label}>
                    <div className="flex justify-between text-[11px] font-mono text-muted-foreground mb-1">
                      <span>{t.label}</span>
                      <span>{t.value}</span>
                    </div>
                    <div className="h-1 bg-white/5 rounded-full overflow-hidden">
                      <div className="h-full bg-ember transition-all duration-700" style={{ width: `${t.value}%` }} />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="glass rounded-xl p-4">
      <div className="text-[10px] font-mono uppercase tracking-widest text-muted-foreground">{label}</div>
      <div className="text-lg font-serif mt-1">{value}</div>
    </div>
  );
}
