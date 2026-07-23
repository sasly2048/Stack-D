import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { Nav } from "@/components/nav";
import { getActiveSeason, joinSeason, type Season, type SeasonStanding } from "@/lib/seasons.functions";
import { toast } from "sonner";

export const Route = createFileRoute("/_authenticated/seasons")({
  head: () => ({
    meta: [
      { title: "Seasons — Stack'd" },
      { name: "description", content: "Compete in the current focus season." },
    ],
  }),
  component: SeasonsPage,
});

function useCountdown(target: string | null) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);
  if (!target) return "";
  const d = new Date(target).getTime() - now;
  if (d <= 0) return "ended";
  const days = Math.floor(d / 86400000);
  const hrs = Math.floor((d % 86400000) / 3600000);
  const mins = Math.floor((d % 3600000) / 60000);
  return `${days}d ${hrs}h ${mins}m`;
}

function SeasonsPage() {
  const loadFn = useServerFn(getActiveSeason);
  const joinFn = useServerFn(joinSeason);
  const [season, setSeason] = useState<Season | null>(null);
  const [top, setTop] = useState<SeasonStanding[]>([]);
  const [myXp, setMyXp] = useState(0);
  const [myRank, setMyRank] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = () =>
    loadFn().then((r) => {
      setSeason(r.season);
      setTop(r.top);
      setMyXp(r.myXp);
      setMyRank(r.myRank);
    });

  useEffect(() => { refresh(); }, []);

  const countdown = useCountdown(season?.ends_at ?? null);

  const join = async () => {
    if (!season) return;
    setBusy(true);
    try {
      await joinFn({ data: { seasonId: season.id } });
      toast.success("Joined the season.");
      await refresh();
    } catch (e) {
      toast.error((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="min-h-screen bg-obsidian text-silver">
      <Nav />
      <div className="pt-24 max-w-4xl mx-auto px-6 pb-24">
        <div className="text-[10px] font-mono uppercase tracking-widest text-muted-foreground">Seasons</div>
        <h1 className="text-3xl font-serif mt-1 mb-8">Compete in cycles.</h1>

        {!season ? (
          <div className="glass rounded-xl p-12 text-center">
            <div className="text-sm text-muted-foreground">No active season right now. Check back soon.</div>
          </div>
        ) : (
          <>
            <div className="glass rounded-2xl p-8 mb-8 relative overflow-hidden">
              <div className="absolute inset-0 opacity-30 bg-gradient-to-br from-ember/20 via-transparent to-transparent pointer-events-none" />
              <div className="relative">
                <div className="flex items-start justify-between gap-6 flex-wrap">
                  <div>
                    <h2 className="text-4xl font-serif text-ember">{season.name}</h2>
                    {season.description && (
                      <p className="text-sm text-muted-foreground mt-2 max-w-md">{season.description}</p>
                    )}
                  </div>
                  <div className="text-right">
                    <div className="text-[10px] font-mono uppercase tracking-widest text-muted-foreground">Ends in</div>
                    <div className="text-2xl font-mono mt-1">{countdown}</div>
                  </div>
                </div>

                <div className="grid grid-cols-3 gap-4 mt-8">
                  <Stat label="Your XP" value={myXp.toLocaleString()} />
                  <Stat label="Rank" value={myRank ? `#${myRank}` : "—"} />
                  <Stat label="XP Multiplier" value={`${season.xp_multiplier}x`} />
                </div>

                {myRank === null && (
                  <button
                    onClick={join}
                    disabled={busy}
                    className="mt-6 btn-ember px-6 py-2.5 border border-silver/20 rounded-full text-silver text-xs font-mono uppercase tracking-widest disabled:opacity-60"
                  >
                    {busy ? "Joining…" : "Enter Season"}
                  </button>
                )}
              </div>
            </div>

            <h3 className="text-[10px] font-mono uppercase tracking-widest text-muted-foreground mb-3">Top 50</h3>
            <div className="space-y-1">
              {top.length === 0 && (
                <div className="text-sm text-muted-foreground text-center py-8">Be the first to score.</div>
              )}
              {top.map((s) => (
                <div key={s.user_id} className="glass rounded-lg px-4 py-2.5 flex items-center gap-4">
                  <div className={`font-serif text-lg w-8 text-center ${
                    s.rank === 1 ? "text-ember" : s.rank <= 3 ? "text-silver" : "text-muted-foreground"
                  }`}>
                    {s.rank}
                  </div>
                  {s.avatar_url ? (
                    <img src={s.avatar_url} alt="" className="size-8 rounded-full object-cover" />
                  ) : (
                    <div className="size-8 rounded-full bg-white/10 flex items-center justify-center text-xs font-mono">
                      {(s.display_name ?? "?").slice(0, 2).toUpperCase()}
                    </div>
                  )}
                  <div className="flex-1 text-sm truncate">{s.display_name ?? "Anon"}</div>
                  <div className="text-sm font-mono text-ember">{s.xp.toLocaleString()}</div>
                </div>
              ))}
            </div>
          </>
        )}
      </div>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="text-[10px] font-mono uppercase tracking-widest text-muted-foreground">{label}</div>
      <div className="text-2xl font-serif mt-1">{value}</div>
    </div>
  );
}
