import { describe, expect, it } from "vitest";

import { checkPublicHttpUrl, isPrivateIp } from "@/lib/safe-url";

/**
 * P1 #18: webhook SSRF. The string check blocks literal internal hosts; the
 * send-time guard (safe-url.server.ts) additionally resolves DNS so a public
 * hostname pointing at an internal IP is rejected. These cover the literal
 * layer + the private-IP classifier the DNS guard relies on.
 */
describe("webhook SSRF guards", () => {
  it("classifies cloud-metadata and private IPs as private", () => {
    // 169.254.169.254 is the AWS/GCP metadata endpoint — the prize target of
    // webhook SSRF; must be caught both as a literal and as a DNS result.
    expect(isPrivateIp("169.254.169.254")).toBe(true);
    expect(isPrivateIp("10.0.0.1")).toBe(true);
    expect(isPrivateIp("172.16.5.4")).toBe(true);
    expect(isPrivateIp("192.168.1.1")).toBe(true);
    expect(isPrivateIp("127.0.0.1")).toBe(true);
    expect(isPrivateIp("100.64.0.1")).toBe(true); // CGNAT
    expect(isPrivateIp("::1")).toBe(true);
    expect(isPrivateIp("fd00::1")).toBe(true);
    expect(isPrivateIp("::ffff:10.0.0.1")).toBe(true); // v4-mapped v6
  });

  it("treats real public IPs as public", () => {
    expect(isPrivateIp("8.8.8.8")).toBe(false);
    expect(isPrivateIp("1.1.1.1")).toBe(false);
    expect(isPrivateIp("172.32.0.1")).toBe(false); // just outside 172.16/12
  });

  it("rejects an out-of-range octet as not-an-IPv4 (fails to public, not blocked-as-private)", () => {
    // 999.1.1.1 is not a valid v4 literal; classifier must not mislabel it.
    expect(isPrivateIp("999.1.1.1")).toBe(false);
  });

  it("string check blocks internal literals and bad schemes", () => {
    expect(checkPublicHttpUrl("http://localhost/x")).toBe("internal_host_blocked");
    expect(checkPublicHttpUrl("http://169.254.169.254/latest/meta-data")).toBe(
      "internal_host_blocked",
    );
    expect(checkPublicHttpUrl("http://foo.internal/")).toBe("internal_host_blocked");
    expect(checkPublicHttpUrl("file:///etc/passwd")).toBe("unsupported_scheme");
    expect(checkPublicHttpUrl("http://user:pass@example.com/")).toBe("credentials_not_allowed");
  });

  it("string check lets a public URL through (DNS guard is the next layer)", () => {
    expect(checkPublicHttpUrl("https://hooks.example.com/endpoint")).toBeNull();
  });
});
