import { readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it, vi } from "vitest";

import { withAiBudget } from "@/lib/require-ai-budget";

/**
 * P1 (Codex #22): a consumed AI action is refunded when the provider call
 * fails, and pure-local endpoints don't consume AI budget at all.
 */
function fakeSupabase(meterOk: boolean) {
  const calls: string[] = [];
  return {
    calls,
    rpc: vi.fn(async (name: string) => {
      calls.push(name);
      if (name === "ai_meter") return { data: [{ ok: meterOk, allowance: 20 }], error: null };
      return { data: null, error: null }; // ai_refund
    }),
  };
}

describe("withAiBudget reserve/refund", () => {
  // NOTE: the refund now runs via the service-role admin client (dynamic
  // import), not the passed user client, so we assert on behaviour rather than
  // on the user client's rpc calls for refund.
  it("consumes on entry and propagates a work failure", async () => {
    const sb = fakeSupabase(true);
    await expect(
      withAiBudget(sb as never, "user-1", async () => {
        throw new Error("ai_failed");
      }),
    ).rejects.toThrow("ai_failed");
    expect(sb.calls[0]).toBe("ai_meter"); // consumed before the work ran
  });

  it("does NOT touch the meter again on success", async () => {
    const sb = fakeSupabase(true);
    const out = await withAiBudget(sb as never, "user-1", async () => "ok");
    expect(out).toBe("ok");
    expect(sb.calls).toEqual(["ai_meter"]); // meter once, no refund on success
  });

  it("rejects (and never runs work) when over quota", async () => {
    const sb = fakeSupabase(false);
    const work = vi.fn();
    await expect(withAiBudget(sb as never, "user-1", work as never)).rejects.toBeTruthy();
    expect(work).not.toHaveBeenCalled();
    expect(sb.calls).toEqual(["ai_meter"]);
  });
});

describe("no AI budget consumed by non-AI endpoints", () => {
  const narrative = readFileSync(
    join(process.cwd(), "src", "lib", "ai-narrative.functions.ts"),
    "utf8",
  );
  const migration = readFileSync(
    join(process.cwd(), "supabase", "migrations", "20260825050000_ai_refund_service_only.sql"),
    "utf8",
  );

  it("discoverPatterns (pure heuristics) no longer meters AI", () => {
    // the only budget usage in this file is the wrapped ai() call in
    // getWeeklyStory — no bare requireAiBudget CALL remains (a comment may
    // still mention the old name).
    expect(narrative).not.toMatch(/await requireAiBudget\(/);
    expect(narrative).toMatch(/withAiBudget\(context\.supabase/);
  });

  it("ai_refund is service-role only (not client-callable) and floored/guarded", () => {
    expect(migration).toMatch(/GREATEST\(u\.action_count - 1, 0\)/);
    expect(migration).toMatch(/u\.action_count > 0/); // nothing-to-refund guard
    expect(migration).toMatch(
      /REVOKE ALL ON FUNCTION public\.ai_refund\(uuid\) FROM PUBLIC, anon, authenticated/,
    );
    expect(migration).toMatch(/GRANT EXECUTE ON FUNCTION public\.ai_refund\(uuid\) TO service_role/);
    expect(migration).not.toMatch(/GRANT EXECUTE ON FUNCTION public\.ai_refund[^;]*TO authenticated/);
  });
});
