import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { supabase } from "@/integrations/supabase/client";
import { Nav } from "@/components/nav";

export const Route = createFileRoute("/_authenticated/leaderboard")({
  head: () => ({ meta: [{ title: "Leaderboard — Stack'd" }] }),
  component: Leaderboard,
});

interface IndividualRow {
  id: string;
  display_name: string | null;
  avatar_url: string | null;
  lifetime_xp: number;
  current_focus_streak: number;
}

interface GroupRow {
  id: string;
  name: string;
  total_group_xp: number;
  member_count: number;
}

type Tab = "individual" | "groups";

function Leaderboard() {
  const [tab, setTab] = useState<Tab>("individual");
  const [loading, setLoading] = useState(true);
  const [me, setMe] = useState<string | null>(null);
  const [individuals, setIndividuals] = useState<IndividualRow[]>([]);
  const [groups, setGroups] = useState<GroupRow[]>([]);

  useEffect(() => {
    let mounted = true;
    (async () => {
      const { data: u } = await supabase.auth.getUser();
      if (mounted) setMe(u.user?.id ?? null);

      const [{ data: people }, { data: grps }, { data: members }] = await Promise.all([
        supabase
          .from("profiles")
          .select("id, display_name, avatar_url, lifetime_xp, current_focus_streak")
          .order("lifetime_xp", { ascending: false })
          .limit(100),
        supabase
          .from("focus_groups")
          .select("id, name, total_group_xp")
          .order("total_group_xp", { ascending: false })
          .limit(100),
        supabase.from("group_members").select("group_id"),
      ]);

      if (!mounted) return;
      const memberCounts = new Map<string, number>();
      (members ?? []).forEach((m) =>
        memberCounts.set(m.group_id, (memberCounts.get(m.group_id) ?? 0) + 1),
      );

      setIndividuals((people ?? []) as IndividualRow[]);
      setGroups(
        (grps ?? []).map((g) => ({
          id: g.id,
          name: g.name,
          total_group_xp: g.total_group_xp ?? 0,
          member_count: memberCounts.get(g.id) ?? 0,
        })),
      );
      setLoading(false);
    })();
    return () => { mounted = false; };
  }, []);

  return (
    <div className="min-h-screen bg-obsidian text-silver">
      <Nav />
      <main className="pt-32 pb-20 px-6 max-w-4xl mx-auto">
        <div className="mb-12">
          <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground mb-2">
            LEADERBOARD / LIFETIME_XP
          </div>
          <h1 className="text-5xl font-extrabold tracking-tighter">Who's stacking.</h1>
        </div>

        <div className="inline-flex gap-1 p-1 mb-8 rounded-full border border-white/10 bg-white/5 backdrop-blur-md">
          {(["individual", "groups"] as const).map((t) => (
            <button
              key={t}
              onClick={() => setTab(t)}
              className={`px-5 py-2 rounded-full font-mono text-[10px] uppercase tracking-widest transition-all ${
                tab === t ? "bg-silver text-obsidian" : "text-muted-foreground hover:text-silver"
              }`}
            >
              {t === "individual" ? "Individuals" : "Circles"}
            </button>
          ))}
        </div>

        <div className="rounded-2xl border border-white/10 bg-white/5 backdrop-blur-xl shadow-2xl overflow-hidden">
          {loading ? (
            <div className="py-12 text-center font-mono text-xs text-muted-foreground uppercase tracking-widest">
              Loading…
            </div>
          ) : tab === "individual" ? (
            <IndividualList rows={individuals} meId={me} />
          ) : (
            <GroupList rows={groups} />
          )}
        </div>
      </main>
    </div>
  );
}

function rankBadge(i: number): string {
  if (i === 0) return "#F59E0B";
  if (i === 1) return "#94A3B8";
  if (i === 2) return "#B45309";
  return "rgba(255,255,255,0.4)";
}

function IndividualList({ rows, meId }: { rows: IndividualRow[]; meId: string | null }) {
  if (rows.length === 0) {
    return (
      <div className="py-12 text-center font-mono text-xs text-muted-foreground uppercase tracking-widest">
        No rituals recorded yet.
      </div>
    );
  }
  return (
    <ul className="divide-y divide-white/5">
      {rows.map((r, i) => {
        const mine = r.id === meId;
        return (
          <li
            key={r.id}
            className={`flex items-center gap-4 px-5 sm:px-7 py-4 ${mine ? "bg-ember/5" : ""}`}
          >
            <span
              className="w-8 font-mono text-sm font-bold tabular-nums"
              style={{ color: rankBadge(i) }}
            >
              {String(i + 1).padStart(2, "0")}
            </span>
            <div className="size-9 rounded-full bg-white/10 border border-white/10 overflow-hidden flex items-center justify-center font-mono text-xs">
              {r.avatar_url ? (
                <img src={r.avatar_url} alt="" className="size-full object-cover" />
              ) : (
                (r.display_name ?? "?").slice(0, 1).toUpperCase()
              )}
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-sm font-semibold truncate">
                {r.display_name ?? "Anonymous"} {mine && <span className="text-ember text-[10px] font-mono ml-1">YOU</span>}
              </div>
              <div className="font-mono text-[10px] uppercase tracking-widest text-muted-foreground">
                streak · {r.current_focus_streak}
              </div>
            </div>
            <div className="text-right">
              <div className="font-mono text-lg font-bold tabular-nums">{r.lifetime_xp.toLocaleString()}</div>
              <div className="font-mono text-[9px] uppercase tracking-widest text-muted-foreground">XP</div>
            </div>
          </li>
        );
      })}
    </ul>
  );
}

function GroupList({ rows }: { rows: GroupRow[] }) {
  if (rows.length === 0) {
    return (
      <div className="py-12 text-center font-mono text-xs text-muted-foreground uppercase tracking-widest">
        No circles yet. <Link to="/groups" className="text-silver underline">Form one.</Link>
      </div>
    );
  }
  return (
    <ul className="divide-y divide-white/5">
      {rows.map((g, i) => (
        <li key={g.id} className="flex items-center gap-4 px-5 sm:px-7 py-4">
          <span className="w-8 font-mono text-sm font-bold tabular-nums" style={{ color: rankBadge(i) }}>
            {String(i + 1).padStart(2, "0")}
          </span>
          <div className="flex-1 min-w-0">
            <div className="text-sm font-semibold truncate">{g.name}</div>
            <div className="font-mono text-[10px] uppercase tracking-widest text-muted-foreground">
              {g.member_count} {g.member_count === 1 ? "soul" : "souls"}
            </div>
          </div>
          <div className="text-right">
            <div className="font-mono text-lg font-bold tabular-nums">{g.total_group_xp.toLocaleString()}</div>
            <div className="font-mono text-[9px] uppercase tracking-widest text-muted-foreground">Group XP</div>
          </div>
        </li>
      ))}
    </ul>
  );
}
