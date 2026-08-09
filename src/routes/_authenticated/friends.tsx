import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useRef, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { toast } from "sonner";
import { copy } from "@/lib/copy";
import { Nav } from "@/components/nav";
import { QueryBoundary, SkeletonList } from "@/components/query-states";
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
      { name: "description", content: "Build your Stack'd focus circle: send ties, accept requests and keep each other accountable in real time." },
      { property: "og:title", content: "Friends — Stack'd" },
      { property: "og:description", content: "Build your Stack'd focus circle: send ties, accept requests and keep each other accountable in real time." },
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

  const queryClient = useQueryClient();
  const [q, setQ] = useState("");
  const [debouncedQ, setDebouncedQ] = useState("");
  const searchRef = useRef<HTMLInputElement>(null);

  const friendsQuery = useQuery({
    queryKey: ["friends"],
    queryFn: async () => (await list()).rows,
  });
  const rows: FriendRow[] = friendsQuery.data ?? [];

  // Debounce in state, then let the query key drive the fetch. react-query
  // dedupes and cancels in-flight requests per key, so a fast typist no longer
  // races results — the old version could render a stale query's rows.
  useEffect(() => {
    const t = setTimeout(() => setDebouncedQ(q.trim()), 250);
    return () => clearTimeout(t);
  }, [q]);

  const searchQuery = useQuery({
    queryKey: ["people-search", debouncedQ],
    queryFn: async () => (await search({ data: { q: debouncedQ } })).rows,
    enabled: debouncedQ.length > 0,
  });
  const results: Person[] = debouncedQ.length > 0 ? (searchQuery.data ?? []) : [];
  // In flight covers both the debounce window (typed but not yet dispatched)
  // and the request itself, so the indicator never blinks out mid-search.
  const searching = q.trim().length > 0 && (q.trim() !== debouncedQ || searchQuery.isFetching);
  const noResults = !searching && debouncedQ.length > 0 && searchQuery.isSuccess && results.length === 0;

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["friends"] });

  const sendMutation = useMutation({
    mutationFn: (addresseeId: string) => send({ data: { addresseeId } }),
    onSuccess: () => {
      toast.success("Request sent");
      invalidate();
    },
    onError: () => toast.error("Could not send request"),
  });

  const respondMutation = useMutation({
    mutationFn: ({ id, accept }: { id: string; accept: boolean }) => respond({ data: { id, accept } }),
    onSuccess: (_r, { accept }) => {
      toast.success(accept ? "Tie accepted" : "Request declined");
      invalidate();
    },
    // Previously this had no catch at all, so a failed accept looked like a
    // no-op and the row stayed put with no explanation.
    onError: () => toast.error("Could not update that request"),
  });

  const removeMutation = useMutation({
    mutationFn: (id: string) => remove({ data: { id } }),
    onSuccess: () => {
      toast(copy.friends.removed);
      invalidate();
    },
    onError: () => toast.error("Could not remove that tie"),
  });

  // Which row is mid-flight, so only that row's buttons disable.
  const busy =
    (sendMutation.isPending ? sendMutation.variables : null) ??
    (respondMutation.isPending ? respondMutation.variables?.id : null) ??
    (removeMutation.isPending ? removeMutation.variables : null) ??
    null;

  const doSend = (id: string) => sendMutation.mutate(id);
  const doRespond = (id: string, accept: boolean) => respondMutation.mutate({ id, accept });
  const doRemove = (id: string) => removeMutation.mutate(id);

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
          <label
            htmlFor="friend-search"
            className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim"
          >
            Find someone
          </label>
          <div className="relative">
            <input
              id="friend-search"
              ref={searchRef}
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Display name…"
              aria-describedby="search-status"
              className="w-full bg-transparent border border-white/10 focus:border-ember/60 rounded-md px-4 py-3 text-silver placeholder:text-silver-dim/40 outline-none transition-colors pr-20"
            />
            {/* The debounce means a keystroke buys 250ms of apparent nothing.
                Without a spinner that reads as "search is broken". */}
            {searching && (
              <span
                aria-hidden="true"
                className="absolute right-11 top-1/2 -translate-y-1/2 size-3.5 rounded-full border-2 border-ember/30 border-t-ember animate-spin"
              />
            )}
            {q.length > 0 && (
              <button
                type="button"
                onClick={() => setQ("")}
                aria-label="Clear search"
                className="absolute right-3 top-1/2 -translate-y-1/2 cursor-pointer rounded-full px-2 py-1 font-mono text-xs text-silver-dim hover:text-silver hover:bg-white/5 active:scale-[0.99] transition-all duration-200 ease-[var(--ease-ritual)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember"
              >
                ×
              </button>
            )}
          </div>
          {/* "Nothing matched Ada" and "start typing" are different situations;
              collapsing them into one blank panel told neither story. */}
          <p id="search-status" role="status" aria-live="polite" className="sr-only">
            {searching
              ? "Searching"
              : noResults
                ? `No results for ${debouncedQ}`
                : results.length > 0
                  ? `${results.length} result${results.length === 1 ? "" : "s"}`
                  : ""}
          </p>
          {noResults && (
            <p className="border border-white/10 rounded-md px-4 py-6 text-sm text-silver-dim/60">
              No one matches “{debouncedQ}”. Check the spelling, or ask them for their display
              name.
            </p>
          )}
          {results.length > 0 && (
            <ul className="divide-y divide-white/5 border border-white/10 rounded-md overflow-hidden">
              {results.map((p) => (
                <li
                  key={p.id}
                  className="flex items-center justify-between px-4 py-3 hover:bg-white/[0.03] transition-all duration-200 ease-[var(--ease-ritual)]"
                >
                  <PersonRow p={p} />
                  {pendingIds.has(p.id) ? (
                    <span className="font-mono text-[10px] tracking-[0.2em] uppercase text-silver-dim">
                      Connected
                    </span>
                  ) : (
                    <button
                      onClick={() => doSend(p.id)}
                      disabled={busy === p.id}
                      className="font-mono text-[10px] tracking-[0.2em] uppercase px-3 py-1.5 border border-ember/40 text-ember hover:bg-ember/10 rounded-full transition-colors disabled:opacity-50 active:scale-[0.99] transition-all duration-200 ease-[var(--ease-ritual)] disabled:cursor-not-allowed focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember"
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
              <li
                key={r.id}
                className="flex items-center justify-between px-4 py-3 hover:bg-white/[0.03] transition-all duration-200 ease-[var(--ease-ritual)]"
              >
                <PersonRow
                  p={{ id: r.user_id, display_name: r.display_name, avatar_url: r.avatar_url }}
                />
                <div className="flex gap-2">
                  <button
                    onClick={() => doRespond(r.id, true)}
                    disabled={busy === r.id}
                    className="font-mono text-[10px] tracking-[0.2em] uppercase px-3 py-1.5 border border-ember/40 text-ember hover:bg-ember/10 rounded-full transition-colors disabled:opacity-50 active:scale-[0.99] transition-all duration-200 ease-[var(--ease-ritual)] disabled:cursor-not-allowed focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember"
                  >
                    Accept
                  </button>
                  <button
                    onClick={() => doRespond(r.id, false)}
                    disabled={busy === r.id}
                    className="font-mono text-[10px] tracking-[0.2em] uppercase px-3 py-1.5 border border-white/10 text-silver-dim hover:text-silver rounded-full transition-colors disabled:opacity-50 active:scale-[0.99] transition-all duration-200 ease-[var(--ease-ritual)] disabled:cursor-not-allowed focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember"
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
              <li
                key={r.id}
                className="flex items-center justify-between px-4 py-3 hover:bg-white/[0.03] transition-all duration-200 ease-[var(--ease-ritual)]"
              >
                <PersonRow
                  p={{ id: r.user_id, display_name: r.display_name, avatar_url: r.avatar_url }}
                />
                <button
                  onClick={() => doRemove(r.id)}
                  disabled={busy === r.id}
                  className="font-mono text-[10px] tracking-[0.2em] uppercase text-silver-dim hover:text-silver transition-colors disabled:opacity-50 active:scale-[0.99] transition-all duration-200 ease-[var(--ease-ritual)] disabled:cursor-not-allowed rounded focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember"
                >
                  Cancel
                </button>
              </li>
            ))}
          </Section>
        )}

        <Section title={friendsQuery.isPending ? "Ties" : `Ties (${friends.length})`}>
          {/* Loading is checked before emptiness. This screen used to render
              "Your circle is empty" while the list was still in flight, so a
              user with fifty ties was told they had none on every slow load. */}
          <QueryBoundary
            isPending={friendsQuery.isPending}
            isError={friendsQuery.isError}
            error={friendsQuery.error}
            onRetry={() => friendsQuery.refetch()}
            errorTitle="Couldn't load your circle."
            loadingLabel="Loading your circle"
            skeleton={<SkeletonList rows={3} className="px-4 py-3" />}
            isEmpty={friends.length === 0}
            empty={
              <li className="px-4 py-6 text-silver-dim/60 text-sm">
                Your circle is empty. Search above to send a tie.
                {/* An empty state that only describes the emptiness leaves the
                    user to find the fix themselves. */}
                <button
                  type="button"
                  onClick={() => searchRef.current?.focus()}
                  className="ml-2 cursor-pointer rounded font-mono text-[10px] tracking-[0.2em] uppercase text-ember hover:text-silver active:scale-[0.99] transition-all duration-200 ease-[var(--ease-ritual)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember"
                >
                  Find someone →
                </button>
              </li>
            }
          >
            {friends.map((r) => (
              <li
                key={r.id}
                className="flex items-center justify-between px-4 py-3 hover:bg-white/[0.03] transition-all duration-200 ease-[var(--ease-ritual)]"
              >
                <Link
                  to="/profile/$id"
                  params={{ id: r.user_id }}
                  className="flex-1 min-w-0 rounded focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember"
                >
                  <PersonRow
                    p={{ id: r.user_id, display_name: r.display_name, avatar_url: r.avatar_url }}
                  />
                </Link>
                <button
                  onClick={() => doRemove(r.id)}
                  disabled={busy === r.id}
                  className="font-mono text-[10px] tracking-[0.2em] uppercase text-silver-dim hover:text-silver transition-colors disabled:opacity-50 active:scale-[0.99] transition-all duration-200 ease-[var(--ease-ritual)] disabled:cursor-not-allowed rounded focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember"
                >
                  Sever
                </button>
              </li>
            ))}
          </QueryBoundary>
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
