/**
 * Public user handles.
 *
 * We never surface raw provider data (avatar CDN URLs, provider ids) in the UI.
 * Instead each account gets a short, stable, human identifier derived from its
 * display name with a deterministic suffix from the user id for uniqueness.
 */
function slugify(input: string): string {
  return input
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "")
    .slice(0, 15);
}

/** Deterministic 4-char suffix so two "raghav"s never collide. */
function suffix(id: string): string {
  let h = 0;
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) >>> 0;
  return h.toString(36).padStart(4, "0").slice(-4);
}

/** Returns a handle without the leading "@". */
export function userHandle(id: string, displayName?: string | null): string {
  const base = slugify(displayName ?? "") || "stacker";
  return `${base}${suffix(id)}`;
}

/** Returns a display-ready handle, e.g. "@raghav1f2c". */
export function formatHandle(id: string, displayName?: string | null): string {
  return `@${userHandle(id, displayName)}`;
}
