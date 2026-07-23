import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { toast } from "sonner";
import { copy } from "@/lib/copy";
import { Nav } from "@/components/nav";
import {
  listFriends,
  searchPeople,
  sendFriendRequest,
  respondFriendRequest,
  removeFriend,
  type FriendRow,
} from "@/lib/friends.functions";

export const Route = createFileRoute("/_authenticated/friends")({
  head: () => ({
    meta: [
      { title: "Friends — Stack'd" },
      { name: "description", content: "Your focus circle. Send and accept ties." },
    ],
  }),
  component: FriendsPage,
});

type Person = { id: string; display_name: string | null; avatar_url: string | null };

function FriendsPage() {
  const list = useServerFn(listFriends);
  const search = useServerFn(searchPeople);
  const send = useServerFn(sendFriendRequest);
  const respond = useServerFn(respondFriendRequest);
  const remove = useServerFn(removeFriend);

  const [rows, setRows] = useState<FriendRow[]>([]);
  const [q, setQ] = useState("");
  const [results, setResults] = useState<Person[]>([]);
  const [busy, setBusy] = useState<string | null>(null);

  const refresh = async () => {
    const r = await list();
    setRows(r.rows);
  };

  useEffect(() => {
    refresh();
  }, []);

  useEffect(() => {
    const t = setTimeout(async () => {
      if (q.trim().length < 1) {
        setResults([]);
        return;
      }
      try {
        const r = await search({ data: { q: q.trim() } });
        setResults(r.rows);
      } catch {
        setResults([]);
      }
    }, 250);
    return () => clearTimeout(t);
  }, [q]);

  const doSend = async (id: string) => {
    setBusy(id);
    try {
      await send({ data: { addresseeId: id } });
      toast.success("Request sent");
      await refresh();
    } catch {
      toast.error("Could not send request");
    } finally {
      setBusy(null);
    }
  };

  const doRespond = async (id: string, accept: boolean) => {
    setBusy(id);
    try {
      await respond({ data: { id, accept } });
      toast.success(accept ? "Tie accepted" : "Request declined");
      await refresh();
    } finally {
      setBusy(null);
    }
  };

  const doRemove = async (id: string) => {
    setBusy(id);
    try {
      await remove({ data: { id } });
      toast(copy.friends.removed);
      await refresh();
    } finally {
      setBusy(null);
    }
  };

  const incoming = rows.filter((r) => r.direction === "incoming");
  const outgoing = rows.filter((r) => r.direction === "outgoing");
  const friends = rows.filter((r) => r.direction === "friend");
  const pendingIds = new Set([...incoming, ...outgoing, ...friends].map((r) => r.user_id));

  return (
    <div className="min-h-screen bg-obsidian text-silver">
      <Nav />
      <main className="max-w-4xl mx-auto px-6 pt-28 pb-24 space-y-12">
        <header>
          <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Circle · 01</p>
          <h1 className="mt-3 text-4xl md:text-5xl font-serif">Friends</h1>
          <p className="mt-3 text-silver-dim max-w-lg">
            Presence is quieter with witnesses. Curate a small, deliberate circle.
          </p>
        </header>

        <section className="space-y-3">
          <label className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">
            Find someone
          </label>
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Display name…"
            className="w-full bg-transparent border border-white/10 focus:border-ember/60 rounded-md px-4 py-3 text-silver placeholder:text-silver-dim/40 outline-none transition-colors"
          />
          {results.length > 0 && (
            <ul className="divide-y divide-white/5 border border-white/10 rounded-md overflow-hidden">
              {results.map((p) => (
                <li key={p.id} className="flex items-center justify-between px-4 py-3">
                  <PersonRow p={p} />
                  {pendingIds.has(p.id) ? (
                    <span className="font-mono text-[10px] tracking-[0.2em] uppercase text-silver-dim">
                      Connected
                    </span>
                  ) : (
                    <button
                      onClick={() => doSend(p.id)}
                      disabled={busy === p.id}
                      className="font-mono text-[10px] tracking-[0.2em] uppercase px-3 py-1.5 border border-ember/40 text-ember hover:bg-ember/10 rounded-full transition-colors disabled:opacity-50"
                    >
                      {busy === p.id ? "…" : "Send tie"}
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </section>

        {incoming.length > 0 && (
          <Section title="Incoming">
            {incoming.map((r) => (
              <li key={r.id} className="flex items-center justify-between px-4 py-3">
                <PersonRow p={{ id: r.user_id, display_name: r.display_name, avatar_url: r.avatar_url }} />
                <div className="flex gap-2">
                  <button
                    onClick={() => doRespond(r.id, true)}
                    disabled={busy === r.id}
                    className="font-mono text-[10px] tracking-[0.2em] uppercase px-3 py-1.5 border border-ember/40 text-ember hover:bg-ember/10 rounded-full transition-colors disabled:opacity-50"
                  >
                    Accept
                  </button>
                  <button
                    onClick={() => doRespond(r.id, false)}
                    disabled={busy === r.id}
                    className="font-mono text-[10px] tracking-[0.2em] uppercase px-3 py-1.5 border border-white/10 text-silver-dim hover:text-silver rounded-full transition-colors disabled:opacity-50"
                  >
                    Decline
                  </button>
                </div>
              </li>
            ))}
          </Section>
        )}

        {outgoing.length > 0 && (
          <Section title="Awaiting">
            {outgoing.map((r) => (
              <li key={r.id} className="flex items-center justify-between px-4 py-3">
                <PersonRow p={{ id: r.user_id, display_name: r.display_name, avatar_url: r.avatar_url }} />
                <button
                  onClick={() => doRemove(r.id)}
                  disabled={busy === r.id}
                  className="font-mono text-[10px] tracking-[0.2em] uppercase text-silver-dim hover:text-silver transition-colors disabled:opacity-50"
                >
                  Cancel
                </button>
              </li>
            ))}
          </Section>
        )}

        <Section title={`Ties (${friends.length})`}>
          {friends.length === 0 ? (
            <li className="px-4 py-6 text-silver-dim/60 text-sm">Your circle is empty. Search above to send a tie.</li>
          ) : (
            friends.map((r) => (
              <li key={r.id} className="flex items-center justify-between px-4 py-3">
                <Link
                  to="/profile/$id"
                  params={{ id: r.user_id }}
                  className="flex-1 min-w-0"
                >
                  <PersonRow p={{ id: r.user_id, display_name: r.display_name, avatar_url: r.avatar_url }} />
                </Link>
                <button
                  onClick={() => doRemove(r.id)}
                  disabled={busy === r.id}
                  className="font-mono text-[10px] tracking-[0.2em] uppercase text-silver-dim hover:text-silver transition-colors disabled:opacity-50"
                >
                  Sever
                </button>
              </li>
            ))
          )}
        </Section>
      </main>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="space-y-3">
      <h2 className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">{title}</h2>
      <ul className="divide-y divide-white/5 border border-white/10 rounded-md overflow-hidden">
        {children}
      </ul>
    </section>
  );
}

function PersonRow({ p }: { p: Person }) {
  const initial = (p.display_name ?? "?").slice(0, 1).toUpperCase();
  return (
    <div className="flex items-center gap-3 min-w-0">
      <div className="size-9 rounded-full border border-white/10 bg-white/5 flex items-center justify-center overflow-hidden">
        {p.avatar_url ? (
          <img src={p.avatar_url} alt="" className="w-full h-full object-cover" />
        ) : (
          <span className="font-mono text-xs text-ember">{initial}</span>
        )}
      </div>
      <span className="truncate text-silver">{p.display_name ?? "Anonymous"}</span>
    </div>
  );
}
