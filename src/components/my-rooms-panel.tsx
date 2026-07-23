import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "@tanstack/react-router";
import { useServerFn } from "@tanstack/react-start";
import { listMyRooms } from "@/lib/room.functions";
import { formatDuration } from "@/lib/room";

type Item = {
  id: string;
  code: string;
  status: "lobby" | "active" | "complete" | "aborted";
  target_duration_seconds: number;
  started_at: string | null;
  ended_at: string | null;
  created_at: string;
  is_host: boolean;
};

type PageData = { items: Item[]; page: number; pageSize: number; hasMore: boolean };

const PAGE_SIZE = 8;

/**
 * Paginated list of the caller's rooms with automatic prefetch of the next
 * page. As soon as page N lands, page N+1 is fetched in the background and
 * stashed in a ref-keyed cache — Next becomes an instant swap instead of a
 * roundtrip. Keeps discovery snappy at scale without over-fetching.
 */
export function MyRoomsPanel() {
  const fetchPage = useServerFn(listMyRooms);
  const [page, setPage] = useState(0);
  const [current, setCurrent] = useState<PageData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  // page -> data cache (survives Next/Prev without re-fetching)
  const cacheRef = useRef<Map<number, PageData>>(new Map());
  // in-flight prefetches keyed by page number
  const inflightRef = useRef<Map<number, Promise<PageData>>>(new Map());

  const load = useCallback(
    async (p: number): Promise<PageData> => {
      const cached = cacheRef.current.get(p);
      if (cached) return cached;
      const inflight = inflightRef.current.get(p);
      if (inflight) return inflight;
      const promise = fetchPage({ data: { page: p, pageSize: PAGE_SIZE } })
        .then((res) => {
          const data = res as PageData;
          cacheRef.current.set(p, data);
          return data;
        })
        .finally(() => {
          inflightRef.current.delete(p);
        });
      inflightRef.current.set(p, promise);
      return promise;
    },
    [fetchPage],
  );

  // Load current page + kick off next-page prefetch in the background.
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(false);
    load(page)
      .then((data) => {
        if (cancelled) return;
        setCurrent(data);
        setLoading(false);
        // Prefetch next page silently — ignore its errors, they'll re-throw on Next.
        if (data.hasMore) {
          void load(page + 1).catch(() => {
            /* silent — surfaced only when the user actually navigates */
          });
        }
      })
      .catch(() => {
        if (cancelled) return;
        setError(true);
        setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [page, load]);

  const nextReady = current?.hasMore && cacheRef.current.has(page + 1);

  return (
    <div className="md:col-span-6 p-8 bg-white/2 border border-white/5 rounded-2xl">
      <div className="flex items-center justify-between mb-6">
        <h3 className="font-mono text-[10px] tracking-[0.3em] text-muted-foreground uppercase">
          MY_ROOMS
        </h3>
        <div className="font-mono text-[10px] uppercase tracking-widest text-muted-foreground/70">
          {current ? (
            <>
              Page <span className="text-silver">{current.page + 1}</span>
              {nextReady ? <span className="text-ember"> · next cached</span> : null}
            </>
          ) : null}
        </div>
      </div>

      {loading && !current ? (
        <div className="space-y-2">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="h-12 rounded bg-white/5 animate-pulse" />
          ))}
        </div>
      ) : error ? (
        <div className="font-mono text-xs text-muted-foreground uppercase tracking-widest py-6 text-center">
          Signal lost.{" "}
          <button
            type="button"
            onClick={() => {
              cacheRef.current.delete(page);
              setPage((p) => p);
              // trigger re-run: bounce state
              setLoading(true);
              load(page)
                .then((d) => {
                  setCurrent(d);
                  setLoading(false);
                })
                .catch(() => {
                  setError(true);
                  setLoading(false);
                });
            }}
            className="underline hover:text-silver"
          >
            Retry
          </button>
        </div>
      ) : current && current.items.length === 0 ? (
        <p className="font-mono text-xs text-muted-foreground uppercase tracking-widest py-8 text-center">
          No rooms yet. <Link to="/start" className="text-silver underline">Start one.</Link>
        </p>
      ) : current ? (
        <>
          <div className="space-y-1">
            {current.items.map((r) => {
              const elapsed =
                r.started_at && r.ended_at
                  ? Math.max(
                      0,
                      Math.floor(
                        (new Date(r.ended_at).getTime() - new Date(r.started_at).getTime()) / 1000,
                      ),
                    )
                  : null;
              return (
                <Link
                  key={r.id}
                  to="/room/$code"
                  params={{ code: r.code }}
                  className="grid grid-cols-[auto_1fr_auto_auto] items-center gap-4 px-4 py-3 rounded border border-transparent hover:border-white/10 hover:bg-white/5 transition-colors"
                >
                  <span
                    className={`size-1.5 rounded-full ${
                      r.status === "active"
                        ? "bg-pulse animate-pulse"
                        : r.status === "lobby"
                        ? "bg-ember"
                        : r.status === "complete"
                        ? "bg-silver/50"
                        : "bg-white/20"
                    }`}
                  />
                  <span className="font-mono text-sm text-silver">{r.code}</span>
                  <span className="font-mono text-[10px] uppercase tracking-widest text-muted-foreground">
                    {r.is_host ? "HOST" : "GUEST"} · {r.status}
                  </span>
                  <span className="font-mono text-xs tabular-nums text-silver-dim">
                    {elapsed !== null
                      ? formatDuration(elapsed)
                      : formatDuration(r.target_duration_seconds)}
                  </span>
                </Link>
              );
            })}
          </div>

          {(current.page > 0 || current.hasMore) && (
            <div className="flex items-center justify-between mt-6 pt-4 border-t border-white/5">
              <button
                type="button"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={current.page === 0}
                className="font-mono text-[10px] uppercase tracking-widest text-silver-dim hover:text-silver disabled:opacity-30 disabled:cursor-not-allowed"
              >
                ← Prev
              </button>
              <button
                type="button"
                onClick={() => setPage((p) => p + 1)}
                disabled={!current.hasMore}
                className="font-mono text-[10px] uppercase tracking-widest text-silver-dim hover:text-silver disabled:opacity-30 disabled:cursor-not-allowed"
              >
                Next →
              </button>
            </div>
          )}
        </>
      ) : null}
    </div>
  );
}
