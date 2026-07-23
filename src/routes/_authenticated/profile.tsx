import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { toast } from "sonner";
import { Nav } from "@/components/nav";
import { useAuth } from "@/hooks/use-auth";
import { getProfile, updateMyProfile, type PublicProfile } from "@/lib/profile.functions";
import { LowPowerToggle } from "@/components/low-power-toggle";

export const Route = createFileRoute("/_authenticated/profile")({
  head: () => ({
    meta: [
      { title: "Your profile — Stack'd" },
      { name: "description", content: "Your record. Your ties. Your unlocks." },
    ],
  }),
  component: MyProfile,
});

function MyProfile() {
  const { user } = useAuth();
  const fetchProfile = useServerFn(getProfile);
  const save = useServerFn(updateMyProfile);

  const [p, setP] = useState<PublicProfile | null>(null);
  const [name, setName] = useState("");
  const [bio, setBio] = useState("");
  const [avatar, setAvatar] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    (async () => {
      const res = await fetchProfile({ data: {} });
      setP(res);
      setName(res.display_name ?? "");
      setBio(res.bio ?? "");
      setAvatar(res.avatar_url ?? "");
    })();
  }, []);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      await save({ data: { display_name: name, bio, avatar_url: avatar } });
      toast.success("Profile updated");
      const res = await fetchProfile({ data: {} });
      setP(res);
    } catch {
      toast.error("Could not save");
    } finally {
      setSaving(false);
    }
  };

  if (!p || !user) return null;

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
            <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Your record</p>
            <h1 className="mt-1 text-3xl md:text-4xl font-serif">{p.display_name ?? "Anonymous"}</h1>
            <p className="text-silver-dim/60 text-xs font-mono uppercase tracking-widest mt-1">{user.email}</p>
          </div>
        </header>

        <section className="grid grid-cols-2 md:grid-cols-4 gap-3">
          <Stat label="Lifetime XP" value={p.lifetime_xp.toLocaleString()} />
          <Stat label="Hours held" value={hours.toString()} />
          <Stat label="Sessions" value={p.session_count.toString()} />
          <Stat label="Best streak" value={p.best_streak.toString()} />
        </section>

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
          <Field label="Avatar URL">
            <input
              value={avatar}
              onChange={(e) => setAvatar(e.target.value)}
              placeholder="https://…"
              className="w-full bg-transparent border border-white/10 focus:border-ember/60 rounded-md px-4 py-3 outline-none transition-colors"
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
          <h2 className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">Settings</h2>
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
      <span className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">{label}</span>
      {children}
    </label>
  );
}
