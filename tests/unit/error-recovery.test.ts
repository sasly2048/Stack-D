import { describe, expect, it } from "vitest";

import { classifyRouteError } from "@/lib/error-recovery";

/**
 * The route error boundary shows a full-screen "Runtime Exception" only for
 * fatal errors. Misclassifying a recoverable blip as fatal means users see a
 * red screen on ordinary reloads; misclassifying a real crash as recoverable
 * means a broken app retries forever. These pin the boundaries.
 */
describe("classifyRouteError", () => {
  it("treats network / auth / abort blips as silent (retry)", () => {
    for (const msg of [
      "The operation was aborted",
      "Failed to fetch",
      "NetworkError when attempting to fetch resource",
      "JWT expired",
      "401 Unauthorized",
      "429 Too Many Requests",
      "503 Service Unavailable",
      "signal is aborted without reason",
    ]) {
      expect(classifyRouteError(new Error(msg)), msg).toBe("silent");
    }
  });

  it("treats hydration/transition races as silent (self-heal on retry)", () => {
    expect(classifyRouteError(new Error("Transition was aborted because of invalid state"))).toBe(
      "silent",
    );
    expect(classifyRouteError(new TypeError("Cannot read properties of undefined (reading 'mount')"))).toBe(
      "silent",
    );
  });

  it("reloads once for a stale chunk after a redeploy", () => {
    for (const msg of [
      "Failed to fetch dynamically imported module: https://x/app.js",
      "error loading dynamically imported module",
      "ChunkLoadError",
    ]) {
      expect(classifyRouteError(new Error(msg)), msg).toBe("reload");
    }
  });

  it("still treats a genuine crash as fatal", () => {
    expect(classifyRouteError(new TypeError("Cannot read properties of undefined (reading 'id')"))).toBe(
      "fatal",
    );
    expect(classifyRouteError(new Error("something specific broke in scoring"))).toBe("fatal");
  });

  it("retries a truly-empty/opaque error rather than showing a red screen", () => {
    // A value carrying no message text at all → one quiet retry.
    expect(classifyRouteError(null)).toBe("silent");
    expect(classifyRouteError(undefined)).toBe("silent");
  });
});
