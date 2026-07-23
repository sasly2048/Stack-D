import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { Virtuoso } from "react-virtuoso";
import { Nav } from "@/components/nav";
import { listFeed, friendsPresence, heartbeat, type FeedItem, type PresenceStatus } from "@/lib/social.functions";

export const Route = createFileRoute("/_authenticated/feed")({
  head: () => ({
    meta: [
      { title: "Feed — Stack'd" },
      { name: "description", content: "Signal from your circle. Sessions, unlocks, ties." },
    ],
  }),
  component: FeedPage,
});

function FeedPage() {
  const feed = useServerFn(listFeed);
  const presence = useServerFn(friendsPresence);
  const beat = useServerFn(heartbeat);

  const [rows, setRows] = useState<FeedItem[]>([]);
  const [friends, setFriends] = useState<
    Array<{ id: string; display_name: string | null; avatar_url: string | null; status: PresenceStatus }>
  >([]);

  useEffect(() => {
    const load = async () => {
      const [f, p] = await Promise.all([feed({ data: { limit: 30 } }), presence()]);
      setRows(f.rows);
      setFriends(p.rows);
    };
    load();
    beat().catch(() => undefined);
    const beatIv = setInterval(() => beat().catch(() => undefined), 60_000);
    const refreshIv = setInterval(load, 30_000);
    return () => {
      clearInterval(beatIv);
      clearInterval(refreshIv);
    };
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
          {rows.length === 0 ? (
            <p className="border border-white/10 rounded-md px-4 py-6 text-silver-dim/60">
              No signal yet. Complete a session or add friends.
            </p>
          ) : (
            <Virtuoso
              useWindowScroll
              data={rows}
              itemContent={(_, r) => <div className="pb-2"><FeedRow r={r} /></div>}
            />
          )}
        </section>

        <aside className="space-y-3">
          <h2 className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">
            Circle · {friends.length}
          </h2>
          <ul className="border border-white/10 rounded-md divide-y divide-white/5">
            {friends.length === 0 && (
              <li className="px-4 py-4 text-silver-dim/60 text-sm">No ties yet.</li>
            )}
            {friends.map((f) => (
              <li key={f.id} className="flex items-center gap-3 px-4 py-3">
                <StatusDot status={f.status} />
                <Link
                  to="/profile/$id"
                  params={{ id: f.id }}
                  className="truncate text-sm text-silver hover:text-ember transition-colors"
                >
                  {f.display_name ?? "Anonymous"}
                </Link>
              </li>
            ))}
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
          <Link to="/profile/$id" params={{ id: r.user_id }} className="text-silver hover:text-ember transition-colors">
            {name}
          </Link>{" "}
          <span className="text-silver-dim">{line}</span>
        </p>
        <p className="mt-0.5 font-mono text-[9px] tracking-[0.2em] uppercase text-silver-dim/60">{rel}</p>
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
