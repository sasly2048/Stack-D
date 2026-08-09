import { describe, expect, it } from "vitest";

import { resolveBoundaryState } from "@/components/query-states";

/**
 * Locks in the precedence that was the actual bug on four screens: feed,
 * friends, trust and capsule each checked `rows.length === 0` with no loading
 * guard, so a user with data was told "Your circle is empty" on every slow
 * fetch. Loading must beat empty; error must beat empty.
 */
describe("resolveBoundaryState", () => {
  it("prefers loading over empty — the regression that caused the bug", () => {
    expect(resolveBoundaryState({ isPending: true, isEmpty: true, hasEmptyUi: true })).toBe(
      "loading",
    );
  });

  it("prefers loading over error", () => {
    expect(resolveBoundaryState({ isPending: true, isError: true })).toBe("loading");
  });

  it("prefers error over empty, so a failure never reads as 'no data'", () => {
    expect(
      resolveBoundaryState({ isPending: false, isError: true, isEmpty: true, hasEmptyUi: true }),
    ).toBe("error");
  });

  it("shows empty only once loaded, without error, and with empty UI supplied", () => {
    expect(
      resolveBoundaryState({ isPending: false, isEmpty: true, hasEmptyUi: true }),
    ).toBe("empty");
  });

  it("falls through to content when a screen supplies no empty UI", () => {
    expect(resolveBoundaryState({ isPending: false, isEmpty: true, hasEmptyUi: false })).toBe(
      "content",
    );
  });

  it("shows content when loaded with data", () => {
    expect(
      resolveBoundaryState({ isPending: false, isEmpty: false, hasEmptyUi: true }),
    ).toBe("content");
  });
});
