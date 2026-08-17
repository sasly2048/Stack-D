import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { Nav } from "@/components/nav";
import { EmptyState, QueryBoundary, SkeletonList } from "@/components/query-states";
import { PremiumGate } from "@/components/premium/premium-gate";
import {
  listVault,
  createVaultItem,
  deleteVaultItem,
  summarizeVaultItem,
  type VaultItem,
} from "@/lib/memory-vault.functions";
import { toast } from "sonner";
import { haptic } from "@/lib/haptics";

export const Route = createFileRoute("/_authenticated/vault")({
  head: () => ({
    meta: [
      { title: "Memory Vault — Stack'd" },
      {
        name: "description",
        content:
          "A searchable archive of your Stack'd focus sessions, notes and links, kept in one private place.",
      },
      { property: "og:title", content: "Memory Vault — Stack'd" },
      {
        property: "og:description",
        content:
          "A searchable archive of your Stack'd focus sessions, notes and links, kept in one private place.",
      },
    ],
  }),
  component: VaultPage,
});

function VaultPage() {
  const list = useServerFn(listVault);
  const create = useServerFn(createVaultItem);
  const del = useServerFn(deleteVaultItem);
  const summarize = useServerFn(summarizeVaultItem);

  const queryClient = useQueryClient();
  const [q, setQ] = useState("");
  // The typed box and the submitted search are separate: only pressing Search
  // moves `submittedQ`, so the query key (and the fetch) doesn't churn per key.
  const [submittedQ, setSubmittedQ] = useState("");
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [tags, setTags] = useState("");

  const vaultQuery = useQuery({
    queryKey: ["vault", submittedQ],
    queryFn: () => list({ data: { q: submittedQ || undefined, limit: 50 } }),
  });
  const items: VaultItem[] = vaultQuery.data ?? [];

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["vault"] });

  const createMutation = useMutation({
    mutationFn: () =>
      create({
        data: {
          title: title.trim(),
          body: body || undefined,
          tags: tags
            .split(",")
            .map((s) => s.trim())
            .filter(Boolean),
        },
      }),
    onSuccess: () => {
      setTitle("");
      setBody("");
      setTags("");
      toast.success("Saved to vault");
      invalidate();
    },
    onError: (err) => toast.error((err as Error).message),
  });

  // Summarizing calls an AI model, which is slow enough that the unguarded
  // button invited three or four clicks and three or four billed calls.
  const summarizeMutation = useMutation({
    mutationFn: (id: string) => summarize({ data: { id } }),
    onSuccess: () => {
      toast.success("Summary ready");
      invalidate();
    },
    onError: (err) => toast.error((err as Error).message),
  });

  // Deleting is irreversible and previously had no confirm, no disabled state
  // and no toast at all — a failed delete was indistinguishable from success.
  const deleteMutation = useMutation({
    mutationFn: (id: string) => del({ data: { id } }),
    onSuccess: () => {
      toast.success("Deleted from vault");
      invalidate();
    },
    onError: (err) => toast.error((err as Error).message),
  });

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;
    haptic("select");
    createMutation.mutate();
  };

  return (
    <div className="min-h-screen bg-obsidian text-silver">
      <Nav />
      <div className="mx-auto max-w-4xl px-4 py-10">
        <div className="mb-8">
          <div className="font-mono text-[10px] uppercase tracking-[0.3em] text-muted-foreground">
            Memory Vault
          </div>
          <h1 className="mt-2 text-3xl font-extrabold tracking-tight">Your focus archive</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            Every note, link, and idea from your sessions — searchable months later.
          </p>
        </div>

        <PremiumGate feature="vault" label="Memory Vault">
          <form onSubmit={onSubmit} className="glass rounded-2xl p-5 mb-8 space-y-3">
            <input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Title"
              className="w-full bg-transparent border border-white/10 rounded px-3 py-2 text-sm outline-none focus:border-ember"
            />
            <textarea
              value={body}
              onChange={(e) => setBody(e.target.value)}
              placeholder="Notes, quotes, key ideas…"
              rows={3}
              className="w-full bg-transparent border border-white/10 rounded px-3 py-2 text-sm outline-none focus:border-ember"
            />
            <input
              value={tags}
              onChange={(e) => setTags(e.target.value)}
              placeholder="tags, comma, separated"
              className="w-full bg-transparent border border-white/10 rounded px-3 py-2 text-xs font-mono outline-none focus:border-ember"
            />
            {/* Submitting with an empty title used to hit `if (!title.trim()) return;`
              and silently do nothing — a dead click with no feedback. Disabling
              the button makes that state impossible to reach. */}
            <button
              type="submit"
              disabled={createMutation.isPending || !title.trim()}
              className="bg-silver text-obsidian px-4 py-2 rounded font-mono text-xs uppercase tracking-widest font-bold hover:opacity-90 disabled:opacity-40"
            >
              {createMutation.isPending ? "Saving…" : "Save"}
            </button>
          </form>

          <div className="mb-4 flex gap-2">
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Search titles, notes, summaries…"
              className="flex-1 bg-transparent border border-white/10 rounded px-3 py-2 text-sm outline-none focus:border-ember"
            />
            <button
              onClick={() => setSubmittedQ(q)}
              className="border border-white/10 px-4 rounded font-mono text-xs uppercase hover:bg-white/5"
            >
              Search
            </button>
          </div>

          {/* Loading before emptiness, and the hand-rolled empty card is now the
            shared EmptyState so it announces itself to screen readers. */}
          <QueryBoundary
            isPending={vaultQuery.isPending}
            isError={vaultQuery.isError}
            error={vaultQuery.error}
            onRetry={() => vaultQuery.refetch()}
            errorTitle="Couldn't load your vault."
            loadingLabel="Loading your vault"
            skeleton={<SkeletonList rows={3} />}
            isEmpty={items.length === 0}
            empty={
              <EmptyState
                icon="📚"
                title="Nothing here yet"
                description="Nothing here yet. Every note you save becomes future-you's search index."
              />
            }
          >
            <ul className="space-y-3">
              {items.map((it) => (
                <li key={it.id} className="glass rounded-xl p-4">
                  <div className="flex items-start justify-between gap-4">
                    <div className="min-w-0">
                      <div className="font-semibold text-silver truncate">{it.title}</div>
                      {it.body && (
                        <p className="mt-1 text-sm text-muted-foreground whitespace-pre-wrap">
                          {it.body}
                        </p>
                      )}
                      {it.ai_summary && (
                        <p className="mt-2 text-xs italic text-ember/80">✦ {it.ai_summary}</p>
                      )}
                      {it.tags.length > 0 && (
                        <div className="mt-2 flex flex-wrap gap-1">
                          {it.tags.map((t) => (
                            <span
                              key={t}
                              className="font-mono text-[10px] uppercase tracking-widest border border-white/10 rounded px-1.5 py-0.5"
                            >
                              {t}
                            </span>
                          ))}
                        </div>
                      )}
                    </div>
                    <div className="flex flex-col gap-1 shrink-0">
                      {!it.ai_summary && (
                        <button
                          onClick={() => summarizeMutation.mutate(it.id)}
                          disabled={
                            summarizeMutation.isPending && summarizeMutation.variables === it.id
                          }
                          className="text-[10px] font-mono uppercase tracking-widest border border-white/10 rounded px-2 py-1 hover:bg-white/5 disabled:opacity-40"
                        >
                          {summarizeMutation.isPending && summarizeMutation.variables === it.id
                            ? "…"
                            : "AI ✦"}
                        </button>
                      )}
                      <button
                        onClick={() => {
                          if (!window.confirm("Delete this vault item?")) return;
                          deleteMutation.mutate(it.id);
                        }}
                        disabled={deleteMutation.isPending && deleteMutation.variables === it.id}
                        className="text-[10px] font-mono uppercase tracking-widest text-breach/70 hover:text-breach disabled:opacity-40"
                      >
                        {deleteMutation.isPending && deleteMutation.variables === it.id
                          ? "Deleting…"
                          : "Delete"}
                      </button>
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          </QueryBoundary>
        </PremiumGate>
      </div>
    </div>
  );
}
