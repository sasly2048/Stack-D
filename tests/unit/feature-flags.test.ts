import { describe, expect, it, beforeEach } from "vitest";
import {
  CORE_ROUTES,
  LAB_ROUTES,
  isLabRoute,
  routeVisible,
  labsEnabled,
  setLabsEnabled,
} from "@/lib/feature-flags";

describe("feature flags", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("keeps core and lab routes disjoint", () => {
    for (const core of CORE_ROUTES) {
      expect(isLabRoute(core)).toBe(false);
    }
    for (const lab of LAB_ROUTES) {
      expect(isLabRoute(lab)).toBe(true);
    }
  });

  it("hides lab routes when labs are off and shows them when on", () => {
    expect(routeVisible("/vault", false)).toBe(false);
    expect(routeVisible("/vault", true)).toBe(true);
    expect(routeVisible("/dashboard", false)).toBe(true);
  });

  it("persists the labs flag", () => {
    expect(labsEnabled()).toBe(false);
    setLabsEnabled(true);
    expect(labsEnabled()).toBe(true);
    setLabsEnabled(false);
    expect(labsEnabled()).toBe(false);
  });
});
