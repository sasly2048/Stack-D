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
  username: string | null;
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

interface LeagueRow {
  user_id: string;
  display_name: string | null;
  username: string | null;
  avatar_url: string | null;
  weekly_xp: number;
  rank: number;
  zone: "promote" | "safe" | "demote";
  tier: number;
  week_ends_at: string;
  is_me: boolean;
}

const TIERS = ["Bronze", "Silver", "Gold", "Ember", "Obsidian"];

type Tab = "week" | "individual" | "groups";

function Leaderboard() {
  // This week first: a small group you can climb beats an all-time list
  // nobody closes the gap on (Android parity).
  const [tab, setTab] = useState<Tab>("week");

  const leagueQuery = useQuery({
    queryKey: ["my-league"],
    queryFn: async () => {
      const { data, error } = await supabase.rpc("my_league");
      if (error) throw error;
      return (data ?? []) as LeagueRow[];
    },
  });

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
            .from("public_profiles")
            .select("id, display_name, username, avatar_url, lifetime_xp, current_focus_streak")
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
      <main className="app-page max-w-4xl">
        <div className="mb-12">
          <div className="ritual-label mb-2 text-muted-foreground">THE STANDINGS</div>
          <h1 className="page-title">Leaderboard</h1>
        </div>

        <div className="inline-flex gap-1 p-1 mb-8 rounded-full border border-white/10 bg-white/5 backdrop-blur-md">
          {(["week", "individual", "groups"] as const).map((t) => (
            <button
              key={t}
              onClick={() => setTab(t)}
              aria-pressed={tab === t}
              className={`px-5 py-2 rounded-full font-mono text-[10px] uppercase tracking-widest transition-all active:scale-[0.99] duration-200 ease-[var(--ease-ritual)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember ${
                tab === t ? "bg-silver text-obsidian" : "text-muted-foreground hover:text-silver"
              }`}
            >
              {t === "week" ? "This week" : t === "individual" ? "All time" : "Circles"}
            </button>
          ))}
        </div>

        {tab === "week" ? (
          <QueryBoundary
            isPending={leagueQuery.isPending}
            isError={leagueQuery.isError}
            error={leagueQuery.error}
            onRetry={() => leagueQuery.refetch()}
            errorTitle="Couldn't load your league."
            loadingLabel="Loading your league"
            skeleton={<SkeletonList rows={6} className="p-4" />}
          >
            <LeagueBoard rows={leagueQuery.data ?? []} />
          </QueryBoundary>
        ) : (
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
        )}
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
                {[
                  rows.filter((o) => o.display_name === r.display_name).length > 1 && r.username
                    ? `@${r.username}`
                    : null,
                  r.current_focus_streak > 0 ? `${r.current_focus_streak}-day streak` : null,
                ]
                  .filter(Boolean)
                  .join(" · ")}
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

function LeagueBoard({ rows }: { rows: LeagueRow[] }) {
  if (rows.length === 0) {
    return (
      <div className="rounded-2xl border border-white/10 bg-white/5 px-8 py-14 text-center">
        <h2 className="font-serif text-2xl text-silver">Your league opens with a stack</h2>
        <p className="mt-3 text-sm text-muted-foreground">
          Finish a stack this week to join a league of up to 30. Each Monday the top fifth moves up
          a tier.
        </p>
        <Link
          to="/start"
          className="mt-6 inline-block rounded-lg bg-silver px-6 py-3 text-sm font-semibold text-obsidian transition-all duration-200 ease-[var(--ease-ritual)] active:scale-[0.99]"
        >
          Start a Stack
        </Link>
      </div>
    );
  }
  const me = rows.find((r) => r.is_me) ?? rows[0];
  const above = rows.find((r) => r.rank === me.rank - 1);
  const daysLeft = Math.max(
    0,
    Math.ceil((new Date(me.week_ends_at).getTime() - Date.now()) / 86_400_000),
  );
  const tier = TIERS[me.tier - 1] ?? "Bronze";
  return (
    <div>
      <div className="mb-6 flex items-end gap-3">
        <span className="font-serif text-5xl tabular-nums text-silver">#{me.rank}</span>
        <span className="mb-2 font-mono text-[11px] uppercase tracking-widest text-muted-foreground">
          {tier} league · {daysLeft === 1 ? "1 day left" : `${daysLeft} days left`}
        </span>
      </div>
      <p className="mb-6 text-sm text-ember">
        {above
          ? `${above.weekly_xp - me.weekly_xp + 1} XP to pass ${above.display_name?.split(" ")[0] ?? "the next stacker"}`
          : rows.length === 1
            ? "You're the first here this week. Others join as they stack."
            : "You're leading the league this week."}
      </p>
      <ul className="space-y-2">
        {rows.map((r, i) => {
          const prev = rows[i - 1]?.zone;
          return (
            <li key={r.user_id}>
              {r.zone !== prev && r.zone !== "safe" && (
                <div
                  className={`mb-1 mt-4 font-mono text-[11px] uppercase tracking-widest ${r.zone === "promote" ? "text-ember" : "text-red-400"}`}
                >
                  {r.zone === "promote" ? "Promotion zone" : "Demotion zone"}
                </div>
              )}
              <div
                className={`flex items-center gap-4 rounded-2xl border px-5 py-3 ${r.is_me ? "border-ember/30 bg-ember/[0.07]" : "border-white/10 bg-white/[0.04]"} ${r.zone === "promote" ? "border-l-[3px] border-l-ember" : r.zone === "demote" ? "border-l-[3px] border-l-red-400" : ""}`}
              >
                <span className="w-10 font-mono text-xs tabular-nums text-muted-foreground">
                  #{r.rank}
                </span>
                <div className="flex size-8 items-center justify-center overflow-hidden rounded-full border border-white/10 bg-white/10 font-mono text-xs">
                  {r.avatar_url ? (
                    <img src={r.avatar_url} alt="" className="size-full object-cover" />
                  ) : (
                    (r.display_name ?? "?").slice(0, 1).toUpperCase()
                  )}
                </div>
                <div className="min-w-0 flex-1">
                  <div className={`truncate text-sm ${r.is_me ? "font-semibold" : ""}`}>
                    {r.display_name ?? "Anon"}
                  </div>
                  {r.username && (
                    <div className="font-mono text-[10px] text-muted-foreground">@{r.username}</div>
                  )}
                </div>
                <span className="font-mono text-sm tabular-nums">
                  {r.weekly_xp.toLocaleString()} XP
                </span>
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
