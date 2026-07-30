import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { getMilestones, type MilestoneShelf as Shelf } from "@/lib/milestones.functions";

/**
 * Permanent, never-resetting markers. Rendered as engraved plates that live on
 * the profile forever.
 */
export function MilestoneShelf({ userId }: { userId?: string }) {
  const fetchShelf = useServerFn(getMilestones);
  const [shelf, setShelf] = useState<Shelf | null>(null);

  useEffect(() => {
    let alive = true;
    fetchShelf({ data: userId ? { userId } : {} })
      .then((s) => {
        if (alive) setShelf(s);
      })
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, [userId, fetchShelf]);

  if (!shelf) return null;

  return (
    <section className="border border-white/5 rounded-2xl p-5 sm:p-6">
      <div className="flex items-baseline justify-between mb-4">
        <h2 className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground">
          Lifetime Milestones
        </h2>
        <span className="font-mono text-[10px] tracking-[0.2em] uppercase text-silver-dim">
          {shelf.totalHours}h held
        </span>
      </div>

      {shelf.earned.length === 0 ? (
        <p className="text-xs text-muted-foreground">
          No milestones yet. The first plate is engraved at 100 hours held.
        </p>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2">
          {shelf.earned.map((m) => (
            <li
              key={m.id}
              className="relative overflow-hidden rounded-xl border border-ember/30 bg-gradient-to-br from-ember/[0.08] to-transparent px-4 py-4"
            >
              <p className="font-mono text-[9px] tracking-[0.3em] uppercase text-ember">
                {m.metric === "hours" ? "Hours" : m.metric === "sessions" ? "Sessions" : "Streak"}
              </p>
              <p className="mt-1 font-serif text-3xl text-silver tabular-nums leading-none">
                {m.threshold.toLocaleString()}
              </p>
              <p className="mt-2 text-xs text-silver-dim">{m.description}</p>
              {m.unlocked_at && (
                <p className="mt-2 font-mono text-[9px] tracking-[0.2em] uppercase text-muted-foreground">
                  {new Date(m.unlocked_at).toLocaleDateString(undefined, {
                    year: "numeric",
                    month: "short",
                    day: "numeric",
                  })}
                </p>
              )}
            </li>
          ))}
        </ul>
      )}

      {shelf.next && (
        <div className="mt-4">
          <div className="flex items-center justify-between font-mono text-[10px] tracking-[0.25em] uppercase text-silver-dim">
            <span>Next · {shelf.next.card.name}</span>
            <span>
              {shelf.next.current} / {shelf.next.card.threshold}
            </span>
          </div>
          <div className="mt-2 h-1 w-full rounded-full bg-white/5 overflow-hidden">
            <div
              className="h-full rounded-full bg-ember/70"
              style={{
                width: `${Math.min(100, (shelf.next.current / Math.max(1, shelf.next.card.threshold)) * 100)}%`,
              }}
            />
          </div>
        </div>
      )}
    </section>
  );
}
