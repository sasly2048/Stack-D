/**
 * The canonical public origin for this deployment.
 *
 * Every canonical link, og:url, sitemap <loc> and public SDK example must come
 * from here. They were previously twelve separate string literals all naming
 * the old Lovable preview host, which meant the live site advertised canonicals
 * for a domain it doesn't serve — search engines were being told the real pages
 * were duplicates of somewhere else.
 *
 * Override per-environment with VITE_SITE_URL (e.g. a preview deploy). The
 * fallback is production so a missing env var degrades to correct-for-prod
 * rather than to a dead host.
 */
const FALLBACK_ORIGIN = "https://stackd.raghav.studio";

/** Origin with no trailing slash, e.g. `https://stackd.raghav.studio`. */
export const SITE_URL: string = (import.meta.env?.VITE_SITE_URL || FALLBACK_ORIGIN).replace(
  /\/+$/,
  "",
);

/**
 * The one address a human actually reads.
 *
 * Kept here because it appears in the footer and twice in the privacy policy —
 * including as the route for account-deletion requests, which is the one place
 * a stale address does real harm. It previously pointed at invented
 * `hello@`/`press@` mailboxes on a domain that serves no mail.
 */
export const CONTACT_EMAIL = "raghavendrasujith204800@gmail.com";

/** The project's X account. Handle kept separate so it can label the link. */
export const X_HANDLE = "StackD_HQ";
export const X_URL = `https://x.com/${X_HANDLE}`;

/**
 * Absolute URL for a site-relative path.
 * `siteUrl("/philosophy")` → `https://stackd.raghav.studio/philosophy`
 * `siteUrl("/")` → `https://stackd.raghav.studio/`
 */
export function siteUrl(path = "/"): string {
  return `${SITE_URL}${path.startsWith("/") ? path : `/${path}`}`;
}
