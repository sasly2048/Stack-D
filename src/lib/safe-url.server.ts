import { lookup } from "node:dns/promises";

import { checkPublicHttpUrl, isPrivateIp } from "@/lib/safe-url";

/**
 * Send-time SSRF guard that closes the DNS gap the string check can't see.
 *
 * checkPublicHttpUrl() only inspects the URL literal, so `http://evil.com`
 * passes even when evil.com resolves to 10.0.0.1 or the cloud-metadata IP
 * 169.254.169.254. Here we resolve the hostname and reject if ANY returned
 * address is private/link-local, before fetch() ever connects.
 *
 * ponytail: checks-then-fetches, so a rebinding DNS that flips between the
 * lookup and the connect still has a narrow window. Blocking the common case
 * (a domain that statically points inward) is the 99% fix; the upgrade path is
 * pinning fetch to the validated IP via a custom undici dispatcher.
 */
export async function assertPublicUrl(raw: string): Promise<void> {
  const literalProblem = checkPublicHttpUrl(raw);
  if (literalProblem) throw new Error(literalProblem);

  const host = new URL(raw).hostname.toLowerCase().replace(/\.$/, "");

  // A literal IP already went through checkPublicHttpUrl; only hostnames need
  // resolving. (lookup on an IP just echoes it, but skip the syscall.)
  if (/^[\d.]+$/.test(host) || host.includes(":")) return;

  let addrs: { address: string }[];
  try {
    addrs = await lookup(host, { all: true });
  } catch {
    throw new Error("dns_resolution_failed");
  }
  if (addrs.length === 0) throw new Error("dns_resolution_failed");
  for (const { address } of addrs) {
    if (isPrivateIp(address)) throw new Error("internal_host_blocked");
  }
}
