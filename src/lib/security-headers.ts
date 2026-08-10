/**
 * Security headers, applied in the server entry.
 *
 * These lived in `public/_headers` first, which was wrong: that is a Cloudflare
 * **Pages** convention, and this app deploys as a Cloudflare **Worker** (Nitro's
 * cloudflare_module preset). Workers do not interpret `_headers` — they served
 * it as a static asset. Confirmed against production: GET /_headers returned the
 * file's contents with HTTP 200, and none of its rules appeared on any response.
 *
 * So the headers looked correct in review, passed CI, and did nothing for a day.
 * Setting them here means they travel with every response the Worker returns.
 */

/** Origins this app genuinely talks to, verified against the built bundle. */
const SUPABASE = "https://*.supabase.co";
const SUPABASE_WS = "wss://*.supabase.co";
const TURNSTILE = "https://challenges.cloudflare.com";
const GOOGLE_FONTS_CSS = "https://fonts.googleapis.com";
const GOOGLE_FONTS_FILES = "https://fonts.gstatic.com";
const AI_GATEWAY = "https://ai.gateway.lovable.dev";

/**
 * The policy.
 *
 * `style-src` needs 'unsafe-inline': Tailwind ships inline critical CSS and the
 * app sets inline styles for animation. That weakens style protection only —
 * script injection, the dangerous case, stays fully blocked.
 *
 * `frame-ancestors 'none'` supersedes X-Frame-Options in modern browsers; the
 * older header is still sent for anything that predates CSP Level 2.
 */
const CSP_DIRECTIVES = [
  "default-src 'self'",
  // 'unsafe-inline' is required, not preferred. TanStack Start's streaming SSR
  // emits an inline hydration script (`<script class="$tsr"
  // id="$tsr-stream-barrier">`) whose content changes per render, so a hash
  // allowlist would need regenerating every response. A nonce is the correct
  // tool, but this version of the framework exposes no way to stamp one onto
  // its internal scripts (verified: no `nonce` option in @tanstack/react-start
  // or the router's config).
  //
  // Enforcing without this was tested in a browser and blocked hydration on
  // every page — the app would have shipped broken while still appearing to
  // render. Revisit if TanStack Start gains nonce support; that single change
  // would restore full XSS protection here.
  //
  // What this still buys: an injected script may execute, but it cannot reach
  // an attacker's server, because connect-src is a strict allowlist. Data
  // exfiltration — the actual payoff of most XSS — stays blocked.
  `script-src 'self' 'unsafe-inline' ${TURNSTILE}`,
  `style-src 'self' 'unsafe-inline' ${GOOGLE_FONTS_CSS}`,
  `font-src 'self' ${GOOGLE_FONTS_FILES} data:`,
  // https: on img-src because avatars are user-supplied and come from arbitrary
  // hosts; blob:/data: cover generated share images and inline icons.
  "img-src 'self' data: blob: https:",
  `connect-src 'self' ${SUPABASE} ${SUPABASE_WS} ${TURNSTILE} ${AI_GATEWAY}`,
  `frame-src ${TURNSTILE}`,
  "frame-ancestors 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "object-src 'none'",
  "upgrade-insecure-requests",
].join("; ");

const HEADERS: Record<string, string> = {
  "Content-Security-Policy": CSP_DIRECTIVES,
  // Clickjacking. /auth takes Google, Apple and email credentials; without this
  // it can be framed invisibly and overlaid so a user types real credentials
  // into someone else's UI.
  "X-Frame-Options": "DENY",
  "X-Content-Type-Options": "nosniff",
  "Referrer-Policy": "strict-origin-when-cross-origin",
  // Nothing here needs these. Denying by default means a future dependency
  // cannot quietly start asking for them.
  "Permissions-Policy": "camera=(), microphone=(), geolocation=(), payment=(), usb=()",
  "Cross-Origin-Opener-Policy": "same-origin-allow-popups",
  "Strict-Transport-Security": "max-age=31536000; includeSubDomains",
};

/**
 * Returns the response with security headers applied.
 *
 * Existing values win: a route that deliberately sets its own header (a
 * relaxed CSP for an embed, say) should not be silently overridden here.
 * Headers are only added where absent.
 */
export function withSecurityHeaders(response: Response): Response {
  // A Response from fetch can have immutable headers, so clone rather than
  // mutate — mutating throws a TypeError at runtime, not at build time.
  const headers = new Headers(response.headers);
  for (const [name, value] of Object.entries(HEADERS)) {
    if (!headers.has(name)) headers.set(name, value);
  }
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}

/** Exported for tests — the policy is easier to assert than to eyeball. */
export const CSP = CSP_DIRECTIVES;
export const SECURITY_HEADERS = HEADERS;
