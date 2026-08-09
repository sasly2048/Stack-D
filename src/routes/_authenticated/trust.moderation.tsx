import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, Link } from "@tanstack/react-router";
import { useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { Nav } from "@/components/nav";
import { QueryBoundary, SkeletonList } from "@/components/query-states";
import { listRoomReports, resolveReport, type HostReport } from "@/lib/moderation.functions";
import { toast } from "sonner";

export const Route = createFileRoute("/_authenticated/trust/moderation")({
  head: () => ({
    meta: [
      { title: "Moderation — Stack'd" },
      { name: "description", content: "Review and resolve reports filed on the Stack'd rooms you host, and keep your sessions safe." },
      { property: "og:title", content: "Moderation — Stack'd" },
      { property: "og:description", content: "Review and resolve reports filed on the Stack'd rooms you host, and keep your sessions safe." },
    ],
  }),
  component: ModerationPage,
});

function ModerationPage() {
  const list = useServerFn(listRoomReports);
  const resolve = useServerFn(resolveReport);
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState<"open" | "all">("open");

  const reportsQuery = useQuery({
    queryKey: ["moderation-reports"],
    queryFn: async () => (await list()).rows,
  });
  const rows: HostReport[] = reportsQuery.data ?? [];

  // The old handler patched the row into local state and never rolled back, so
  // a failed resolve still showed the report as resolved until a reload. And
  // with no disabled state a double-click fired the action twice.
  const actMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: "resolved" | "dismissed" }) =>
      resolve({ data: { id, status } }),
    onSuccess: (_r, { status }) => {
      toast.success(status === "resolved" ? "Report resolved." : "Report dismissed.");
      queryClient.invalidateQueries({ queryKey: ["moderation-reports"] });
    },
    onError: (err) => toast.error((err as Error).message),
  });

  const act = (id: string, status: "resolved" | "dismissed") => actMutation.mutate({ id, status });
  // Only the row mid-flight disables, so one busy report doesn't freeze the page.
  const busyId = actMutation.isPending ? actMutation.variables?.id : null;

  const visible = rows.filter((r) => (filter === "open" ? r.status === "open" : true));

  return (
    <div className="min-h-screen bg-obsidian text-silver">
      <Nav />
      <div className="pt-24 max-w-5xl mx-auto px-6 pb-24">
        <div className="flex items-baseline justify-between mb-6">
          <div>
            <h1 className="text-3xl font-serif">Moderation</h1>
            <p className="text-xs font-mono text-muted-foreground mt-1">
              Reports filed on rooms you host.
            </p>
          </div>
          <Link
            to="/trust"
            className="text-[10px] font-mono uppercase tracking-widest text-muted-foreground hover:text-ember"
          >
            ← Trust & Safety
          </Link>
        </div>

        <div className="flex gap-2 mb-6">
          {(["open", "all"] as const).map((k) => (
            <button
              key={k}
              onClick={() => setFilter(k)}
              className={`text-[10px] font-mono uppercase tracking-widest px-3 py-1.5 rounded-full border ${
                filter === k
                  ? "border-ember/60 text-ember bg-ember/10"
                  : "border-white/10 text-muted-foreground"
              }`}
            >
              {k}
            </button>
          ))}
        </div>

        <QueryBoundary
          isPending={reportsQuery.isPending}
          isError={reportsQuery.isError}
          error={reportsQuery.error}
          onRetry={() => reportsQuery.refetch()}
          errorTitle="Couldn't load reports."
          loadingLabel="Loading reports"
          skeleton={<SkeletonList rows={3} />}
          isEmpty={visible.length === 0}
          empty={
            <div className="glass rounded-xl p-12 text-center">
              <div className="text-sm text-muted-foreground">No reports here.</div>
              <div className="text-[11px] font-mono text-muted-foreground/70 mt-2">
                Rooms you host are clean.
              </div>
            </div>
          }
        >
          <div className="space-y-3">
            {visible.map((r) => (
              <div key={r.id} className="glass rounded-xl p-5">
                <div className="flex flex-wrap items-start justify-between gap-3 mb-3">
                  <div className="min-w-0">
                    <div className="text-[10px] font-mono uppercase tracking-widest text-muted-foreground">
                      {new Date(r.created_at).toLocaleString()} · Room {r.room_code ?? "—"}
                    </div>
                    <div className="text-sm mt-1">
                      <span className="text-ember font-mono">{r.kind}</span>
                      {r.target_name && (
                        <span className="text-silver/80"> · target: {r.target_name}</span>
                      )}
                      {r.reporter_name && (
                        <span className="text-muted-foreground"> · from {r.reporter_name}</span>
                      )}
                    </div>
                  </div>
                  <span
                    className={`text-[10px] font-mono uppercase tracking-widest px-2 py-1 rounded-full border ${
                      r.status === "open"
                        ? "border-ember/40 text-ember"
                        : "border-white/10 text-muted-foreground"
                    }`}
                  >
                    {r.status}
                  </span>
                </div>
                {r.reason && (
                  <div className="text-sm text-silver/80 border-l-2 border-white/10 pl-3 mb-3 italic">
                    "{r.reason}"
                  </div>
                )}
                {r.status === "open" && (
                  <div className="flex gap-2">
                    <button
                      onClick={() => act(r.id, "resolved")}
                      disabled={busyId === r.id}
                      className="text-[10px] font-mono uppercase tracking-widest px-3 py-1.5 rounded-full border border-ember/40 text-ember hover:bg-ember/10 disabled:opacity-40"
                    >
                      Resolve
                    </button>
                    <button
                      onClick={() => act(r.id, "dismissed")}
                      disabled={busyId === r.id}
                      className="text-[10px] font-mono uppercase tracking-widest px-3 py-1.5 rounded-full border border-white/10 text-muted-foreground hover:text-silver disabled:opacity-40"
                    >
                      Dismiss
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
        </QueryBoundary>
      </div>
    </div>
  );
}
