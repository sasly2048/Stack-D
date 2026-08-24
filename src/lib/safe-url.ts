/**
 * SSRF guard for user-supplied outbound URLs (webhooks).
 * Rejects non-http(s) schemes, loopback/link-local/private hosts and
 * literal private IPs so the server can't be used to probe internal services.
 */

const BLOCKED_HOSTNAMES = new Set([
  "localhost",
  "localhost.localdomain",
  "metadata",
  "metadata.google.internal",
  "instance-data",
  "[::1]",
  "::1",
  "0.0.0.0",
]);

function isPrivateIPv4(host: string): boolean {
  const m = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(host);
  if (!m) return false;
  const octets = [Number(m[1]), Number(m[2]), Number(m[3]), Number(m[4])];
  if (octets.some((n) => n > 255)) return false; // not a valid IPv4 at all
  const [a, b] = octets;
  if (a === 0 || a === 10 || a === 127) return true;
  if (a === 169 && b === 254) return true; // link-local / cloud metadata
  if (a === 172 && b >= 16 && b <= 31) return true;
  if (a === 192 && b === 168) return true;
  if (a === 100 && b >= 64 && b <= 127) return true; // CGNAT
  if (a >= 224) return true; // multicast / reserved
  return false;
}

function isPrivateIPv6(host: string): boolean {
  const h = host.replace(/^\[|\]$/g, "").toLowerCase();
  if (!h.includes(":")) return false;
  if (h === "::" || h === "::1") return true;
  if (h.startsWith("fe80") || h.startsWith("fc") || h.startsWith("fd")) return true;
  if (h.startsWith("::ffff:")) return isPrivateIPv4(h.slice(7));
  return false;
}

/** True if `host` is a private/loopback/link-local IPv4 or IPv6 literal. */
export function isPrivateIp(host: string): boolean {
  return isPrivateIPv4(host) || isPrivateIPv6(host);
}

/** Returns an error code string when the URL is unsafe, otherwise null. */
export function checkPublicHttpUrl(raw: string): string | null {
  let url: URL;
  try {
    url = new URL(raw);
  } catch {
    return "invalid_url";
  }
  if (url.protocol !== "http:" && url.protocol !== "https:") return "unsupported_scheme";
  if (url.username || url.password) return "credentials_not_allowed";

  const host = url.hostname.toLowerCase().replace(/\.$/, "");
  if (!host) return "invalid_host";
  if (BLOCKED_HOSTNAMES.has(host)) return "internal_host_blocked";
  if (host.endsWith(".localhost") || host.endsWith(".internal") || host.endsWith(".local")) {
    return "internal_host_blocked";
  }
  if (isPrivateIPv4(host) || isPrivateIPv6(host)) return "internal_host_blocked";
  return null;
}

export function isPublicHttpUrl(raw: string): boolean {
  return checkPublicHttpUrl(raw) === null;
}
