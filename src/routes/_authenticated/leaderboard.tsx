import { useQuery } from "@tanstack/react-query";
import { createFileRoute, Link } from "@tanstack/react-router";
import { useState } from "react";
import { supabase } from "@/integrations/supabase/client";
import { Nav } from "@/components/nav";
import { QueryBoundary, SkeletonList } from "@/components/query-states";
import { BadgeHint } from "@/components/ui/badge-hint";

export const Route = createFileRoute("/_authenticated/leaderboard")({
  head: () => ({
    meta: [
      { title: "Leaderboard — Stack'd" },
      {
        name: "description",
        content:
          "See how you rank against other stackers and groups by lifetime XP and current focus streak.",
      },
      { property: "og:title", content: "Leaderboard — Stack'd" },
      {
        property: "og:description",
        content: "Individual and group rankings by lifetime XP and focus streak.",
      },
      { name: "robots", content: "noindex" },
    ],
  }),
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

  const meQuery = useQuery({
    queryKey: ["me-id"],
    queryFn: async () => (await supabase.auth.getUser()).data.user?.id ?? null,
  });
  const me = meQuery.data ?? null;

  const boardQuery = useQuery({
    queryKey: ["leaderboard"],
    queryFn: async () => {
      const [{ data: people, error: peopleErr }, { data: grps, error: grpsErr }] =
        await Promise.all([
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
        ]);
      // Errors were previously discarded, so a failed board rendered as an
      // empty one. Surfacing them lets the boundary below show a retry.
      if (peopleErr) throw peopleErr;
      if (grpsErr) throw grpsErr;

      const groupIds = (grps ?? []).map((g) => g.id);
      // Scoped to the ≤100 groups actually on screen. This previously selected
      // every row of group_members platform-wide and counted them in the
      // browser, so the payload grew with total membership across all users
      // while only 100 counts were ever displayed.
      const { data: members, error: membersErr } = groupIds.length
        ? await supabase.from("group_members").select("group_id").in("group_id", groupIds)
        : { data: [], error: null };
      if (membersErr) throw membersErr;

      const memberCounts = new Map<string, number>();
      (members ?? []).forEach((m) =>
        memberCounts.set(m.group_id, (memberCounts.get(m.group_id) ?? 0) + 1),
      );

      return {
        individuals: (people ?? []) as IndividualRow[],
        groups: (grps ?? []).map((g) => ({
          id: g.id,
          name: g.name,
          total_group_xp: g.total_group_xp ?? 0,
          member_count: memberCounts.get(g.id) ?? 0,
        })),
      };
    },
  });

  const individuals = boardQuery.data?.individuals ?? [];
  const groups = boardQuery.data?.groups ?? [];

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
              aria-pressed={tab === t}
              className={`px-5 py-2 rounded-full font-mono text-[10px] uppercase tracking-widest transition-all active:scale-[0.99] duration-200 ease-[var(--ease-ritual)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember ${
                tab === t ? "bg-silver text-obsidian" : "text-muted-foreground hover:text-silver"
              }`}
            >
              {t === "individual" ? "Individuals" : "Circles"}
            </button>
          ))}
        </div>

        <div className="rounded-2xl border border-white/10 bg-white/5 backdrop-blur-xl shadow-2xl overflow-hidden">
          <QueryBoundary
            isPending={boardQuery.isPending}
            isError={boardQuery.isError}
            error={boardQuery.error}
            onRetry={() => boardQuery.refetch()}
            errorTitle="Couldn't load the leaderboard."
            loadingLabel="Loading the leaderboard"
            skeleton={<SkeletonList rows={6} className="p-4" />}
          >
            {tab === "individual" ? (
              <IndividualList rows={individuals} meId={me} />
            ) : (
              <GroupList rows={groups} />
            )}
          </QueryBoundary>
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
        No sessions recorded yet.{" "}
        {/* The board is empty because nobody has stacked — the only useful next
            step is to be the first, so link straight at it. */}
        <Link
          to="/start"
          className="text-silver underline rounded active:scale-[0.99] transition-all duration-200 ease-[var(--ease-ritual)] hover:text-ember focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember"
        >
          Start the first one.
        </Link>
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
            className={`flex items-center gap-4 px-5 sm:px-7 py-4 hover:bg-white/[0.03] transition-all duration-200 ease-[var(--ease-ritual)] ${mine ? "bg-ember/5" : ""}`}
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
              <div className="flex items-center gap-2 text-sm font-semibold truncate">
                <span className="truncate">{r.display_name ?? "Anonymous"}</span>
                {/* The bare "YOU" span became a BadgeHint so self-identification
                    reads the same here as everywhere else in the app. */}
                {mine && <BadgeHint tone="accent">You</BadgeHint>}
                {/* Rank 1 already has the amber numeral, which is easy to miss
                    on a long board — the word is what actually lands. */}
                {i === 0 && (
                  <BadgeHint tone="info" title="Highest lifetime XP">
                    Leader
                  </BadgeHint>
                )}
              </div>
              <div className="font-mono text-[10px] uppercase tracking-widest text-muted-foreground">
                streak · {r.current_focus_streak}
              </div>
            </div>
            <div className="text-right">
              <div className="font-mono text-lg font-bold tabular-nums">
                {r.lifetime_xp.toLocaleString()}
              </div>
              <div className="font-mono text-[9px] uppercase tracking-widest text-muted-foreground">
                XP
              </div>
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
        No circles yet.{" "}
        <Link to="/groups" className="text-silver underline">
          Form one.
        </Link>
      </div>
    );
  }
  return (
    <ul className="divide-y divide-white/5">
      {rows.map((g, i) => (
        <li
          key={g.id}
          className="flex items-center gap-4 px-5 sm:px-7 py-4 hover:bg-white/[0.03] transition-all duration-200 ease-[var(--ease-ritual)]"
        >
          <span
            className="w-8 font-mono text-sm font-bold tabular-nums"
            style={{ color: rankBadge(i) }}
          >
            {String(i + 1).padStart(2, "0")}
          </span>
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 text-sm font-semibold truncate">
              <span className="truncate">{g.name}</span>
              {i === 0 && (
                <BadgeHint tone="info" title="Highest total group XP">
                  Leader
                </BadgeHint>
              )}
            </div>
            <div className="font-mono text-[10px] uppercase tracking-widest text-muted-foreground">
              {g.member_count} {g.member_count === 1 ? "member" : "members"}
            </div>
          </div>
          <div className="text-right">
            <div className="font-mono text-lg font-bold tabular-nums">
              {g.total_group_xp.toLocaleString()}
            </div>
            <div className="font-mono text-[9px] uppercase tracking-widest text-muted-foreground">
              Group XP
            </div>
          </div>
        </li>
      ))}
    </ul>
  );
}
