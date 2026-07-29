/**
 * Room-code domain logic — shared by the landing UI and the `validateRoomCode`
 * server function.
 *
 * This lives outside `*.functions.ts` on purpose: TanStack's server-function
 * splitting strips runtime siblings from those modules, so every helper the
 * handler needs at runtime must be imported, not declared alongside it.
 */

export type ValidateErrorCode =
  | "invalid_format"
  | "not_found"
  | "closed"
  | "rate_limited"
  | "server_error";

/** Client-only outcomes on top of the server codes. */
export type CodeError = null | ValidateErrorCode | "network";

export const ROOM_CODE_LENGTH = 6;
export const ROOM_CODE_PATTERN = /^[A-Z0-9]{6}$/;

/** Fold anything the user can paste/autofill into a canonical 6-char key. */
export function normalizeCode(raw: unknown): string {
  if (typeof raw !== "string") return "";
  return raw
    .normalize("NFKC")
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "")
    .slice(0, ROOM_CODE_LENGTH);
}

export function isValidRoomCode(raw: unknown): boolean {
  return ROOM_CODE_PATTERN.test(normalizeCode(raw));
}

/* -------------------------------------------------------------------------- */
/*  Per-Worker positive cache                                                  */
/* -------------------------------------------------------------------------- */

export type PositiveEntry = { code: string; status: "lobby" | "active"; expires: number };

export const POSITIVE_TTL_MS = 30_000;
const POSITIVE_MAX = 500;
const positiveCache = new Map<string, PositiveEntry>();

/** Recent successful resolution, or null when absent/expired. Negative
 *  outcomes are never cached so a lobby going active is picked up next try. */
export function getPositive(code: string, now = Date.now()): PositiveEntry | null {
  const hit = positiveCache.get(code);
  if (!hit) return null;
  if (now > hit.expires) {
    positiveCache.delete(code);
    return null;
  }
  return hit;
}

export function setPositive(code: string, status: "lobby" | "active", now = Date.now()) {
  positiveCache.set(code, { code, status, expires: now + POSITIVE_TTL_MS });
  if (positiveCache.size > POSITIVE_MAX) {
    const oldest = positiveCache.keys().next().value;
    if (oldest) positiveCache.delete(oldest);
  }
}

/** Test seam — never called from app code. */
export function _clearPositiveCache() {
  positiveCache.clear();
}

/* -------------------------------------------------------------------------- */
/*  Error copy                                                                 */
/* -------------------------------------------------------------------------- */

export interface ErrorCopy {
  msg: string;
  retry: string;
  loading: string;
  canRetry: boolean;
}

export const ERROR_COPY: Record<Exclude<CodeError, null>, ErrorCopy> = {
  invalid_format: {
    msg: "Invalid code — need 6 characters",
    retry: "Check the code and try again",
    loading: "Re-checking format…",
    canRetry: false,
  },
  not_found: {
    msg: "No room with that key",
    retry: "Double-check with your host",
    loading: "Looking again…",
    canRetry: false,
  },
  closed: {
    msg: "That session has already ended",
    retry: "Ask for a fresh code",
    loading: "Re-verifying status…",
    canRetry: false,
  },
  rate_limited: {
    msg: "Slow down — too many attempts",
    retry: "Wait a moment, then retry",
    loading: "Waiting on the rate limit…",
    canRetry: true,
  },
  server_error: {
    msg: "Server hiccup on our end",
    retry: "Tap retry",
    loading: "Trying the protocol again…",
    canRetry: true,
  },
  network: {
    msg: "Couldn't reach the protocol",
    retry: "Check your connection, then retry",
    loading: "Reconnecting…",
    canRetry: true,
  },
};
