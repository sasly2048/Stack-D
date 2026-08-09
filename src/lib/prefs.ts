/**
 * Small, durable user preferences kept on the device.
 *
 * These are the details that make an app feel like it remembers you: which
 * provider you signed in with last, whether you've been here before, the
 * session length you keep picking. None of it is worth a round-trip or a
 * server column — it is per-device by nature, and a wrong value must never
 * break anything, so every read is defensive and every miss degrades to "no
 * opinion".
 *
 * Deliberately not DataStore/IndexedDB: these are a handful of short strings
 * read during first paint, where localStorage's synchronous read is the point.
 */

const NS = "stackd:";

type Key =
  | "last-auth-provider"
  | "has-visited"
  | "has-completed-session"
  | "last-session-minutes"
  | "last-session-mode"
  | "dismissed-tips";

function read(key: Key): string | null {
  if (typeof localStorage === "undefined") return null;
  try {
    return localStorage.getItem(NS + key);
  } catch {
    // Safari private mode and hardened browser settings both throw here.
    return null;
  }
}

function write(key: Key, value: string): void {
  if (typeof localStorage === "undefined") return;
  try {
    localStorage.setItem(NS + key, value);
  } catch {
    /* quota or blocked storage — a lost preference is never worth an error */
  }
}

/* ---------- auth ---------- */

export type AuthProviderId = "google" | "apple" | "email";

const PROVIDERS: readonly AuthProviderId[] = ["google", "apple", "email"];

/**
 * Which provider signed in successfully last, for the "Last used" hint on the
 * auth screen. Returning users otherwise have to remember which of three
 * buttons is the one tied to their account — and picking wrong silently
 * creates a second identity.
 */
export function getLastAuthProvider(): AuthProviderId | null {
  const v = read("last-auth-provider");
  return PROVIDERS.includes(v as AuthProviderId) ? (v as AuthProviderId) : null;
}

export function setLastAuthProvider(p: AuthProviderId): void {
  write("last-auth-provider", p);
}

/* ---------- first-run ---------- */

/** False on a device's very first visit, so we can greet rather than assume. */
export function hasVisitedBefore(): boolean {
  return read("has-visited") === "1";
}

export function markVisited(): void {
  write("has-visited", "1");
}

/**
 * Whether this device has ever finished a session. Drives first-run coaching:
 * someone who has never held a room needs different copy than a regular.
 */
export function hasCompletedSession(): boolean {
  return read("has-completed-session") === "1";
}

export function markSessionCompleted(): void {
  write("has-completed-session", "1");
}

/* ---------- session setup ---------- */

/** Last duration the user actually started, to pre-select it next time. */
export function getLastSessionMinutes(): number | null {
  const n = Number(read("last-session-minutes"));
  return Number.isFinite(n) && n > 0 && n <= 480 ? n : null;
}

export function setLastSessionMinutes(minutes: number): void {
  write("last-session-minutes", String(minutes));
}

/* ---------- dismissible hints ---------- */

function dismissedSet(): Set<string> {
  const raw = read("dismissed-tips");
  if (!raw) return new Set();
  try {
    const parsed: unknown = JSON.parse(raw);
    return new Set(Array.isArray(parsed) ? parsed.map(String) : []);
  } catch {
    return new Set();
  }
}

export function isTipDismissed(id: string): boolean {
  return dismissedSet().has(id);
}

/** A dismissed hint must stay dismissed — re-showing it reads as a bug. */
export function dismissTip(id: string): void {
  const next = dismissedSet();
  next.add(id);
  write("dismissed-tips", JSON.stringify([...next]));
}
