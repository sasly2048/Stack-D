/**
 * Most errors that reach a route boundary during a reload or a route change are
 * not real application failures: a fetch aborted because the user navigated
 * away, a chunk that 404s after a redeploy, a Supabase call that lost the
 * network for a second, or an auth token that had not hydrated yet. Showing a
 * full-screen "Runtime Exception" for any of those is both alarming and wrong —
 * they all recover on their own if we simply try again.
 */

export type RecoveryKind = "silent" | "reload" | "fatal";

function textOf(error: unknown): string {
  const parts: string[] = [];
  const seen = new Set<unknown>();
  let current: unknown = error;
  while (current && typeof current === "object" && !seen.has(current)) {
    seen.add(current);
    const e = current as { name?: string; message?: string; code?: string; cause?: unknown };
    parts.push(e.name ?? "", e.message ?? "", e.code ?? "");
    current = e.cause;
  }
  if (typeof error === "string") parts.push(error);
  return parts.join(" ").toLowerCase();
}

// A stale bundle reference: the only real cure is a fresh document load.
const RELOAD_PATTERNS =
  /failed to fetch dynamically imported module|error loading dynamically imported module|importing a module script failed|unable to preload|chunkloaderror|'text\/html' is not a valid javascript mime type/;

// Transient: retrying the loader/query is enough. Also covers a couple of
// hydration/transition races that self-heal on a retry: a router transition
// aborted mid-flight ("invalid state"), and the specific provider-mount crash
// seen when a route context is momentarily incomplete during hydration
// ("reading 'mount'"). These are narrow, signature-scoped — a generic "reading
// 'x' of undefined" is still fatal.
const SILENT_PATTERNS =
  /\baborted\b|aborterror|the operation was aborted|network ?error|failed to fetch|load failed|econnreset|etimedout|networkerror when attempting|no authorization header|unauthorized|jwt expired|401|429|503|504|fetch failed|signal is aborted|transition was aborted|invalid state|reading 'mount'|reading "mount"/;

export function classifyRouteError(error: unknown): RecoveryKind {
  const text = textOf(error);
  if (RELOAD_PATTERNS.test(text)) return "reload";
  if (SILENT_PATTERNS.test(text)) return "silent";
  // An empty/opaque error carries no signal that anything is genuinely broken;
  // one quiet retry is friendlier than a red screen.
  if (!text.trim()) return "silent";
  return "fatal";
}

const RELOAD_FLAG = "stackd:chunk-reload";

/** Reload once per session for a stale-chunk error, so we can't loop forever. */
export function reloadOnceForStaleChunk(): boolean {
  if (typeof window === "undefined") return false;
  try {
    if (sessionStorage.getItem(RELOAD_FLAG)) return false;
    sessionStorage.setItem(RELOAD_FLAG, "1");
  } catch {
    /* private mode — still worth one reload attempt */
  }
  window.location.reload();
  return true;
}

export function clearStaleChunkFlag() {
  if (typeof window === "undefined") return;
  try {
    sessionStorage.removeItem(RELOAD_FLAG);
  } catch {
    /* noop */
  }
}
