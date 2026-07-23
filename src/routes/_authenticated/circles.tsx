import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { Nav } from "@/components/nav";
import { listMyCircles, getCircleDetail, type CircleDetail } from "@/lib/circles.functions";

export const Route = createFileRoute("/_authenticated/circles")({
  head: () => ({
    meta: [
      { title: "Study Circles — Stack'd" },
      { name: "description", content: "Weekly rankings across your focus circles." },
    ],
  }),
  component: CirclesPage,
});

function CirclesPage() {
  const listFn = useServerFn(listMyCircles);
  const detailFn = useServerFn(getCircleDetail);
  const [circles, setCircles] = useState<{ id: string; name: string; total_xp: number }[]>([]);
  const [active, setActive] = useState<string | null>(null);
  const [detail, setDetail] = useState<CircleDetail | null>(null);

  useEffect(() => {
    listFn().then((rows) => {
      setCircles(rows);
      if (rows[0]) setActive(rows[0].id);
    });
  }, []);

  useEffect(() => {
    if (!active) return;
    setDetail(null);
    detailFn({ data: { id: active } }).then(setDetail);
  }, [active]);

  return (
    <div className="min-h-screen bg-obsidian text-silver">
      <Nav />
      <div className="pt-24 max-w-6xl mx-auto px-6 pb-24">
        <div className="flex items-baseline justify-between mb-8">
          <div>
            <div className="text-[10px] font-mono uppercase tracking-widest text-muted-foreground">Study Circles</div>
            <h1 className="text-3xl font-serif mt-1">Your circles</h1>
          </div>
          <Link to="/groups" className="text-xs font-mono uppercase tracking-widest text-muted-foreground hover:text-ember">
            Manage →
          </Link>
        </div>

        {circles.length === 0 ? (
          <div className="glass rounded-xl p-12 text-center">
            <div className="text-sm text-muted-foreground mb-4">You haven't joined any circles yet.</div>
            <Link to="/groups" className="btn-ember px-5 py-2 border border-silver/20 rounded-full text-silver text-xs font-mono uppercase tracking-widest">
              Create or join
            </Link>
          </div>
        ) : (
          <div className="grid md:grid-cols-[240px_1fr] gap-8">
            <aside className="space-y-1">
              {circles.map((c) => (
                <button
                  key={c.id}
                  onClick={() => setActive(c.id)}
                  className={`w-full text-left px-4 py-3 rounded-lg border transition-colors ${
                    active === c.id ? "bg-ember/10 border-ember/40 text-ember" : "border-white/5 hover:bg-white/5"
                  }`}
                >
                  <div className="text-sm font-medium truncate">{c.name}</div>
                  <div className="text-[10px] font-mono uppercase tracking-widest text-muted-foreground mt-0.5">
                    {c.total_xp.toLocaleString()} XP
                  </div>
                </button>
              ))}
            </aside>

            <section>
              {!detail ? (
                <div className="text-sm text-muted-foreground">Loading circle…</div>
              ) : (
                <>
                  <div className="flex items-baseline justify-between mb-6">
                    <h2 className="text-2xl font-serif">{detail.name}</h2>
                    <div className="text-xs font-mono text-muted-foreground">
                      {detail.member_count} members · {detail.total_xp.toLocaleString()} XP
                    </div>
                  </div>
                  <div className="space-y-2">
                    {detail.members.map((m, i) => (
                      <div key={m.user_id} className="glass rounded-lg px-4 py-3 flex items-center gap-4">
                        <div className="font-serif text-xl w-8 text-center text-muted-foreground">{i + 1}</div>
                        <div className="relative">
                          {m.avatar_url ? (
                            <img src={m.avatar_url} alt="" className="size-9 rounded-full object-cover" />
                          ) : (
                            <div className="size-9 rounded-full bg-white/10 flex items-center justify-center text-xs font-mono">
                              {(m.display_name ?? "?").slice(0, 2).toUpperCase()}
                            </div>
                          )}
                          {m.is_online && (
                            <span className="absolute -bottom-0.5 -right-0.5 size-2.5 rounded-full bg-ember ring-2 ring-obsidian" />
                          )}
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="text-sm truncate">{m.display_name ?? "Anon"}</div>
                          <div className="text-[10px] font-mono uppercase tracking-widest text-muted-foreground">
                            {m.current_streak}🔥 streak · {m.weekly_minutes}m this week
                          </div>
                        </div>
                        <div className="text-right">
                          <div className="text-sm font-mono text-ember">{m.weekly_xp.toLocaleString()}</div>
                          <div className="text-[9px] font-mono uppercase tracking-widest text-muted-foreground">Weekly XP</div>
                        </div>
                      </div>
                    ))}
                  </div>
                </>
              )}
            </section>
          </div>
        )}
      </div>
    </div>
  );
}
