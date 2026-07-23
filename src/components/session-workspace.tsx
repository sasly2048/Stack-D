import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import {
  listWorkspace,
  addWorkspaceItem,
  updateWorkspaceItem,
  deleteWorkspaceItem,
  type WorkspaceItem,
} from "@/lib/session-interactions.functions";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { haptic } from "@/lib/haptics";
import { toast } from "sonner";

type Props = { sessionId?: string; roomId?: string };

export function SessionWorkspace({ sessionId, roomId }: Props) {
  const [items, setItems] = useState<WorkspaceItem[]>([]);
  const [kind, setKind] = useState<"note" | "todo" | "link">("note");
  const [content, setContent] = useState("");
  const [url, setUrl] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  const list = useServerFn(listWorkspace);
  const add = useServerFn(addWorkspaceItem);
  const upd = useServerFn(updateWorkspaceItem);
  const del = useServerFn(deleteWorkspaceItem);

  useEffect(() => {
    let alive = true;
    list({ data: { sessionId, roomId } })
      .then((rows) => { if (alive) setItems(rows); })
      .catch(() => {})
      .finally(() => alive && setLoading(false));
    return () => { alive = false; };
  }, [sessionId, roomId, list]);

  async function submit() {
    if (!content.trim() || busy) return;
    haptic("tap");
    setBusy(true);
    try {
      const item = await add({
        data: {
          sessionId,
          roomId,
          kind,
          content: content.trim(),
          url: kind === "link" && url.trim() ? url.trim() : undefined,
        },
      });
      setItems((prev) => [item, ...prev]);
      setContent("");
      setUrl("");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Failed to add");
    } finally {
      setBusy(false);
    }
  }

  async function toggleDone(item: WorkspaceItem) {
    const next = { ...item, done: !item.done };
    haptic(next.done ? "success" : "select");
    setItems((prev) => prev.map((i) => (i.id === item.id ? next : i)));
    try {
      await upd({ data: { id: item.id, done: next.done } });
    } catch {
      setItems((prev) => prev.map((i) => (i.id === item.id ? item : i)));
    }
  }

  async function remove(id: string) {
    const backup = items;
    setItems((prev) => prev.filter((i) => i.id !== id));
    try {
      await del({ data: { id } });
    } catch {
      setItems(backup);
      toast.error("Failed to delete");
    }
  }

  return (
    <div className="glass rounded-lg p-4 space-y-3">
      <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground">
        WORKSPACE
      </div>

      <div className="flex gap-1 text-[10px] font-mono uppercase tracking-widest">
        {(["note", "todo", "link"] as const).map((k) => (
          <button
            key={k}
            onClick={() => setKind(k)}
            className={`px-2 py-1 rounded ${
              kind === k ? "bg-ember/15 text-ember" : "text-muted-foreground hover:text-silver"
            }`}
          >
            {k}
          </button>
        ))}
      </div>

      <div className="space-y-2">
        {kind === "note" ? (
          <Textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            placeholder="Capture a thought…"
            rows={2}
            className="text-sm bg-white/5 border-white/10"
          />
        ) : (
          <Input
            value={content}
            onChange={(e) => setContent(e.target.value)}
            placeholder={kind === "todo" ? "Next action…" : "Link title…"}
            className="text-sm bg-white/5 border-white/10"
          />
        )}
        {kind === "link" && (
          <Input
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="https://…"
            className="text-sm bg-white/5 border-white/10"
          />
        )}
        <Button
          size="sm"
          onClick={submit}
          disabled={busy || !content.trim()}
          className="w-full h-8 text-xs bg-ember/20 hover:bg-ember/30 text-ember border border-ember/40"
        >
          Add {kind}
        </Button>
      </div>

      <ul className="space-y-1.5 max-h-72 overflow-y-auto pr-1">
        {loading && <li className="text-xs text-muted-foreground">Loading…</li>}
        {!loading && items.length === 0 && (
          <li className="text-xs text-muted-foreground">Nothing yet. Capture as you focus.</li>
        )}
        {items.map((i) => (
          <li key={i.id} className="group flex items-start gap-2 text-xs border border-white/5 rounded px-2 py-1.5 bg-white/[0.02]">
            {i.kind === "todo" ? (
              <input
                type="checkbox"
                checked={i.done}
                onChange={() => toggleDone(i)}
                className="mt-0.5 accent-ember"
              />
            ) : (
              <span className="mt-0.5 font-mono text-[9px] text-muted-foreground uppercase w-8 shrink-0">
                {i.kind === "note" ? "◆" : "→"}
              </span>
            )}
            <div className="flex-1 min-w-0">
              {i.kind === "link" && i.url ? (
                <a
                  href={i.url}
                  target="_blank"
                  rel="noreferrer noopener"
                  className="text-silver hover:text-ember break-words"
                >
                  {i.content}
                </a>
              ) : (
                <span className={`break-words ${i.done ? "line-through text-muted-foreground" : "text-silver"}`}>
                  {i.content}
                </span>
              )}
            </div>
            <button
              onClick={() => remove(i.id)}
              className="opacity-0 group-hover:opacity-100 text-muted-foreground hover:text-breach text-sm leading-none"
              aria-label="Delete"
            >
              ×
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
