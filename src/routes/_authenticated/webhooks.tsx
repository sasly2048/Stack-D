import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { Nav } from "@/components/nav";
import {
  listWebhooks,
  createWebhook,
  toggleWebhook,
  deleteWebhook,
  EVENT_TYPES,
  type Webhook,
} from "@/lib/webhooks.functions";
import { listDeliveries, testWebhook, type Delivery } from "@/lib/webhook-deliveries.functions";
import { toast } from "sonner";
import { haptic } from "@/lib/haptics";

export const Route = createFileRoute("/_authenticated/webhooks")({
  head: () => ({
    meta: [
      { title: "Webhooks — Stack'd" },
      { name: "description", content: "Stream Stack'd events to your own systems." },
    ],
  }),
  component: WebhooksPage,
});

function WebhooksPage() {
  const list = useServerFn(listWebhooks);
  const create = useServerFn(createWebhook);
  const toggle = useServerFn(toggleWebhook);
  const remove = useServerFn(deleteWebhook);
  const [hooks, setHooks] = useState<Webhook[]>([]);
  const [url, setUrl] = useState("");
  const [selected, setSelected] = useState<string[]>(["session.complete"]);
  const [busy, setBusy] = useState(false);
  const [reveal, setReveal] = useState<string | null>(null);

  const refresh = () => list().then(setHooks).catch(() => {});
  useEffect(() => { refresh(); }, []);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!url || selected.length === 0) return;
    setBusy(true);
    try {
      const row = await create({ data: { url, events: selected as never[] } });
      setHooks((h) => [row, ...h]);
      setReveal(row.id);
      setUrl("");
      haptic("success");
      toast.success("Webhook created. Copy the secret now.");
    } catch (err) {
      toast.error((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="min-h-screen bg-obsidian text-silver">
      <Nav />
      <div className="pt-24 max-w-4xl mx-auto px-6 pb-24">
        <h1 className="text-3xl font-serif mb-2">Webhooks</h1>
        <p className="text-sm text-muted-foreground mb-8 max-w-xl">
          Stream focus events to your own systems. Each delivery is signed with HMAC-SHA256 in the
          <code className="mx-1 px-1.5 py-0.5 rounded bg-white/5 font-mono text-xs">X-Stackd-Signature</code>
          header.
        </p>

        <form onSubmit={submit} className="glass rounded-xl p-6 mb-8 space-y-4">
          <div>
            <label className="text-[10px] font-mono uppercase tracking-widest text-muted-foreground">Endpoint URL</label>
            <input
              type="url"
              required
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder="https://your-app.com/webhooks/stackd"
              className="mt-1 w-full bg-black/40 border border-white/10 rounded-md px-3 py-2 text-sm focus:border-ember/60 outline-none"
            />
          </div>
          <div>
            <div className="text-[10px] font-mono uppercase tracking-widest text-muted-foreground mb-2">Events</div>
            <div className="flex flex-wrap gap-2">
              {EVENT_TYPES.map((ev) => {
                const on = selected.includes(ev);
                return (
                  <button
                    type="button"
                    key={ev}
                    onClick={() => setSelected((s) => (on ? s.filter((x) => x !== ev) : [...s, ev]))}
                    className={`px-3 py-1 rounded-full text-xs font-mono border transition-colors ${
                      on ? "border-ember/60 text-ember bg-ember/10" : "border-white/10 text-muted-foreground hover:text-silver"
                    }`}
                  >
                    {ev}
                  </button>
                );
              })}
            </div>
          </div>
          <button
            type="submit"
            disabled={busy}
            className="btn-ember px-5 py-2 border border-silver/20 rounded-full text-silver text-xs font-mono uppercase tracking-widest disabled:opacity-60"
          >
            {busy ? "Creating…" : "Create Webhook"}
          </button>
        </form>

        <div className="space-y-3">
          {hooks.length === 0 && (
            <div className="text-sm text-muted-foreground text-center py-12">No webhooks yet.</div>
          )}
          {hooks.map((h) => (
            <HookRow
              key={h.id}
              hook={h}
              revealSecret={reveal === h.id}
              onToggle={async () => {
                await toggle({ data: { id: h.id, active: !h.active } });
                setHooks((all) => all.map((x) => (x.id === h.id ? { ...x, active: !x.active } : x)));
              }}
              onDelete={async () => {
                if (!confirm("Delete this webhook?")) return;
                await remove({ data: { id: h.id } });
                setHooks((all) => all.filter((x) => x.id !== h.id));
              }}
            />
          ))}
        </div>

        <div className="mt-10 text-center">
          <Link to="/sdk" className="text-[10px] font-mono uppercase tracking-widest text-muted-foreground hover:text-ember">
            SDK & signature verification →
          </Link>
        </div>
      </div>
    </div>
  );
}

function HookRow({
  hook,
  revealSecret,
  onToggle,
  onDelete,
}: {
  hook: Webhook;
  revealSecret: boolean;
  onToggle: () => Promise<void>;
  onDelete: () => Promise<void>;
}) {
  const listDel = useServerFn(listDeliveries);
  const test = useServerFn(testWebhook);
  const [open, setOpen] = useState(false);
  const [rows, setRows] = useState<Delivery[]>([]);
  const [loading, setLoading] = useState(false);
  const [testing, setTesting] = useState(false);

  const load = () => {
    setLoading(true);
    listDel({ data: { webhookId: hook.id } }).then(setRows).finally(() => setLoading(false));
  };

  useEffect(() => { if (open) load(); }, [open]);

  const runTest = async () => {
    setTesting(true);
    haptic("tap");
    try {
      const row = await test({ data: { webhookId: hook.id } });
      setRows((r) => [row, ...r]);
      if (!open) setOpen(true);
      row.ok ? toast.success(`Test delivered · ${row.status_code}`) : toast.error(`Test failed · ${row.status_code ?? "network"}`);
    } catch (err) {
      toast.error((err as Error).message);
    } finally {
      setTesting(false);
    }
  };

  return (
    <div className="glass rounded-xl p-5 flex flex-col gap-3">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="text-sm font-mono truncate">{hook.url}</div>
          <div className="text-[11px] text-muted-foreground mt-1">
            {hook.events.join(" · ")}
          </div>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <button
            onClick={runTest}
            disabled={testing}
            className="text-[10px] font-mono uppercase tracking-widest px-2 py-1 rounded-full border border-white/10 text-muted-foreground hover:text-ember hover:border-ember/40 disabled:opacity-50"
          >
            {testing ? "Testing…" : "Test"}
          </button>
          <button
            onClick={onToggle}
            className={`text-[10px] font-mono uppercase tracking-widest px-2 py-1 rounded-full border ${
              hook.active ? "border-ember/40 text-ember" : "border-white/10 text-muted-foreground"
            }`}
          >
            {hook.active ? "Live" : "Paused"}
          </button>
          <button
            onClick={onDelete}
            className="text-[10px] font-mono uppercase tracking-widest text-muted-foreground hover:text-ember"
          >
            Delete
          </button>
        </div>
      </div>

      {revealSecret && (
        <div className="bg-black/60 border border-ember/30 rounded-md px-3 py-2 text-[11px] font-mono text-ember break-all">
          <div className="text-muted-foreground mb-1">Signing secret (shown once)</div>
          {hook.secret}
        </div>
      )}

      <button
        onClick={() => setOpen((o) => !o)}
        className="text-[10px] font-mono uppercase tracking-widest text-muted-foreground hover:text-silver text-left"
      >
        {open ? "▾" : "▸"} Delivery log
      </button>

      {open && (
        <div className="border-t border-white/5 pt-3">
          {loading ? (
            <div className="text-xs text-muted-foreground">Loading…</div>
          ) : rows.length === 0 ? (
            <div className="text-xs text-muted-foreground">
              No deliveries yet. Hit <span className="text-silver">Test</span> to send a signed sample.
            </div>
          ) : (
            <ul className="space-y-2">
              {rows.map((d) => (
                <li key={d.id} className="text-[11px] font-mono flex items-center gap-3">
                  <span className={`size-2 rounded-full shrink-0 ${d.ok ? "bg-ember" : "bg-breach"}`} />
                  <span className="text-muted-foreground w-32 shrink-0">
                    {new Date(d.created_at).toLocaleString([], { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" })}
                  </span>
                  <span className="text-silver w-14 shrink-0">{d.status_code ?? "—"}</span>
                  <span className="text-muted-foreground shrink-0">{d.event}</span>
                  <span className="text-muted-foreground/70 truncate">{d.response_snippet}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

