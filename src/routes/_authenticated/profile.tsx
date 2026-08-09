import { MilestoneShelf } from "@/components/profile/milestone-shelf";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useRef, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { toast } from "sonner";
import { Nav } from "@/components/nav";
import { QueryBoundary, Skeleton, SkeletonCards } from "@/components/query-states";
import { useAuth } from "@/hooks/use-auth";
import { getProfile, updateMyProfile, type PublicProfile } from "@/lib/profile.functions";
import { LowPowerToggle } from "@/components/low-power-toggle";
import { formatHandle } from "@/lib/handle";
import { useXpSync } from "@/lib/xp-sync";

export const Route = createFileRoute("/_authenticated/profile")({
  head: () => ({
    meta: [
      { title: "Your profile — Stack'd" },
      { name: "description", content: "Your Stack'd record: lifetime XP, streaks, unlocks and the ties you keep with your focus circle." },
      { property: "og:title", content: "Your profile — Stack'd" },
      { property: "og:description", content: "Your Stack'd record: lifetime XP, streaks, unlocks and the ties you keep with your focus circle." },
    ],
  }),
  component: MyProfile,
});

function MyProfile() {
  const { user } = useAuth();
  const fetchProfile = useServerFn(getProfile);
  const save = useServerFn(updateMyProfile);

  const queryClient = useQueryClient();
  const [name, setName] = useState("");
  const [bio, setBio] = useState("");
  // Only seed the form from the server once; re-seeding on every refetch would
  // wipe whatever the user is mid-way through typing.
  const seeded = useRef(false);

  const profileQuery = useQuery({
    queryKey: ["my-profile"],
    queryFn: () => fetchProfile({ data: {} }) as Promise<PublicProfile>,
  });
  const p = profileQuery.data;

  useEffect(() => {
    if (!p || seeded.current) return;
    seeded.current = true;
    setName(p.display_name ?? "");
    setBio(p.bio ?? "");
  }, [p]);

  useXpSync(() => {
    void queryClient.invalidateQueries({ queryKey: ["my-profile"] });
  });

  const saveMutation = useMutation({
    mutationFn: () => save({ data: { display_name: name, bio } }),
    onSuccess: () => {
      toast.success("Profile updated");
      queryClient.invalidateQueries({ queryKey: ["my-profile"] });
    },
    onError: () => toast.error("Could not save"),
  });
  const saving = saveMutation.isPending;

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    saveMutation.mutate();
  };

  // This used to be `if (!p || !user) return null`, i.e. a blank obsidian page
  // on every visit until the fetch landed, and permanently if it failed.
  if (!p || !user) {
    return (
      <div className="min-h-screen bg-obsidian text-silver">
        <Nav />
        <main className="mx-auto max-w-3xl px-6 pt-28">
          <QueryBoundary
            isPending={profileQuery.isPending || (!p && !profileQuery.isError)}
            isError={profileQuery.isError}
            error={profileQuery.error}
            onRetry={() => profileQuery.refetch()}
            errorTitle="Couldn't load your profile."
            loadingLabel="Loading your profile"
            skeleton={
              <div className="space-y-6">
                <Skeleton className="size-20 rounded-full" />
                <Skeleton className="h-8 w-48" />
                <SkeletonCards count={3} />
              </div>
            }
          >
            {null}
          </QueryBoundary>
        </main>
      </div>
    );
  }

  const hours = Math.floor(p.total_focus_seconds / 3600);
  const initial = (p.display_name ?? "?").slice(0, 1).toUpperCase();

  return (
    <div className="min-h-screen bg-obsidian text-silver">
      <Nav />
      <main className="max-w-3xl mx-auto px-6 pt-28 pb-24 space-y-12">
        <header className="flex items-center gap-6">
          <div className="size-20 rounded-full border border-ember/30 bg-white/5 flex items-center justify-center overflow-hidden">
            {p.avatar_url ? (
              <img src={p.avatar_url} alt="" className="w-full h-full object-cover" />
            ) : (
              <span className="font-serif text-3xl text-ember">{initial}</span>
            )}
          </div>
          <div>
            <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">
              Your record
            </p>
            <h1 className="mt-1 text-3xl md:text-4xl font-serif">
              {p.display_name ?? "Anonymous"}
            </h1>
            {p.productivity_dna && (
              <p className="mt-1 font-mono text-[11px] tracking-[0.25em] uppercase text-ember">
                {p.productivity_dna}
              </p>
            )}
            <p className="text-silver-dim text-sm font-mono mt-1">
              {formatHandle(p.id, p.display_name)}
            </p>
          </div>
        </header>

        <section className="grid grid-cols-2 md:grid-cols-4 gap-3">
          <Stat label="Lifetime XP" value={p.lifetime_xp.toLocaleString()} />
          <Stat label="Hours held" value={hours.toString()} />
          <Stat label="Sessions" value={p.session_count.toString()} />
          <Stat label="Best streak" value={p.best_streak.toString()} />
        </section>

        <MilestoneShelf />

        <form onSubmit={submit} className="space-y-4">
          <h2 className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">Edit</h2>
          <Field label="Display name">
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              maxLength={40}
              className="w-full bg-transparent border border-white/10 focus:border-ember/60 rounded-md px-4 py-3 outline-none transition-colors"
            />
          </Field>
          <Field label="Bio">
            <textarea
              value={bio}
              onChange={(e) => setBio(e.target.value)}
              maxLength={280}
              rows={3}
              className="w-full bg-transparent border border-white/10 focus:border-ember/60 rounded-md px-4 py-3 outline-none transition-colors resize-none"
            />
          </Field>
          <button
            type="submit"
            disabled={saving}
            className="font-mono text-[10px] tracking-[0.3em] uppercase px-5 py-2.5 border border-ember/40 text-ember hover:bg-ember/10 rounded-full transition-colors disabled:opacity-50"
          >
            {saving ? "Sealing…" : "Save"}
          </button>
        </form>

        <section className="space-y-3">
          <h2 className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">
            Settings
          </h2>
          <LowPowerToggle />
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

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block space-y-2">
      <span className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">
        {label}
      </span>
      {children}
    </label>
  );
}
