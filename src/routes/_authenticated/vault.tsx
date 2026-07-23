import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { Nav } from "@/components/nav";
import { listVault, createVaultItem, deleteVaultItem, summarizeVaultItem, type VaultItem } from "@/lib/memory-vault.functions";
import { toast } from "sonner";
import { haptic } from "@/lib/haptics";

export const Route = createFileRoute("/_authenticated/vault")({
  head: () => ({ meta: [{ title: "Memory Vault — Stack'd" }, { name: "description", content: "Searchable archive of your focus sessions, notes and links." }] }),
  component: VaultPage,
});

function VaultPage() {
  const list = useServerFn(listVault);
  const create = useServerFn(createVaultItem);
  const del = useServerFn(deleteVaultItem);
  const summarize = useServerFn(summarizeVaultItem);

  const [items, setItems] = useState<VaultItem[]>([]);
  const [q, setQ] = useState("");
  const [loading, setLoading] = useState(true);
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [tags, setTags] = useState("");

  const refresh = async (query?: string) => {
    setLoading(true);
    try {
      const rows = await list({ data: { q: query || undefined, limit: 50 } });
      setItems(rows);
    } finally { setLoading(false); }
  };

  useEffect(() => { refresh(); }, []);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;
    haptic("select");
    await create({ data: { title: title.trim(), body: body || undefined, tags: tags.split(",").map(s => s.trim()).filter(Boolean) } });
    setTitle(""); setBody(""); setTags("");
    toast.success("Saved to vault");
    refresh(q);
  };

  return (
    <div className="min-h-screen bg-obsidian text-silver">
      <Nav />
      <div className="mx-auto max-w-4xl px-4 py-10">
        <div className="mb-8">
          <div className="font-mono text-[10px] uppercase tracking-[0.3em] text-muted-foreground">Memory Vault</div>
          <h1 className="mt-2 text-3xl font-extrabold tracking-tight">Your focus archive</h1>
          <p className="mt-2 text-sm text-muted-foreground">Every note, link, and idea from your sessions — searchable months later.</p>
        </div>

        <form onSubmit={onSubmit} className="glass rounded-2xl p-5 mb-8 space-y-3">
          <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Title" className="w-full bg-transparent border border-white/10 rounded px-3 py-2 text-sm outline-none focus:border-ember" />
          <textarea value={body} onChange={(e) => setBody(e.target.value)} placeholder="Notes, quotes, key ideas…" rows={3} className="w-full bg-transparent border border-white/10 rounded px-3 py-2 text-sm outline-none focus:border-ember" />
          <input value={tags} onChange={(e) => setTags(e.target.value)} placeholder="tags, comma, separated" className="w-full bg-transparent border border-white/10 rounded px-3 py-2 text-xs font-mono outline-none focus:border-ember" />
          <button type="submit" className="bg-silver text-obsidian px-4 py-2 rounded font-mono text-xs uppercase tracking-widest font-bold hover:opacity-90">Save</button>
        </form>

        <div className="mb-4 flex gap-2">
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search titles, notes, summaries…" className="flex-1 bg-transparent border border-white/10 rounded px-3 py-2 text-sm outline-none focus:border-ember" />
          <button onClick={() => refresh(q)} className="border border-white/10 px-4 rounded font-mono text-xs uppercase hover:bg-white/5">Search</button>
        </div>

        {loading && <div className="text-sm text-muted-foreground">Loading…</div>}
        {!loading && items.length === 0 && (
          <div className="glass rounded-2xl p-10 text-center">
            <div className="text-4xl mb-3">📚</div>
            <div className="text-sm text-muted-foreground">Nothing here yet. Every note you save becomes future-you's search index.</div>
          </div>
        )}
        <ul className="space-y-3">
          {items.map((it) => (
            <li key={it.id} className="glass rounded-xl p-4">
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <div className="font-semibold text-silver truncate">{it.title}</div>
                  {it.body && <p className="mt-1 text-sm text-muted-foreground whitespace-pre-wrap">{it.body}</p>}
                  {it.ai_summary && <p className="mt-2 text-xs italic text-ember/80">✦ {it.ai_summary}</p>}
                  {it.tags.length > 0 && (
                    <div className="mt-2 flex flex-wrap gap-1">
                      {it.tags.map((t) => <span key={t} className="font-mono text-[10px] uppercase tracking-widest border border-white/10 rounded px-1.5 py-0.5">{t}</span>)}
                    </div>
                  )}
                </div>
                <div className="flex flex-col gap-1 shrink-0">
                  {!it.ai_summary && (
                    <button onClick={async () => { await summarize({ data: { id: it.id } }); refresh(q); }}
                      className="text-[10px] font-mono uppercase tracking-widest border border-white/10 rounded px-2 py-1 hover:bg-white/5">AI ✦</button>
                  )}
                  <button onClick={async () => { await del({ data: { id: it.id } }); refresh(q); }}
                    className="text-[10px] font-mono uppercase tracking-widest text-breach/70 hover:text-breach">Delete</button>
                </div>
              </div>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
