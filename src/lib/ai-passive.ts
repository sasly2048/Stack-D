import type { SupabaseClient } from "@supabase/supabase-js";

/**
 * Tier cap for PASSIVE AI surfaces — recommend, dashboard insights, proactive
 * insights, session recap. These render on page load, so they are gated by
 * tier rather than metered against the monthly action allowance (which would
 * drain on page views and lock paid users out of the Companion):
 *
 *   free                    → throws AiNotEntitled; the caller's existing
 *                             catch returns its deterministic, non-AI result
 *   pro / elite / admin /   → AI, generated at most once per (kind, scope)
 *   lifetime                  and served from ai_response_cache afterwards
 *
 * `scope` must change when the answer should change — the latest session's
 * timestamp for history-based surfaces, the room id for a recap. Explicit
 * actions (Companion, vault summary, weekly story) stay on withAiBudget.
 */
export type PassiveKind = "recommend" | "dashboard_insights" | "proactive" | "session_recap";

export class AiNotEntitled extends Error {
  constructor() {
    super("AI features are available on Pro and Elite.");
    this.name = "AiNotEntitled";
  }
}

// The cache table isn't in the generated types yet; keep the untyped surface
// in one place.
type Untyped = {
  from: (t: string) => {
    select: (c: string) => {
      eq: (
        k: string,
        v: string,
      ) => {
        eq: (
          k: string,
          v: string,
        ) => {
          eq: (k: string, v: string) => { maybeSingle: () => Promise<{ data: unknown }> };
        };
      };
    };
    upsert: (row: Record<string, unknown>) => Promise<unknown>;
  };
  rpc: (fn: string) => Promise<{ data: unknown; error: unknown }>;
};

/** True when the caller's tier includes AI (paid, admin, or lifetime). */
export async function isAiEntitled(supabase: SupabaseClient): Promise<boolean> {
  const { data, error } = await (supabase as unknown as Untyped).rpc("my_entitlement");
  if (error) return false; // fail closed: no paid call without a verified tier
  const row = (Array.isArray(data) ? data[0] : data) as {
    is_premium?: boolean;
    is_admin?: boolean;
    source?: string;
  } | null;
  return !!(row?.is_premium || row?.is_admin || row?.source === "lifetime");
}

/**
 * Returns the cached AI payload for (userId, kind, scope) or generates it with
 * [generate] when the caller is entitled. Throws [AiNotEntitled] for free
 * users so the surface falls back to its deterministic copy.
 */
export async function passiveAi<T>(
  supabase: SupabaseClient,
  userId: string,
  kind: PassiveKind,
  scope: string,
  generate: () => Promise<T>,
): Promise<T> {
  const db = supabase as unknown as Untyped;
  const { data: hit } = await db
    .from("ai_response_cache")
    .select("payload")
    .eq("user_id", userId)
    .eq("kind", kind)
    .eq("scope", scope)
    .maybeSingle();
  if (hit && typeof hit === "object" && "payload" in hit) {
    return (hit as { payload: T }).payload;
  }

  if (!(await isAiEntitled(supabase))) throw new AiNotEntitled();

  const fresh = await generate();
  // Best-effort: a failed cache write must not lose the answer we paid for.
  // (Postgrest builders are thenables that report errors instead of throwing.)
  try {
    await db.from("ai_response_cache").upsert({
      user_id: userId,
      kind,
      scope,
      payload: fresh as unknown as Record<string, unknown>,
      created_at: new Date().toISOString(),
    });
  } catch {
    // ignore
  }
  return fresh;
}
