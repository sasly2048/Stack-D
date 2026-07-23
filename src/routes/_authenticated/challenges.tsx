import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { Nav } from "@/components/nav";
import { listChallenges, type ChallengeRow } from "@/lib/challenges.functions";

export const Route = createFileRoute("/_authenticated/challenges")({
  head: () => ({
    meta: [
      { title: "Challenges — Stack'd" },
      { name: "description", content: "Daily and weekly rites. Small, deliberate targets." },
    ],
  }),
  component: ChallengesPage,
});

function ChallengesPage() {
  const list = useServerFn(listChallenges);
  const [rows, setRows] = useState<ChallengeRow[]>([]);

  useEffect(() => {
    list().then((r) => setRows(r.rows));
  }, []);

  const daily = rows.filter((r) => r.cadence === "daily");
  const weekly = rows.filter((r) => r.cadence === "weekly");

  return (
    <div className="min-h-screen bg-obsidian text-silver">
      <Nav />
      <main className="max-w-5xl mx-auto px-6 pt-28 pb-24 space-y-12">
        <header>
          <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Rites</p>
          <h1 className="mt-3 text-4xl md:text-5xl font-serif">Challenges</h1>
          <p className="mt-3 text-silver-dim max-w-lg">
            Daily and weekly targets. Small enough to keep. Sharp enough to matter.
          </p>
        </header>

        <Group title="Today" rows={daily} />
        <Group title="This week" rows={weekly} />
      </main>
    </div>
  );
}

function Group({ title, rows }: { title: string; rows: ChallengeRow[] }) {
  return (
    <section className="space-y-4">
      <h2 className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">{title}</h2>
      <ul className="grid grid-cols-1 md:grid-cols-2 gap-3">
        {rows.map((c) => {
          const pct = Math.min(100, Math.round((c.progress / c.target) * 100));
          const done = !!c.completed_at;
          return (
            <li
              key={c.id}
              className={`border rounded-md p-5 transition-colors ${
                done ? "border-ember/40 bg-ember/[0.04]" : "border-white/10"
              }`}
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <h3 className="font-serif text-xl">{c.name}</h3>
                  <p className="mt-1 text-sm text-silver-dim">{c.description}</p>
                </div>
                <span className="font-mono text-[10px] tracking-[0.2em] uppercase text-ember shrink-0">
                  +{c.xp_reward} XP
                </span>
              </div>
              <div className="mt-4 h-1 bg-white/5 rounded-full overflow-hidden">
                <div
                  className={`h-full transition-[width] duration-700 ${done ? "bg-ember" : "bg-silver"}`}
                  style={{ width: `${pct}%` }}
                />
              </div>
              <p className="mt-2 font-mono text-[10px] tracking-[0.2em] uppercase text-silver-dim">
                {done ? "Completed" : `${c.progress} / ${c.target}${c.metric === "focus_minutes" ? " min" : ""}`}
              </p>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
