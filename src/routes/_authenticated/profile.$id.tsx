import { createFileRoute, useParams } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { toast } from "sonner";
import { copy } from "@/lib/copy";
import { formatHandle } from "@/lib/handle";
import { Nav } from "@/components/nav";
import { getProfile, type PublicProfile } from "@/lib/profile.functions";
import { sendFriendRequest, respondFriendRequest, removeFriend } from "@/lib/friends.functions";
import { useAuth } from "@/hooks/use-auth";
import { Button } from "@/components/ui/button";
import { ErrorPanel, Skeleton } from "@/components/query-states";

export const Route = createFileRoute("/_authenticated/profile/$id")({
  head: () => ({
    meta: [
      { title: "Profile — Stack'd" },
      {
        name: "description",
        content:
          "A witness's focus record on Stack'd: sessions held, streaks kept, titles earned and ties formed.",
      },
      { property: "og:title", content: "Profile — Stack'd" },
      {
        property: "og:description",
        content:
          "A witness's focus record on Stack'd: sessions held, streaks kept, titles earned and ties formed.",
      },
    ],
  }),
  component: PublicProfileView,
});

function PublicProfileView() {
  const { id } = useParams({ from: "/_authenticated/profile/$id" });
  const { user } = useAuth();
  const fetchProfile = useServerFn(getProfile);
  const send = useServerFn(sendFriendRequest);
  const respond = useServerFn(respondFriendRequest);
  const remove = useServerFn(removeFriend);

  const [p, setP] = useState<PublicProfile | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = async () => {
    setError(null);
    setLoading(true);
    try {
      const res = await fetchProfile({ data: { userId: id } });
      if (!res) throw new Error("This profile could not be found.");
      setP(res);
    } catch (cause) {
      setP(null);
      setError(cause instanceof Error ? cause.message : "This profile could not be found.");
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => {
    // Wait for the Supabase session, else the serverFn call has no bearer token.
    if (!user?.id) return;
    void refresh();
  }, [id, user?.id]);

  if (loading || !user?.id) {
    return (
      <div className="min-h-screen bg-obsidian text-silver">
        <Nav />
        <main className="app-page max-w-4xl" aria-busy="true" aria-label="Loading profile">
          <div className="flex items-center gap-5">
            <Skeleton className="size-20 shrink-0 rounded-full sm:size-24" />
            <div className="w-full max-w-sm space-y-3">
              <Skeleton className="h-4 w-24" />
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-4 w-40" />
            </div>
          </div>
        </main>
      </div>
    );
  }

  if (error || !p) {
    return (
      <div className="min-h-screen bg-obsidian text-silver">
        <Nav />
        <main className="app-page max-w-3xl">
          <ErrorPanel
            title="Profile unavailable"
            message={error ?? "This profile could not be found."}
            onRetry={() => void refresh()}
          />
        </main>
      </div>
    );
  }

  const hours = Math.floor(p.total_focus_seconds / 3600);
  const initial = (p.display_name ?? "?").slice(0, 1).toUpperCase();

  const action = async () => {
    setBusy(true);
    try {
      if (!p.friendship) {
        await send({ data: { addresseeId: p.id } });
        toast.success("Request sent");
      } else if (p.friendship.direction === "incoming") {
        await respond({ data: { id: p.friendship.id, accept: true } });
        toast.success("Tie accepted");
      } else {
        await remove({ data: { id: p.friendship.id } });
        toast(copy.friends.removed);
      }
      await refresh();
    } catch {
      toast.error("Action failed");
    } finally {
      setBusy(false);
    }
  };

  const buttonLabel = !p.friendship
    ? "Send tie"
    : p.friendship.direction === "friend"
      ? "Sever tie"
      : p.friendship.direction === "incoming"
        ? "Accept tie"
        : "Awaiting…";

  return (
    <div className="min-h-screen bg-obsidian text-silver">
      <Nav />
      <main className="app-page max-w-4xl space-y-10 sm:space-y-12">
        <header className="grid grid-cols-[auto_minmax(0,1fr)] items-start gap-4 sm:grid-cols-[auto_minmax(0,1fr)_auto] sm:gap-6">
          <div className="flex size-20 shrink-0 items-center justify-center overflow-hidden rounded-full border border-ember/30 bg-white/5 sm:size-24">
            {p.avatar_url ? (
              <img src={p.avatar_url} alt="" className="w-full h-full object-cover" />
            ) : (
              <span className="font-serif text-4xl text-ember">{initial}</span>
            )}
          </div>
          <div className="flex-1 min-w-0">
            <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Witness</p>
            <h1 className="mt-2 text-3xl md:text-4xl font-serif truncate">
              {p.display_name ?? "Anonymous"}
            </h1>
            <p className="mt-1 font-mono text-sm text-silver-dim">
              {formatHandle(p.id, p.display_name)}
            </p>
            {p.bio && <p className="mt-2 text-silver-dim">{p.bio}</p>}
            <p className="mt-2 font-mono text-[10px] tracking-[0.2em] uppercase text-silver-dim/60">
              Joined{" "}
              {new Date(p.created_at).toLocaleDateString(undefined, {
                month: "short",
                year: "numeric",
              })}
            </p>
          </div>
          <Button
            onClick={action}
            disabled={busy || p.friendship?.direction === "outgoing"}
            variant="outline"
            className="col-span-2 w-full border-ember/40 font-mono text-[10px] uppercase tracking-[0.2em] text-ember hover:bg-ember/10 sm:col-span-1 sm:w-auto"
          >
            {busy ? "…" : buttonLabel}
          </Button>
        </header>

        <section className="grid grid-cols-2 md:grid-cols-4 gap-3">
          <Stat label="Lifetime XP" value={p.lifetime_xp.toLocaleString()} />
          <Stat label="Hours held" value={hours.toString()} />
          <Stat label="Sessions" value={p.session_count.toString()} />
          <Stat label="Best streak" value={p.best_streak.toString()} />
        </section>

        <section className="space-y-3">
          <h2 className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">
            Achievements · {p.achievements.length}
          </h2>
          {p.achievements.length === 0 ? (
            <p className="text-silver-dim/60 text-sm border border-white/10 rounded-md px-4 py-6">
              No unlocks yet.
            </p>
          ) : (
            <ul className="grid grid-cols-2 md:grid-cols-3 gap-3">
              {p.achievements.map((a) => (
                <li
                  key={a.id}
                  className="border border-white/10 rounded-md px-4 py-3 hover:border-ember/30 transition-colors"
                >
                  <p className="font-mono text-[9px] tracking-[0.25em] uppercase text-ember">
                    {a.tier}
                  </p>
                  <p className="mt-1 text-silver">{a.name}</p>
                </li>
              ))}
            </ul>
          )}
        </section>
      </main>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="border border-white/10 rounded-md px-4 py-4">
      <p className="font-mono text-[9px] tracking-[0.25em] uppercase text-silver-dim">{label}</p>
      <p className="mt-1 text-2xl font-serif text-silver">{value}</p>
    </div>
  );
}
