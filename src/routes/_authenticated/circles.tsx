import { useQuery } from "@tanstack/react-query";
import { createFileRoute, Link } from "@tanstack/react-router";
import { useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { Nav } from "@/components/nav";
import { QueryBoundary, SkeletonList } from "@/components/query-states";
import { BadgeHint } from "@/components/ui/badge-hint";
import { INTERACTIVE, INTERACTIVE_TIGHT } from "@/components/ui/interactive";
import { listMyCircles, getCircleDetail, type CircleDetail } from "@/lib/circles.functions";

export const Route = createFileRoute("/_authenticated/circles")({
  head: () => ({
    meta: [
      { title: "Study Circles — Stack'd" },
      { name: "description", content: "Weekly rankings across your Stack'd study circles, so you can see how your group holds focus together." },
      { property: "og:title", content: "Study Circles — Stack'd" },
      { property: "og:description", content: "Weekly rankings across your Stack'd study circles, so you can see how your group holds focus together." },
    ],
  }),
  component: CirclesPage,
});

function CirclesPage() {
  const listFn = useServerFn(listMyCircles);
  const detailFn = useServerFn(getCircleDetail);
  const [picked, setPicked] = useState<string | null>(null);

  // Both fetches were bare `.then()` chains with no .catch — a failure was an
  // unhandled rejection and the sidebar simply never appeared, with no error
  // and no loading state to explain the blank.
  const circlesQuery = useQuery({
    queryKey: ["circles"],
    queryFn: () => listFn(),
  });
  const circles = circlesQuery.data ?? [];
  // Defaulting off the fetched rows instead of a second setState, so there is
  // no frame where the list is loaded but nothing is selected.
  const active = picked ?? circles[0]?.id ?? null;

  const detailQuery = useQuery({
    queryKey: ["circle", active],
    queryFn: () => detailFn({ data: { id: active as string } }),
    enabled: !!active,
  });
  const detail: CircleDetail | null = detailQuery.data ?? null;

  return (
    <div className="min-h-screen bg-obsidian text-silver">
      <Nav />
      <div className="pt-24 max-w-6xl mx-auto px-6 pb-24">
        <div className="flex items-baseline justify-between mb-8">
          <div>
            <div className="text-[10px] font-mono uppercase tracking-widest text-muted-foreground">
              Study Circles
            </div>
            <h1 className="text-3xl font-serif mt-1">Your circles</h1>
          </div>
          <Link
            to="/groups"
            className={`text-xs font-mono uppercase tracking-widest text-muted-foreground hover:text-ember ${INTERACTIVE_TIGHT}`}
          >
            Manage →
          </Link>
        </div>

        {/* Loading is checked before emptiness. "You haven't joined any circles
            yet" used to show on every first paint, including for members of
            five circles. */}
        <QueryBoundary
          isPending={circlesQuery.isPending}
          isError={circlesQuery.isError}
          error={circlesQuery.error}
          onRetry={() => circlesQuery.refetch()}
          errorTitle="Couldn't load your circles."
          loadingLabel="Loading your circles"
          skeleton={<SkeletonList rows={3} />}
          isEmpty={circles.length === 0}
          empty={
            <div className="glass rounded-xl p-12 text-center">
              <div className="text-sm text-muted-foreground mb-4">
                You haven't joined any circles yet.
              </div>
              <Link
                to="/groups"
                className={`btn-ember inline-block px-5 py-2 border border-silver/20 rounded-full text-silver text-xs font-mono uppercase tracking-widest ${INTERACTIVE}`}
              >
                Create or join
              </Link>
            </div>
          }
        >
          <div className="grid md:grid-cols-[240px_1fr] gap-8">
            <aside className="space-y-1">
              {circles.map((c) => (
                <button
                  key={c.id}
                  onClick={() => setPicked(c.id)}
                  // aria-current, not just a tint: which circle is showing on
                  // the right was communicated by colour alone.
                  aria-current={active === c.id ? "true" : undefined}
                  className={`w-full text-left px-4 py-3 rounded-lg border transition-colors ${INTERACTIVE_TIGHT} ${
                    active === c.id
                      ? "bg-ember/10 border-ember/40 text-ember"
                      : "border-white/5 hover:bg-white/5"
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <div className="text-sm font-medium truncate">{c.name}</div>
                    {active === c.id && (
                      <BadgeHint tone="accent" title="Currently showing this circle">
                        <span className="sr-only">Selected: </span>
                        Viewing
                      </BadgeHint>
                    )}
                  </div>
                  <div className="text-[10px] font-mono uppercase tracking-widest text-muted-foreground mt-0.5">
                    {c.total_xp.toLocaleString()} XP
                  </div>
                </button>
              ))}
            </aside>

            <section>
              <QueryBoundary
                isPending={detailQuery.isPending}
                isError={detailQuery.isError}
                error={detailQuery.error}
                onRetry={() => detailQuery.refetch()}
                errorTitle="Couldn't load this circle."
                loadingLabel="Loading circle"
                skeleton={<div className="text-sm text-muted-foreground">Loading circle…</div>}
                isEmpty={!detail}
                empty={
                  <div className="text-sm text-muted-foreground">
                    This circle is gone.{" "}
                    <Link
                      to="/groups"
                      className={`text-ember underline underline-offset-2 ${INTERACTIVE_TIGHT}`}
                    >
                      Join another
                    </Link>
                  </div>
                }
              >
                {detail && (
                <>
                  <div className="flex items-baseline justify-between mb-6">
                    <h2 className="text-2xl font-serif">{detail.name}</h2>
                    <div className="text-xs font-mono text-muted-foreground">
                      {detail.member_count} members · {detail.total_xp.toLocaleString()} XP
                    </div>
                  </div>
                  <div className="space-y-2">
                    {detail.members.map((m, i) => (
                      <div
                        key={m.user_id}
                        className="glass rounded-lg px-4 py-3 flex items-center gap-4"
                      >
                        <div className="font-serif text-xl w-8 text-center text-muted-foreground">
                          {i + 1}
                        </div>
                        <div className="relative">
                          {m.avatar_url ? (
                            <img
                              src={m.avatar_url}
                              alt=""
                              className="size-9 rounded-full object-cover"
                            />
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
                          <div className="text-sm font-mono text-ember">
                            {m.weekly_xp.toLocaleString()}
                          </div>
                          <div className="text-[9px] font-mono uppercase tracking-widest text-muted-foreground">
                            Weekly XP
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </>
                )}
              </QueryBoundary>
            </section>
          </div>
        </QueryBoundary>
      </div>
    </div>
  );
}
