import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, Link } from "@tanstack/react-router";
import { useServerFn } from "@tanstack/react-start";
import { toast } from "sonner";
import { Nav } from "@/components/nav";
import { listBlocks, listMyReports, unblockUser } from "@/lib/trust.functions";
import { EmptyState } from "@/components/empty-state";
import { QueryBoundary, SkeletonList } from "@/components/query-states";

export const Route = createFileRoute("/_authenticated/trust")({
  head: () => ({
    meta: [
      { title: "Trust & Safety — Stack'd" },
      { name: "description", content: "Manage blocked accounts and review the reports you have filed across your Stack'd sessions." },
      { property: "og:title", content: "Trust & Safety — Stack'd" },
      { property: "og:description", content: "Manage blocked accounts and review the reports you have filed across your Stack'd sessions." },
    ],
  }),
  component: TrustPage,
});

function TrustPage() {
  const blocks = useServerFn(listBlocks);
  const reports = useServerFn(listMyReports);
  const unblock = useServerFn(unblockUser);
  const queryClient = useQueryClient();

  // The old refresh() was called from useEffect with no .catch, so a failed
  // load was an unhandled rejection and the page just sat on its empty states.
  const trustQuery = useQuery({
    queryKey: ["trust-blocks"],
    queryFn: async () => {
      const [b, r] = await Promise.all([blocks(), reports()]);
      return { blockRows: b.rows, reportRows: r.rows };
    },
  });

  const blockRows = trustQuery.data?.blockRows ?? [];
  const reportRows = trustQuery.data?.reportRows ?? [];

  const unblockMutation = useMutation({
    mutationFn: (userId: string) => unblock({ data: { userId } }),
    onSuccess: () => {
      toast.success("Unblocked");
      queryClient.invalidateQueries({ queryKey: ["trust-blocks"] });
    },
    // Unblocking silently failing left the row in place with no explanation.
    onError: () => toast.error("Could not unblock"),
  });

  return (
    <div className="min-h-screen bg-obsidian text-silver">
      <Nav />
      <main className="max-w-4xl mx-auto px-6 pt-28 pb-24 space-y-12">
        <header>
          <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Safety</p>
          <h1 className="mt-3 text-4xl md:text-5xl font-serif">Trust & Safety</h1>
          <p className="mt-3 text-silver-dim">Blocks are silent. Reports go to moderators.</p>
          <Link
            to="/trust/moderation"
            className="inline-block mt-4 text-[10px] font-mono uppercase tracking-widest text-ember hover:underline"
          >
            Host moderation dashboard →
          </Link>
        </header>

        <section>
          <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">
            Blocked users
          </p>
          {/* Loading is checked before emptiness. "Nobody blocked" used to show
              while the list was still loading, telling a user with blocks that
              they had none. */}
          <QueryBoundary
            isPending={trustQuery.isPending}
            isError={trustQuery.isError}
            error={trustQuery.error}
            onRetry={() => trustQuery.refetch()}
            errorTitle="Couldn't load your blocked users."
            loadingLabel="Loading blocked users"
            skeleton={<SkeletonList rows={2} className="mt-4" />}
            isEmpty={blockRows.length === 0}
            empty={
              <EmptyState title="Nobody blocked" description="You're on good terms with everyone." />
            }
          >
            <ul className="mt-4 space-y-2">
              {blockRows.map((b) => (
                <li
                  key={b.id}
                  className="border border-white/10 rounded p-4 flex justify-between items-center text-sm"
                >
                  <span className="text-silver">{b.display_name ?? "Anon"}</span>
                  <button
                    onClick={() => unblockMutation.mutate(b.id)}
                    disabled={unblockMutation.isPending && unblockMutation.variables === b.id}
                    className="text-[10px] font-mono uppercase tracking-widest text-silver-dim hover:text-ember disabled:opacity-40"
                  >
                    {unblockMutation.isPending && unblockMutation.variables === b.id
                      ? "Unblocking…"
                      : "Unblock"}
                  </button>
                </li>
              ))}
            </ul>
          </QueryBoundary>
        </section>

        <section>
          <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">
            Your reports
          </p>
          {/* Same query backs both sections, so this empty state had the same
              bug: it claimed "No reports filed" before the fetch resolved. */}
          <QueryBoundary
            isPending={trustQuery.isPending}
            isError={trustQuery.isError}
            error={trustQuery.error}
            onRetry={() => trustQuery.refetch()}
            errorTitle="Couldn't load your reports."
            loadingLabel="Loading your reports"
            skeleton={<SkeletonList rows={2} className="mt-4" />}
            isEmpty={reportRows.length === 0}
            empty={
              <EmptyState
                title="No reports filed"
                description="Report from any profile or room when needed."
              />
            }
          >
            <ul className="mt-4 space-y-2">
              {reportRows.map((r) => (
                <li key={r.id} className="border border-white/10 rounded p-4 text-sm">
                  <div className="flex justify-between">
                    <span className="text-silver">{r.kind}</span>
                    <span
                      className={`font-mono text-[10px] uppercase tracking-widest ${
                        r.status === "open" ? "text-ember" : "text-silver-dim"
                      }`}
                    >
                      {r.status}
                    </span>
                  </div>
                  {r.reason && <p className="mt-1 text-silver-dim text-xs">{r.reason}</p>}
                  <p className="mt-1 text-[10px] font-mono text-silver-dim/60 uppercase tracking-widest">
                    {new Date(r.created_at).toLocaleDateString()}
                  </p>
                </li>
              ))}
            </ul>
          </QueryBoundary>
        </section>
      </main>
    </div>
  );
}
