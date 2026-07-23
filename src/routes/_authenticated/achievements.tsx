import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { Nav } from "@/components/nav";
import { listAchievements, type Achievement } from "@/lib/achievements.functions";
import { NARRATIVE_CHAPTERS, chapterForXp, nextChapter } from "@/lib/copy";
import { useAuth } from "@/hooks/use-auth";
import { supabase } from "@/integrations/supabase/client";

export const Route = createFileRoute("/_authenticated/achievements")({
  head: () => ({
    meta: [
      { title: "Achievements — Stack'd" },
      { name: "description", content: "Your unlocks and the marks yet to earn." },
    ],
  }),
  component: AchievementsPage,
});

const TIER_STYLE: Record<string, string> = {
  bronze: "text-[#B87333] border-[#B87333]/30",
  silver: "text-silver border-silver/30",
  gold: "text-ember border-ember/40",
  obsidian: "text-white border-white/30",
};

function AchievementsPage() {
  const list = useServerFn(listAchievements);
  const { user } = useAuth();
  const [rows, setRows] = useState<Achievement[]>([]);
  const [stats, setStats] = useState({ unlocked: 0, total: 0 });
  const [xp, setXp] = useState(0);

  useEffect(() => {
    (async () => {
      const r = await list();
      setRows(r.rows);
      setStats({ unlocked: r.unlocked, total: r.total });
      if (user) {
        const { data } = await supabase.from("profiles").select("lifetime_xp").eq("id", user.id).maybeSingle();
        setXp(data?.lifetime_xp ?? 0);
      }
    })();
  }, [user?.id]);

  const chapter = chapterForXp(xp);
  const next = nextChapter(xp);
  const chapterPct = next ? Math.min(100, ((xp - chapter.minXp) / (next.minXp - chapter.minXp)) * 100) : 100;

  return (
    <div className="min-h-screen bg-obsidian text-silver">
      <Nav />
      <main className="max-w-5xl mx-auto px-6 pt-28 pb-24 space-y-12">
        <header>
          <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Marks</p>
          <h1 className="mt-3 text-4xl md:text-5xl font-serif">Achievements</h1>
          <p className="mt-3 text-silver-dim">
            {stats.unlocked} of {stats.total} unlocked. Every mark is a private record.
          </p>
          <div className="mt-4 h-1 bg-white/5 rounded-full overflow-hidden">
            <div
              className="h-full bg-ember transition-[width] duration-700"
              style={{ width: stats.total ? `${(stats.unlocked / stats.total) * 100}%` : "0%" }}
            />
          </div>
        </header>

        <section className="border border-ember/30 rounded-md p-6 bg-ember/[0.04]">
          <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Chapter {NARRATIVE_CHAPTERS.findIndex((c) => c.key === chapter.key) + 1}</p>
          <p className="mt-2 font-serif text-3xl text-silver">{chapter.title}</p>
          <p className="mt-1 text-silver-dim text-sm">{chapter.subtitle}</p>
          {next && (
            <>
              <div className="mt-4 h-1 bg-white/5 rounded-full overflow-hidden">
                <div className="h-full bg-ember transition-[width] duration-700" style={{ width: `${chapterPct}%` }} />
              </div>
              <p className="mt-2 font-mono text-[10px] tracking-widest uppercase text-silver-dim">
                {(next.minXp - xp).toLocaleString()} XP → {next.title}
              </p>
            </>
          )}
        </section>

        <ul className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {rows.map((a) => {
            const locked = !a.unlocked_at;
            return (
              <li
                key={a.id}
                className={`border rounded-md p-5 transition-all ${
                  locked
                    ? "border-white/5 opacity-40 hover:opacity-70"
                    : `${TIER_STYLE[a.tier] ?? TIER_STYLE.bronze} hover:shadow-[0_0_30px_-10px_currentColor]`
                }`}
              >
                <div className="flex items-start justify-between">
                  <p className={`font-mono text-[9px] tracking-[0.3em] uppercase ${locked ? "text-silver-dim" : ""}`}>
                    {a.tier}
                  </p>
                  <p className="font-mono text-[9px] tracking-[0.2em] uppercase text-silver-dim">
                    +{a.xp_reward} XP
                  </p>
                </div>
                <h3 className={`mt-3 text-xl font-serif ${locked ? "text-silver-dim" : "text-silver"}`}>
                  {a.name}
                </h3>
                <p className="mt-1 text-sm text-silver-dim">{a.description}</p>
                {a.unlocked_at && (
                  <p className="mt-3 font-mono text-[9px] tracking-[0.2em] uppercase text-silver-dim/60">
                    Unlocked {new Date(a.unlocked_at).toLocaleDateString()}
                  </p>
                )}
              </li>
            );
          })}
        </ul>
      </main>
    </div>
  );
}
