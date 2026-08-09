import { useQuery } from "@tanstack/react-query";
import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect } from "react";
import { useServerFn } from "@tanstack/react-start";
import { Virtuoso } from "react-virtuoso";
import { Nav } from "@/components/nav";
import { QueryBoundary, SkeletonList } from "@/components/query-states";
import {
  listFeed,
  friendsPresence,
  heartbeat,
  type FeedItem,
  type PresenceStatus,
} from "@/lib/social.functions";

export const Route = createFileRoute("/_authenticated/feed")({
  head: () => ({
    meta: [
      { title: "Feed — Stack'd" },
      { name: "description", content: "Signal from your circle: live sessions, fresh unlocks and new ties across everyone you focus with." },
      { property: "og:title", content: "Feed — Stack'd" },
      { property: "og:description", content: "Signal from your circle: live sessions, fresh unlocks and new ties across everyone you focus with." },
    ],
  }),
  component: FeedPage,
});

function FeedPage() {
  const feed = useServerFn(listFeed);
  const presence = useServerFn(friendsPresence);
  const beat = useServerFn(heartbeat);

  // The old setInterval(load, 30s) polled forever, even in a background tab,
  // and its promise had no catch — a single failed refresh was an unhandled
  // rejection. refetchInterval retries under react-query's error handling and,
  // because refetchIntervalInBackground defaults to false, stops while hidden.
  const feedQuery = useQuery({
    queryKey: ["feed"],
    queryFn: async () => {
      const [f, p] = await Promise.all([feed({ data: { limit: 30 } }), presence()]);
      return { rows: f.rows, friends: p.rows };
    },
    refetchInterval: 30_000,
  });

  const rows: FeedItem[] = feedQuery.data?.rows ?? [];
  const friends: Array<{
    id: string;
    display_name: string | null;
    avatar_url: string | null;
    status: PresenceStatus;
  }> = feedQuery.data?.friends ?? [];

  // Presence heartbeat is fire-and-forget, not data this screen renders, so it
  // stays a plain interval — but every call needs its own catch or a dropped
  // network beat becomes an unhandled rejection.
  useEffect(() => {
    beat().catch(() => undefined);
    const beatIv = setInterval(() => beat().catch(() => undefined), 60_000);
    return () => clearInterval(beatIv);
  }, []);

  return (
    <div className="min-h-screen bg-obsidian text-silver">
      <Nav />
      <main className="max-w-5xl mx-auto px-6 pt-28 pb-24 grid gap-10 md:grid-cols-[1fr_260px]">
        <section className="space-y-6">
          <header>
            <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Signal</p>
            <h1 className="mt-3 text-4xl md:text-5xl font-serif">Feed</h1>
          </header>
          {/* Loading is checked before emptiness. This screen used to render
              "No signal yet" during the very first fetch, so an active circle
              was told nothing was happening on every slow load. */}
          <QueryBoundary
            isPending={feedQuery.isPending}
            isError={feedQuery.isError}
            error={feedQuery.error}
            onRetry={() => feedQuery.refetch()}
            errorTitle="Couldn't load your feed."
            loadingLabel="Loading your feed"
            skeleton={<SkeletonList rows={4} />}
            isEmpty={rows.length === 0}
            empty={
              /* An empty state that only states the problem is a dead end —
                 both routes out of it are one tap away, so offer them. */
              <div className="border border-white/10 rounded-md px-4 py-8 text-center">
                <p className="text-silver-dim/60">No signal yet. Complete a session or add friends.</p>
                <div className="mt-5 flex flex-wrap items-center justify-center gap-3">
                  <Link
                    to="/start"
                    className="cursor-pointer rounded-full border border-ember/50 px-5 py-2 font-mono text-[10px] uppercase tracking-widest text-ember transition-all duration-200 ease-[var(--ease-ritual)] hover:bg-ember/10 active:scale-[0.99] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember"
                  >
                    Start a session
                  </Link>
                  <Link
                    to="/friends"
                    className="cursor-pointer rounded-full border border-white/15 px-5 py-2 font-mono text-[10px] uppercase tracking-widest text-silver-dim transition-all duration-200 ease-[var(--ease-ritual)] hover:border-white/30 hover:text-silver active:scale-[0.99] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember"
                  >
                    Find friends
                  </Link>
                </div>
              </div>
            }
          >
            <Virtuoso
              useWindowScroll
              data={rows}
              itemContent={(_, r) => (
                <div className="pb-2">
                  <FeedRow r={r} />
                </div>
              )}
            />
          </QueryBoundary>
        </section>

        <aside className="space-y-3">
          <h2 className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">
            {feedQuery.isPending ? "Circle" : `Circle · ${friends.length}`}
          </h2>
          <ul className="border border-white/10 rounded-md divide-y divide-white/5">
            {/* Same query, same rule: "No ties yet." only after the fetch
                settles, never as a stand-in for "still loading". */}
            <QueryBoundary
              isPending={feedQuery.isPending}
              isError={feedQuery.isError}
              error={feedQuery.error}
              onRetry={() => feedQuery.refetch()}
              errorTitle="Couldn't load your circle."
              loadingLabel="Loading your circle"
              skeleton={<SkeletonList rows={3} className="px-4 py-3" />}
              isEmpty={friends.length === 0}
              empty={
                <li className="px-4 py-4 text-sm">
                  <span className="text-silver-dim/60">No ties yet.</span>{" "}
                  <Link
                    to="/friends"
                    className="cursor-pointer rounded text-ember underline underline-offset-4 transition-colors hover:text-ember-glow focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember"
                  >
                    Find someone
                  </Link>
                </li>
              }
            >
              {friends.map((f) => (
                // The whole row is the target, not just the name — a 3-word
                // link in a 260px row is a needlessly small tap area.
                <li key={f.id}>
                  <Link
                    to="/profile/$id"
                    params={{ id: f.id }}
                    className="flex items-center gap-3 px-4 py-3 transition-all duration-200 ease-[var(--ease-ritual)] hover:bg-white/[0.04] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember focus-visible:ring-inset"
                  >
                    <StatusDot status={f.status} />
                    <span className="truncate text-sm text-silver">
                      {f.display_name ?? "Anonymous"}
                    </span>
                  </Link>
                </li>
              ))}
            </QueryBoundary>
          </ul>
        </aside>
      </main>
    </div>
  );
}

function StatusDot({ status }: { status: PresenceStatus }) {
  const cls =
    status === "focusing"
      ? "bg-ember shadow-[0_0_8px_theme(colors.ember)]"
      : status === "idle"
        ? "bg-silver-dim"
        : "bg-white/10";
  return <span className={`size-2 rounded-full shrink-0 ${cls}`} title={status} />;
}

function FeedRow({ r }: { r: FeedItem }) {
  const name = r.display_name ?? "Anonymous";
  const initial = name.slice(0, 1).toUpperCase();
  const line = describe(r);
  const time = new Date(r.created_at);
  const rel = timeAgo(time);
  return (
    <li className="flex items-start gap-3 border border-white/10 rounded-md px-4 py-3">
      <Link
        to="/profile/$id"
        params={{ id: r.user_id }}
        className="size-9 rounded-full border border-white/10 bg-white/5 flex items-center justify-center shrink-0"
      >
        {r.avatar_url ? (
          <img src={r.avatar_url} alt="" className="w-full h-full object-cover rounded-full" />
        ) : (
          <span className="font-mono text-xs text-ember">{initial}</span>
        )}
      </Link>
      <div className="min-w-0 flex-1">
        <p className="text-sm">
          <Link
            to="/profile/$id"
            params={{ id: r.user_id }}
            className="text-silver hover:text-ember transition-colors"
          >
            {name}
          </Link>{" "}
          <span className="text-silver-dim">{line}</span>
        </p>
        <p className="mt-0.5 font-mono text-[9px] tracking-[0.2em] uppercase text-silver-dim/60">
          {rel}
        </p>
      </div>
    </li>
  );
}

function describe(r: FeedItem) {
  switch (r.kind) {
    case "session_complete": {
      const t = (r.payload.tier as string) ?? "";
      const mins = Math.round(((r.payload.duration_seconds as number) ?? 0) / 60);
      return `completed a ${mins}-minute session · ${t}`;
    }
    case "achievement_unlock":
      return `unlocked ${r.payload.id ?? "an achievement"}`;
    case "challenge_complete":
      return `finished the ${r.payload.name ?? "challenge"} rite`;
    case "friend_add":
      return "formed a new tie";
  }
}
function timeAgo(d: Date) {
  const s = (Date.now() - d.getTime()) / 1000;
  if (s < 60) return "just now";
  if (s < 3600) return `${Math.floor(s / 60)}m ago`;
  if (s < 86400) return `${Math.floor(s / 3600)}h ago`;
  return `${Math.floor(s / 86400)}d ago`;
}
