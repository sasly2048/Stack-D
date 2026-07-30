import { createFileRoute } from "@tanstack/react-router";
import { useCallback, useEffect, useRef, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { toast } from "sonner";
import { Nav } from "@/components/nav";
import { getWrapped, type WrappedStats } from "@/lib/wrapped.functions";

export const Route = createFileRoute("/_authenticated/wrapped")({
  head: () => ({
    meta: [
      { title: "Stack Wrapped — Stack'd" },
      {
        name: "description",
        content: "Your year of held time: hours, XP, streaks and the people you stacked with.",
      },
      { property: "og:title", content: "Stack Wrapped — Stack'd" },
      {
        property: "og:description",
        content: "Your year of held time, wrapped into one shareable card.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: WrappedPage,
});

const EMBER = "#F0A968";
const OBSIDIAN = "#0A0A0A";
const SILVER = "#E2E2E2";

function drawCard(stats: WrappedStats): HTMLCanvasElement {
  const c = document.createElement("canvas");
  c.width = 1080;
  c.height = 1350;
  const ctx = c.getContext("2d")!;

  ctx.fillStyle = OBSIDIAN;
  ctx.fillRect(0, 0, c.width, c.height);

  const glow = ctx.createRadialGradient(540, 420, 40, 540, 420, 720);
  glow.addColorStop(0, "rgba(240,169,104,0.20)");
  glow.addColorStop(1, "rgba(10,10,10,0)");
  ctx.fillStyle = glow;
  ctx.fillRect(0, 0, c.width, c.height);

  ctx.textAlign = "center";
  ctx.fillStyle = EMBER;
  ctx.font = "600 26px ui-monospace, monospace";
  ctx.fillText(
    `STACK WRAPPED · ${stats.rolling ? "LAST 12 MONTHS" : stats.year}`.split("").join(" "),
    540,
    140,
  );

  ctx.fillStyle = SILVER;
  ctx.font = "700 56px Georgia, serif";
  ctx.fillText(stats.displayName, 540, 230);

  ctx.fillStyle = EMBER;
  ctx.font = "700 220px Georgia, serif";
  ctx.fillText(String(stats.totalHours), 540, 470);
  ctx.fillStyle = SILVER;
  ctx.font = "500 32px ui-monospace, monospace";
  ctx.fillText("HOURS HELD", 540, 525);

  const rows: [string, string][] = [
    ["SESSIONS", stats.totalSessions.toLocaleString()],
    ["XP EARNED", stats.totalXp.toLocaleString()],
    ["LONGEST SESSION", `${stats.longestSessionMinutes} min`],
    ["BEST STREAK", `${stats.bestStreak} days`],
    ["PEAK DAY", stats.topWeekday],
    ["PEAK HOUR", `${String(stats.peakHour).padStart(2, "0")}:00`],
    ["UNBROKEN", `${stats.perfectSessions}`],
    ["TOP ALLY", stats.topCollaborator?.name ?? "—"],
  ];

  ctx.textAlign = "left";
  let y = 640;
  for (const [label, value] of rows) {
    ctx.fillStyle = "rgba(226,226,226,0.45)";
    ctx.font = "500 24px ui-monospace, monospace";
    ctx.fillText(label, 120, y);
    ctx.textAlign = "right";
    ctx.fillStyle = SILVER;
    ctx.font = "600 34px Georgia, serif";
    ctx.fillText(value, 960, y);
    ctx.textAlign = "left";
    ctx.strokeStyle = "rgba(255,255,255,0.07)";
    ctx.beginPath();
    ctx.moveTo(120, y + 22);
    ctx.lineTo(960, y + 22);
    ctx.stroke();
    y += 74;
  }

  ctx.textAlign = "center";
  if (stats.personality) {
    ctx.fillStyle = EMBER;
    ctx.font = "600 36px Georgia, serif";
    ctx.fillText(stats.personality, 540, y + 40);
  }

  ctx.fillStyle = "rgba(226,226,226,0.4)";
  ctx.font = "500 24px ui-monospace, monospace";
  ctx.fillText(`TOP ${Math.max(1, 100 - stats.percentile)}% OF STACKERS`, 540, 1230);
  ctx.fillStyle = SILVER;
  ctx.font = "600 26px ui-monospace, monospace";
  ctx.fillText("S T A C K ' D", 540, 1285);

  return c;
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="border border-white/5 rounded-2xl p-5">
      <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground">
        {label}
      </p>
      <p className="mt-2 font-serif text-3xl text-silver tabular-nums leading-none">{value}</p>
    </div>
  );
}

function WrappedPage() {
  const load = useServerFn(getWrapped);
  const [stats, setStats] = useState<WrappedStats | null>(null);
  const [busy, setBusy] = useState(false);
  const previewRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    let alive = true;
    load()
      .then((s) => {
        if (alive) setStats(s);
      })
      .catch(() => toast.error("Couldn't load your Wrapped."));
    return () => {
      alive = false;
    };
  }, [load]);

  // Render the share card into the preview slot once stats arrive.
  useEffect(() => {
    if (!stats || !previewRef.current) return;
    const canvas = drawCard(stats);
    canvas.className = "w-full rounded-2xl border border-white/10";
    previewRef.current.replaceChildren(canvas);
  }, [stats]);

  const share = useCallback(async () => {
    if (!stats) return;
    setBusy(true);
    try {
      const canvas = drawCard(stats);
      const blob: Blob | null = await new Promise((res) => canvas.toBlob(res, "image/png"));
      if (!blob) throw new Error("render failed");
      const file = new File([blob], "stackd-wrapped.png", { type: "image/png" });
      const nav = navigator as Navigator & {
        canShare?: (d: ShareData) => boolean;
        share?: (d: ShareData) => Promise<void>;
      };
      if (nav.canShare?.({ files: [file] }) && nav.share) {
        await nav.share({ files: [file], title: "Stack Wrapped" });
      } else {
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = "stackd-wrapped.png";
        a.click();
        URL.revokeObjectURL(url);
        toast.success("Wrapped card downloaded.");
      }
    } catch {
      toast.error("Couldn't create the share image.");
    } finally {
      setBusy(false);
    }
  }, [stats]);

  return (
    <div className="min-h-screen bg-obsidian">
      <Nav />
      <main className="max-w-4xl mx-auto px-4 sm:px-6 pt-28 pb-24">
        <p className="font-mono text-[10px] tracking-[0.35em] uppercase text-ember">
          Stack Wrapped {stats?.rolling ? "· Last 12 months" : stats ? `· ${stats.year}` : ""}
        </p>
        <h1 className="mt-4 font-serif text-5xl sm:text-7xl text-silver leading-[0.95]">
          {stats ? `${stats.totalHours} hours` : "Your year"}
          <span className="block text-ember">held.</span>
        </h1>

        {stats && (
          <>
            <p className="mt-6 max-w-xl text-silver-dim">
              You stacked {stats.totalSessions.toLocaleString()} sessions, earned{" "}
              {stats.totalXp.toLocaleString()} XP, and held the line best on {stats.topWeekday}s
              around {String(stats.peakHour).padStart(2, "0")}:00.
            </p>

            <div className="mt-10 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              <Stat label="Sessions" value={stats.totalSessions.toLocaleString()} />
              <Stat label="XP earned" value={stats.totalXp.toLocaleString()} />
              <Stat label="Longest session" value={`${stats.longestSessionMinutes} min`} />
              <Stat label="Best streak" value={`${stats.bestStreak} days`} />
              <Stat label="Unbroken sessions" value={String(stats.perfectSessions)} />
              <Stat label="Flow states" value={String(stats.flowSessions)} />
              <Stat label="Peak day" value={stats.topWeekday} />
              <Stat
                label="Top ally"
                value={stats.topCollaborator ? stats.topCollaborator.name : "—"}
              />
              <Stat label="Percentile" value={`Top ${Math.max(1, 100 - stats.percentile)}%`} />
            </div>

            {stats.personality && (
              <p className="mt-10 font-serif text-3xl text-ember">{stats.personality}</p>
            )}

            <div className="mt-12">
              <h2 className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground mb-4">
                Share card
              </h2>
              <div ref={previewRef} className="max-w-sm" />
              <button
                type="button"
                onClick={share}
                disabled={busy}
                className="mt-5 rounded-full border border-ember px-8 py-3 font-mono text-[11px] tracking-[0.3em] uppercase text-ember transition-colors hover:bg-ember/10 disabled:opacity-50"
              >
                {busy ? "Rendering…" : "Share Wrapped"}
              </button>
            </div>
          </>
        )}

        {!stats && (
          <p className="mt-10 font-mono text-xs tracking-[0.2em] uppercase text-muted-foreground">
            Gathering your year…
          </p>
        )}
      </main>
    </div>
  );
}
