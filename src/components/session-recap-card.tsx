import { useCallback, useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { generateSessionRecap, type SessionRecap } from "@/lib/ai.functions";
import { toast } from "sonner";

interface Props {
  roomId: string;
  roomCode: string;
  score: number;
  xp: number;
  durationSeconds: number;
  breachesCount: number;
  tier: string;
  displayName?: string;
}

/**
 * AI-written post-session recap. Fetches once when mounted (i.e. when the
 * room enters complete/aborted state) and offers a "Download PDF" action.
 *
 * PDF generation intentionally uses jsPDF client-side — no server round-trip,
 * no font/asset dependencies, and the file never leaves the device.
 */
export function SessionRecapCard(props: Props) {
  const { roomId, roomCode, score, xp, durationSeconds, breachesCount, tier, displayName } = props;
  const fetchRecap = useServerFn(generateSessionRecap);
  const [recap, setRecap] = useState<SessionRecap | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [exporting, setExporting] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    setError(false);
    fetchRecap({ data: { roomId, roomCode, score, xp, durationSeconds, breachesCount, tier } })
      .then((r) => setRecap(r))
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, [fetchRecap, roomId, roomCode, score, xp, durationSeconds, breachesCount, tier]);

  useEffect(() => {
    load();
  }, [load]);

  const exportPdf = async () => {
    if (!recap || exporting) return;
    setExporting(true);
    try {
      const { jsPDF } = await import("jspdf");
      const doc = new jsPDF({ unit: "pt", format: "letter" });
      const W = doc.internal.pageSize.getWidth();
      const M = 56;
      let y = 72;

      // Header bar
      doc.setFillColor(15, 15, 17);
      doc.rect(0, 0, W, 40, "F");
      doc.setTextColor(226, 226, 226);
      doc.setFont("helvetica", "bold");
      doc.setFontSize(10);
      doc.text("STACK'D · SESSION RECAP", M, 26);
      doc.setFont("helvetica", "normal");
      doc.setFontSize(8);
      doc.setTextColor(160, 160, 160);
      doc.text(`ROOM ${roomCode}`, W - M, 26, { align: "right" });

      // Title
      doc.setTextColor(20, 20, 20);
      doc.setFont("helvetica", "bold");
      doc.setFontSize(22);
      const titleLines = doc.splitTextToSize(recap.title, W - M * 2);
      doc.text(titleLines, M, y);
      y += titleLines.length * 26 + 8;

      // Byline
      doc.setFont("helvetica", "normal");
      doc.setFontSize(9);
      doc.setTextColor(120, 120, 120);
      doc.text(
        `${displayName ? displayName + " · " : ""}${new Date(recap.generatedAt).toLocaleString()}`,
        M,
        y,
      );
      y += 24;

      // Metric row
      const cellW = (W - M * 2) / 5;
      const metrics: Array<[string, string]> = [
        ["SCORE", `${recap.score}/100`],
        ["TIER", recap.tier],
        ["DURATION", `${Math.round(recap.durationSeconds / 60)} min`],
        ["XP", `+${recap.xp}`],
        ["ANOMALIES", String(recap.breachesCount)],
      ];
      doc.setDrawColor(220, 220, 220);
      doc.setLineWidth(0.5);
      doc.line(M, y, W - M, y);
      y += 14;
      metrics.forEach(([label, value], i) => {
        const x = M + i * cellW;
        doc.setFont("helvetica", "normal");
        doc.setFontSize(7);
        doc.setTextColor(140, 140, 140);
        doc.text(label, x, y);
        doc.setFont("helvetica", "bold");
        doc.setFontSize(14);
        doc.setTextColor(20, 20, 20);
        doc.text(value, x, y + 20);
      });
      y += 44;
      doc.line(M, y, W - M, y);
      y += 24;

      // Summary
      doc.setFont("helvetica", "bold");
      doc.setFontSize(9);
      doc.setTextColor(140, 140, 140);
      doc.text("SUMMARY", M, y);
      y += 16;
      doc.setFont("helvetica", "normal");
      doc.setFontSize(11);
      doc.setTextColor(30, 30, 30);
      const summaryLines = doc.splitTextToSize(recap.summary, W - M * 2);
      doc.text(summaryLines, M, y);
      y += summaryLines.length * 15 + 20;

      // Reflections
      doc.setFont("helvetica", "bold");
      doc.setFontSize(9);
      doc.setTextColor(140, 140, 140);
      doc.text("REFLECTIONS", M, y);
      y += 16;
      doc.setFont("helvetica", "normal");
      doc.setFontSize(11);
      doc.setTextColor(30, 30, 30);
      recap.reflections.forEach((r) => {
        const lines = doc.splitTextToSize(`— ${r}`, W - M * 2 - 12);
        doc.text(lines, M + 4, y);
        y += lines.length * 15 + 6;
      });
      y += 12;

      // Next step
      doc.setFont("helvetica", "bold");
      doc.setFontSize(9);
      doc.setTextColor(140, 140, 140);
      doc.text("NEXT STEP", M, y);
      y += 16;
      doc.setFont("helvetica", "normal");
      doc.setFontSize(11);
      doc.setTextColor(30, 30, 30);
      const nextLines = doc.splitTextToSize(recap.nextStep, W - M * 2);
      doc.text(nextLines, M, y);

      // Footer
      const H = doc.internal.pageSize.getHeight();
      doc.setDrawColor(220, 220, 220);
      doc.line(M, H - 60, W - M, H - 60);
      doc.setFont("helvetica", "normal");
      doc.setFontSize(8);
      doc.setTextColor(140, 140, 140);
      doc.text("Non-digital space is a human right.", M, H - 40);
      doc.text("stackd.app", W - M, H - 40, { align: "right" });

      const safeCode = roomCode.replace(/[^A-Z0-9]/gi, "");
      doc.save(`stackd-recap-${safeCode}-${new Date(recap.generatedAt).toISOString().slice(0, 10)}.pdf`);
      toast.success("Recap exported.");
    } catch (e) {
      console.error("pdf_export_failed", e);
      toast.error("Couldn't export the recap.");
    } finally {
      setExporting(false);
    }
  };

  return (
    <div className="border border-ember/25 rounded-2xl p-6 sm:p-8 bg-gradient-to-br from-ember/[0.05] via-transparent to-transparent">
      <div className="flex items-center justify-between mb-6 gap-3 flex-wrap">
        <div className="flex items-center gap-3">
          <span className="size-1.5 rounded-full bg-ember animate-pulse" />
          <h3 className="font-mono text-[10px] tracking-[0.3em] text-ember uppercase">
            AI / SESSION_RECAP
          </h3>
        </div>
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={load}
            disabled={loading}
            className="font-mono text-[10px] uppercase tracking-widest text-silver-dim hover:text-ember transition-colors disabled:opacity-50"
          >
            {loading ? "Composing…" : "Regenerate →"}
          </button>
          <button
            type="button"
            onClick={exportPdf}
            disabled={!recap || exporting || loading}
            className="btn-ember inline-flex items-center gap-2 px-4 py-2 rounded-lg font-mono text-[10px] uppercase tracking-widest font-bold border border-ember/50 text-silver hover:text-ember disabled:opacity-50 disabled:cursor-not-allowed"
            aria-label="Download session recap as PDF"
          >
            {exporting ? "Exporting…" : "Download PDF"}
          </button>
        </div>
      </div>

      {loading && !recap ? (
        <div className="space-y-3">
          <div className="h-5 w-3/4 bg-white/5 rounded animate-pulse" />
          <div className="h-3 bg-white/5 rounded animate-pulse" />
          <div className="h-3 bg-white/5 rounded animate-pulse" />
          <div className="h-3 w-5/6 bg-white/5 rounded animate-pulse" />
        </div>
      ) : error ? (
        <div className="font-mono text-xs text-muted-foreground uppercase tracking-widest">
          Signal lost.{" "}
          <button type="button" onClick={load} className="underline hover:text-ember">
            Retry
          </button>
        </div>
      ) : recap ? (
        <div className="space-y-5">
          <h4 className="text-2xl font-bold tracking-tight text-silver text-balance">
            {recap.title}
          </h4>
          <p className="text-sm text-silver-dim leading-relaxed text-balance">
            {recap.summary}
          </p>
          <div className="space-y-2 border-l border-ember/30 pl-4">
            {recap.reflections.map((r, i) => (
              <p key={i} className="text-sm text-silver-dim leading-relaxed">
                {r}
              </p>
            ))}
          </div>
          <div className="pt-3 border-t border-white/5">
            <div className="font-mono text-[10px] tracking-[0.3em] text-ember uppercase mb-2">
              Next
            </div>
            <p className="text-sm text-silver leading-relaxed">{recap.nextStep}</p>
          </div>
        </div>
      ) : null}
    </div>
  );
}
