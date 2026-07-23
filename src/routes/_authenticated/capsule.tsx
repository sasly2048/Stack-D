import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { toast } from "sonner";
import { Nav } from "@/components/nav";
import { listCapsules, writeCapsule, openCapsule, type Capsule } from "@/lib/capsules.functions";
import { EmptyState } from "@/components/empty-state";

export const Route = createFileRoute("/_authenticated/capsule")({
  head: () => ({
    meta: [
      { title: "Time Capsule — Stack'd" },
      { name: "description", content: "Write a letter to your future self, seal it, and forget until it opens." },
      { property: "og:title", content: "Time Capsule — Stack'd" },
      { property: "og:description", content: "Write a letter to your future self." },
    ],
  }),
  component: CapsulePage,
});

function CapsulePage() {
  const load = useServerFn(listCapsules);
  const write = useServerFn(writeCapsule);
  const open = useServerFn(openCapsule);
  const [rows, setRows] = useState<Capsule[]>([]);
  const [msg, setMsg] = useState("");
  const [days, setDays] = useState(30);
  const [busy, setBusy] = useState(false);

  const refresh = () => load().then((r) => setRows(r.rows));
  useEffect(() => {
    refresh();
  }, []);

  const send = async () => {
    if (!msg.trim()) return;
    setBusy(true);
    try {
      await write({ data: { message: msg, days } });
      toast.success(`Sealed. Opens in ${days} days.`);
      setMsg("");
      refresh();
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "Failed");
    } finally {
      setBusy(false);
    }
  };

  const now = Date.now();
  const openable = rows.filter((c) => new Date(c.open_at).getTime() <= now);
  const sealed = rows.filter((c) => new Date(c.open_at).getTime() > now);

  return (
    <div className="min-h-screen bg-obsidian text-silver">
      <Nav />
      <main className="max-w-3xl mx-auto px-6 pt-28 pb-24 space-y-12">
        <header>
          <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Vault of Selves</p>
          <h1 className="mt-3 text-4xl md:text-5xl font-serif">Time Capsule</h1>
          <p className="mt-3 text-silver-dim">Write a letter to your future self. Seal it. Forget it.</p>
        </header>

        <section className="border border-white/10 rounded-md p-6 bg-black/30">
          <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">Compose</p>
          <textarea
            value={msg}
            onChange={(e) => setMsg(e.target.value)}
            rows={6}
            maxLength={4000}
            placeholder="Dear future me,"
            className="mt-3 w-full bg-black/50 border border-white/10 rounded p-3 text-silver text-sm resize-none focus:outline-none focus:border-ember/40"
          />
          <div className="mt-4 flex items-center gap-4">
            <label className="text-xs text-silver-dim">Opens in</label>
            <input
              type="number"
              min={1}
              max={365}
              value={days}
              onChange={(e) => setDays(parseInt(e.target.value) || 30)}
              className="w-20 bg-black/50 border border-white/10 rounded px-3 py-1 text-sm text-silver"
            />
            <span className="text-xs text-silver-dim">days</span>
            <button
              onClick={send}
              disabled={busy || !msg.trim()}
              className="ml-auto px-5 py-2 rounded-full border border-ember text-ember font-mono text-xs uppercase tracking-widest disabled:opacity-40 hover:bg-ember/10"
            >
              {busy ? "Sealing…" : "Seal capsule"}
            </button>
          </div>
        </section>

        {openable.length > 0 && (
          <section>
            <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Ready to Open</p>
            <div className="mt-4 space-y-3">
              {openable.map((c) => (
                <div key={c.id} className="border border-ember/40 rounded-md p-5 bg-ember/[0.04]">
                  {c.opened_at ? (
                    <>
                      <p className="text-sm text-silver whitespace-pre-wrap">{c.message}</p>
                      <p className="mt-3 text-[10px] font-mono uppercase tracking-widest text-silver-dim">
                        Written {new Date(c.created_at).toLocaleDateString()} · Opened {new Date(c.opened_at).toLocaleDateString()}
                      </p>
                    </>
                  ) : (
                    <button
                      onClick={async () => { await open({ data: { id: c.id } }); refresh(); }}
                      className="w-full py-3 border border-dashed border-ember/60 rounded font-serif text-lg text-ember hover:bg-ember/5"
                    >
                      Open — sealed {new Date(c.created_at).toLocaleDateString()}
                    </button>
                  )}
                </div>
              ))}
            </div>
          </section>
        )}

        <section>
          <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">Sealed</p>
          {sealed.length === 0 ? (
            <EmptyState title="No sealed capsules" description="Your future self is waiting." />
          ) : (
            <ul className="mt-4 space-y-2">
              {sealed.map((c) => (
                <li key={c.id} className="border border-white/10 rounded p-4 flex justify-between text-sm">
                  <span className="text-silver-dim">Sealed {new Date(c.created_at).toLocaleDateString()}</span>
                  <span className="font-mono text-xs text-ember">Opens {new Date(c.open_at).toLocaleDateString()}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </main>
    </div>
  );
}
