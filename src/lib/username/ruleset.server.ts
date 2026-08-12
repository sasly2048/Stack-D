/**
 * Loads the active moderation ruleset from the database so lists can be
 * updated (or a category rolled back to an earlier version) without touching
 * the matching logic or shipping a new build.
 *
 * The bundled config in ./terms is the fallback: if the tables are empty or
 * unreachable we still moderate, just with the compiled-in copy.
 */
import { buildRuleset, type ModerationRuleset } from "./match";
import type { MatchMode, ModerationCategory } from "./terms";
import { FALLBACK_RULESET } from "./validate";

const TTL_MS = 5 * 60 * 1000;
let cache: { at: number; ruleset: ModerationRuleset } | null = null;

export async function loadModerationRuleset(): Promise<ModerationRuleset> {
  if (cache && Date.now() - cache.at < TTL_MS) return cache.ruleset;
  try {
    const { supabaseAdmin } = await import("@/integrations/supabase/client.server");
    const [terms, allow, versions] = await Promise.all([
      supabaseAdmin
        .from("moderation_terms")
        .select("category, term, match_mode")
        .eq("active", true),
      supabaseAdmin.from("moderation_allowlist").select("canonical"),
      supabaseAdmin.from("moderation_list_versions").select("version"),
    ]);

    if (terms.error || !terms.data?.length) return FALLBACK_RULESET;

    const version = Math.max(
      1,
      ...(versions.data ?? []).map((v: { version: number }) => v.version),
    );
    const ruleset = buildRuleset(
      terms.data.map((t: { category: string; term: string; match_mode: string }) => ({
        category: t.category as ModerationCategory,
        term: t.term,
        mode: t.match_mode as MatchMode,
      })),
      (allow.data ?? []).map((a: { canonical: string }) => a.canonical),
      version,
    );
    cache = { at: Date.now(), ruleset };
    return ruleset;
  } catch {
    return FALLBACK_RULESET;
  }
}

/** Drop the cache (used after list edits). */
export function invalidateModerationCache(): void {
  cache = null;
}
