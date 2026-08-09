import { useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { exportFocusHistoryCsv } from "@/lib/export.functions";
import { toast } from "sonner";
import { haptic } from "@/lib/haptics";

export function CsvExportButton({ className = "" }: { className?: string }) {
  const run = useServerFn(exportFocusHistoryCsv);
  const [busy, setBusy] = useState(false);

  const download = async () => {
    setBusy(true);
    try {
      const { filename, csv, rowCount } = await run();
      const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
      haptic("success");
      toast.success(`Exported ${rowCount} sessions.`);
    } catch (e) {
      toast.error((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <button
      onClick={download}
      disabled={busy}
      aria-busy={busy}
      className={`px-4 py-2 border border-white/10 rounded-full text-xs font-mono uppercase tracking-widest hover:border-ember/40 hover:text-ember hover:bg-white/[0.04] active:scale-[0.99] cursor-pointer transition-all duration-200 ease-[var(--ease-ritual)] disabled:opacity-50 disabled:cursor-not-allowed disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember focus-visible:ring-offset-2 focus-visible:ring-offset-obsidian ${className}`}
    >
      {busy ? "Exporting…" : "Export CSV"}
    </button>
  );
}
