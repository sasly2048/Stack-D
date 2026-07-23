import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { toast } from "sonner";
import { Nav } from "@/components/nav";
import { listPartners, pairPartner, endPartnership, type Partner } from "@/lib/mentor.functions";
import { searchPeople } from "@/lib/friends.functions";

export const Route = createFileRoute("/_authenticated/partners")({
  head: () => ({ meta: [{ title: "Partners — Stack'd" }] }),
  component: PartnersPage,
});

function PartnersPage() {
  const list = useServerFn(listPartners);
  const pair = useServerFn(pairPartner);
  const end = useServerFn(endPartnership);
  const search = useServerFn(searchPeople);

  const [rows, setRows] = useState<Partner[]>([]);
  const [q, setQ] = useState("");
  const [results, setResults] = useState<Array<{ id: string; display_name: string | null }>>([]);
  const [busy, setBusy] = useState(false);

  const refresh = async () => {
    const r = await list();
    setRows(r.rows);
  };
  useEffect(() => { refresh().catch(() => {}); }, []);
  useEffect(() => {
    if (!q.trim()) { setResults([]); return; }
    const t = setTimeout(async () => {
      try {
        const r = await search({ data: { query: q } });
        setResults(r.rows ?? []);
      } catch { setResults([]); }
    }, 200);
    return () => clearTimeout(t);
  }, [q]);

  const invite = async (partnerId: string, asRole: "mentor" | "mentee") => {
    setBusy(true);
    try {
      await pair({ data: { partnerId, asRole } });
      toast.success("Partnership formed");
      setQ(""); setResults([]);
      await refresh();
    } catch (e) {
      toast.error("Failed to pair", { description: String((e as Error).message) });
    } finally { setBusy(false); }
  };

  const drop = async (id: string) => {
    setBusy(true);
    try { await end({ data: { relationshipId: id } }); await refresh(); }
    finally { setBusy(false); }
  };

  return (
    <div className="min-h-screen bg-obsidian text-silver">
      <Nav />
      <main className="max-w-3xl mx-auto px-6 pt-28 pb-24 space-y-10">
        <header>
          <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Accountability</p>
          <h1 className="mt-3 text-4xl md:text-5xl font-serif">Partners</h1>
          <p className="mt-3 text-silver-dim max-w-lg">
            Pair with a mentor or mentee. Keep each other honest.
          </p>
        </header>

        <section className="space-y-3">
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Find someone…"
            className="w-full bg-transparent border border-white/10 focus:border-ember/60 rounded-md px-4 py-3 outline-none"
          />
          {results.length > 0 && (
            <ul className="divide-y divide-white/5 border border-white/10 rounded-md">
              {results.map((p) => (
                <li key={p.id} className="flex items-center justify-between px-4 py-3">
                  <span className="text-sm">{p.display_name ?? "Anon"}</span>
                  <div className="flex gap-2">
                    <button disabled={busy} onClick={() => invite(p.id, "mentor")}
                      className="font-mono text-[10px] tracking-[0.2em] uppercase px-3 py-1.5 border border-ember/40 text-ember hover:bg-ember/10 rounded-full disabled:opacity-50">
                      Be Mentor
                    </button>
                    <button disabled={busy} onClick={() => invite(p.id, "mentee")}
                      className="font-mono text-[10px] tracking-[0.2em] uppercase px-3 py-1.5 border border-white/20 text-silver hover:bg-white/5 rounded-full disabled:opacity-50">
                      Be Mentee
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="space-y-3">
          <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">Active</p>
          {rows.length === 0 && <p className="text-sm text-silver-dim">No partners yet.</p>}
          <ul className="space-y-2">
            {rows.map((r) => (
              <li key={r.relationship_id} className="glass rounded-md px-4 py-3 flex items-center justify-between">
                <div>
                  <p className="text-sm">{r.display_name ?? "Anon"}</p>
                  <p className="text-[10px] font-mono uppercase tracking-widest text-silver-dim">{r.role} · {r.status}</p>
                </div>
                <button disabled={busy} onClick={() => drop(r.relationship_id)}
                  className="font-mono text-[10px] tracking-[0.2em] uppercase px-3 py-1.5 border border-white/10 text-silver-dim hover:text-breach rounded-full disabled:opacity-50">
                  End
                </button>
              </li>
            ))}
          </ul>
        </section>
      </main>
    </div>
  );
}
