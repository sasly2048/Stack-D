import { describe, expect, it } from "vitest";

import { CSP, SECURITY_HEADERS, withSecurityHeaders } from "@/lib/security-headers";

/**
 * A CSP fails silently: omit an origin and the browser blocks it while the page
 * still looks fine, so sign-in or realtime breaks with no error anyone reports.
 * These assert every origin the app actually uses is permitted, and that the
 * dangerous directives stay locked.
 */

const directive = (name: string) =>
  CSP.split(";")
    .map((d) => d.trim())
    .find((d) => d.startsWith(`${name} `) || d === name) ?? "";

describe("CSP allows every origin the app genuinely uses", () => {
  it("permits Supabase REST and realtime websockets", () => {
    // wss: is separate from https: in CSP. Missing it kills realtime — the
    // room silently stops receiving breaches while the timer keeps counting.
    expect(directive("connect-src")).toContain("https://*.supabase.co");
    expect(directive("connect-src")).toContain("wss://*.supabase.co");
  });

  it("permits Turnstile to load, run and frame", () => {
    // The widget needs all three: a script, an iframe, and a verify call.
    expect(directive("script-src")).toContain("https://challenges.cloudflare.com");
    expect(directive("frame-src")).toContain("https://challenges.cloudflare.com");
    expect(directive("connect-src")).toContain("https://challenges.cloudflare.com");
  });

  it("permits Google Fonts stylesheet and font files from their separate hosts", () => {
    expect(directive("style-src")).toContain("https://fonts.googleapis.com");
    expect(directive("font-src")).toContain("https://fonts.gstatic.com");
  });

  it("permits the AI gateway", () => {
    expect(directive("connect-src")).toContain("https://ai.gateway.lovable.dev");
  });

  it("permits Razorpay Checkout to load, frame and call home", () => {
    // The checkout script, its payment iframe, and its API/telemetry calls each
    // sit under a different directive. Miss one and checkout fails with
    // "Failed to load Razorpay Checkout" (script-src) or a blank/blocked modal.
    expect(directive("script-src")).toContain("https://checkout.razorpay.com");
    expect(directive("frame-src")).toContain("https://api.razorpay.com");
    expect(directive("connect-src")).toContain("https://*.razorpay.com");
  });

  it("permits user avatars from arbitrary https hosts", () => {
    expect(directive("img-src")).toContain("https:");
    expect(directive("img-src")).toContain("data:");
  });
});

describe("CSP keeps the dangerous directives locked", () => {
  it("never allows eval'd script", () => {
    // 'unsafe-eval' is never required by this stack. 'unsafe-inline' IS —
    // TanStack Start's streaming hydration script is inline and per-render, and
    // this framework version exposes no nonce hook. Enforcing without it was
    // browser-tested and blocked hydration on every page.
    expect(directive("script-src")).not.toContain("unsafe-eval");
  });

  it("keeps connect-src strict, which is what limits inline script's blast radius", () => {
    // Since script-src must allow inline, connect-src carries the weight: an
    // injected script can run but cannot phone home, so it cannot exfiltrate a
    // session token. A wildcard here would undo that.
    const sources = directive("connect-src").replace("connect-src ", "").split(" ");
    // A subdomain wildcard on a specific host (https://*.supabase.co) is fine —
    // it is scoped to one vendor. What must never appear is a bare `*`, a bare
    // scheme like `https:`, or plaintext http:, any of which would let an
    // injected script post a stolen token anywhere.
    expect(sources).not.toContain("*");
    expect(sources).not.toContain("https:");
    expect(sources).not.toContain("http:");
    expect(sources.some((s) => s.startsWith("http://"))).toBe(false);
    expect(sources).toContain("'self'");
  });

  it("blocks framing entirely", () => {
    expect(directive("frame-ancestors")).toBe("frame-ancestors 'none'");
  });

  it("blocks plugins and restricts form targets and base href", () => {
    expect(directive("object-src")).toBe("object-src 'none'");
    expect(directive("form-action")).toBe("form-action 'self'");
    expect(directive("base-uri")).toBe("base-uri 'self'");
  });

  it("has a default-src fallback so unlisted directives are not open", () => {
    expect(directive("default-src")).toBe("default-src 'self'");
  });

  it("is enforcing, not report-only", () => {
    expect(Object.keys(SECURITY_HEADERS)).toContain("Content-Security-Policy");
    expect(Object.keys(SECURITY_HEADERS)).not.toContain(
      "Content-Security-Policy-Report-Only",
    );
  });
});

describe("withSecurityHeaders", () => {
  it("applies every header to a response", () => {
    const res = withSecurityHeaders(new Response("ok"));
    for (const name of Object.keys(SECURITY_HEADERS)) {
      expect(res.headers.get(name), name).toBeTruthy();
    }
  });

  it("preserves status, statusText and body", async () => {
    const res = withSecurityHeaders(
      new Response("not found", { status: 404, statusText: "Not Found" }),
    );
    expect(res.status).toBe(404);
    expect(await res.text()).toBe("not found");
  });

  it("keeps headers the response already set", () => {
    // A route deliberately setting its own policy must not be overridden.
    const res = withSecurityHeaders(
      new Response("ok", { headers: { "X-Frame-Options": "SAMEORIGIN" } }),
    );
    expect(res.headers.get("X-Frame-Options")).toBe("SAMEORIGIN");
  });

  it("does not lose existing unrelated headers", () => {
    const res = withSecurityHeaders(
      new Response("{}", { headers: { "content-type": "application/json" } }),
    );
    expect(res.headers.get("content-type")).toBe("application/json");
  });

  it("works on a response with immutable headers", () => {
    // Responses from fetch() have guarded headers; mutating them throws at
    // runtime, which is why the implementation clones.
    const immutable = new Response("x", { headers: { "x-test": "1" } });
    Object.freeze(immutable.headers);
    expect(() => withSecurityHeaders(immutable)).not.toThrow();
  });
});
