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
  it("refunds when the work throws (provider failure)", async () => {
    const sb = fakeSupabase(true);
    await expect(
      withAiBudget(sb as never, async () => {
        throw new Error("ai_failed");
      }),
    ).rejects.toThrow("ai_failed");
    expect(sb.calls).toEqual(["ai_meter", "ai_refund"]); // consumed then refunded
  });

  it("does NOT refund on success", async () => {
    const sb = fakeSupabase(true);
    const out = await withAiBudget(sb as never, async () => "ok");
    expect(out).toBe("ok");
    expect(sb.calls).toEqual(["ai_meter"]); // no refund
  });

  it("rejects (and never runs work) when over quota", async () => {
    const sb = fakeSupabase(false);
    const work = vi.fn();
    await expect(withAiBudget(sb as never, work as never)).rejects.toBeTruthy();
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
    join(process.cwd(), "supabase", "migrations", "20260825040000_ai_refund.sql"),
    "utf8",
  );

  it("discoverPatterns (pure heuristics) no longer meters AI", () => {
    // the only budget usage in this file is the wrapped ai() call in
    // getWeeklyStory — no bare requireAiBudget CALL remains (a comment may
    // still mention the old name).
    expect(narrative).not.toMatch(/await requireAiBudget\(/);
    expect(narrative).toMatch(/withAiBudget\(context\.supabase/);
  });

  it("ai_refund floors at zero and is client-callable", () => {
    expect(migration).toMatch(/GREATEST\(action_count - 1, 0\)/);
    expect(migration).toMatch(/GRANT EXECUTE ON FUNCTION public\.ai_refund\(\) TO authenticated/);
  });
});
