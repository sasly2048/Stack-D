import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { toast } from "sonner";
import { Nav } from "@/components/nav";
import { listBlocks, listMyReports, unblockUser } from "@/lib/trust.functions";
import { EmptyState } from "@/components/empty-state";

export const Route = createFileRoute("/_authenticated/trust")({
  head: () => ({
    meta: [
      { title: "Trust & Safety — Stack'd" },
      { name: "description", content: "Manage blocks and view your reports." },
    ],
  }),
  component: TrustPage,
});

function TrustPage() {
  const blocks = useServerFn(listBlocks);
  const reports = useServerFn(listMyReports);
  const unblock = useServerFn(unblockUser);
  const [blockRows, setBlockRows] = useState<Array<{ id: string; display_name: string | null; created_at: string }>>([]);
  const [reportRows, setReportRows] = useState<Awaited<ReturnType<typeof reports>>["rows"]>([]);

  const refresh = async () => {
    const [b, r] = await Promise.all([blocks(), reports()]);
    setBlockRows(b.rows);
    setReportRows(r.rows);
  };

  useEffect(() => { refresh(); }, []);

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
          <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">Blocked users</p>
          {blockRows.length === 0 ? (
            <EmptyState title="Nobody blocked" description="You're on good terms with everyone." />
          ) : (
            <ul className="mt-4 space-y-2">
              {blockRows.map((b) => (
                <li key={b.id} className="border border-white/10 rounded p-4 flex justify-between items-center text-sm">
                  <span className="text-silver">{b.display_name ?? "Anon"}</span>
                  <button
                    onClick={async () => {
                      await unblock({ data: { userId: b.id } });
                      toast.success("Unblocked");
                      refresh();
                    }}
                    className="text-[10px] font-mono uppercase tracking-widest text-silver-dim hover:text-ember"
                  >
                    Unblock
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section>
          <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">Your reports</p>
          {reportRows.length === 0 ? (
            <EmptyState title="No reports filed" description="Report from any profile or room when needed." />
          ) : (
            <ul className="mt-4 space-y-2">
              {reportRows.map((r) => (
                <li key={r.id} className="border border-white/10 rounded p-4 text-sm">
                  <div className="flex justify-between">
                    <span className="text-silver">{r.kind}</span>
                    <span className={`font-mono text-[10px] uppercase tracking-widest ${
                      r.status === "open" ? "text-ember" : "text-silver-dim"
                    }`}>{r.status}</span>
                  </div>
                  {r.reason && <p className="mt-1 text-silver-dim text-xs">{r.reason}</p>}
                  <p className="mt-1 text-[10px] font-mono text-silver-dim/60 uppercase tracking-widest">
                    {new Date(r.created_at).toLocaleDateString()}
                  </p>
                </li>
              ))}
            </ul>
          )}
        </section>
      </main>
    </div>
  );
}
