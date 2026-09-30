import { describe, expect, it, vi } from "vitest";
import type { SupabaseClient } from "@supabase/supabase-js";
import { AiNotEntitled, passiveAi } from "@/lib/ai-passive";

/** Minimal fake: one cache row (or none) and a fixed entitlement. */
function fake(opts: { cached?: unknown; entitlement?: Record<string, unknown> | null }) {
  const upserts: unknown[] = [];
  const chain = {
    eq: () => chain,
    maybeSingle: async () => ({
      data: opts.cached === undefined ? null : { payload: opts.cached },
    }),
  };
  const client = {
    from: () => ({
      select: () => chain,
      upsert: async (row: unknown) => {
        upserts.push(row);
        return { error: null };
      },
    }),
    rpc: async () => ({ data: opts.entitlement ? [opts.entitlement] : [], error: null }),
  };
  return { supabase: client as unknown as SupabaseClient, upserts };
}

describe("passiveAi (tier-capped passive AI)", () => {
  it("serves a cached answer without re-checking tier or calling the model", async () => {
    const { supabase } = fake({ cached: { topic: "cached" } });
    const generate = vi.fn();
    await expect(passiveAi(supabase, "u1", "recommend", "s1", generate)).resolves.toEqual({
      topic: "cached",
    });
    expect(generate).not.toHaveBeenCalled();
  });

  it("refuses free users so the surface falls back to deterministic copy", async () => {
    const { supabase } = fake({
      entitlement: { is_premium: false, is_admin: false, source: "none" },
    });
    const generate = vi.fn();
    await expect(passiveAi(supabase, "u1", "recommend", "s1", generate)).rejects.toBeInstanceOf(
      AiNotEntitled,
    );
    expect(generate).not.toHaveBeenCalled();
  });

  it("generates and caches for paid, admin and lifetime users", async () => {
    for (const ent of [
      { is_premium: true },
      { is_admin: true },
      { source: "lifetime", is_premium: false },
    ]) {
      const { supabase, upserts } = fake({ entitlement: ent });
      const out = await passiveAi(supabase, "u1", "proactive", "s2", async () => ({ ok: 1 }));
      expect(out).toEqual({ ok: 1 });
      expect(upserts).toHaveLength(1);
    }
  });
});
