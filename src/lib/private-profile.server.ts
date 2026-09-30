import type { SupabaseClient } from "@supabase/supabase-js";

/**
 * The sensitive profile columns (personality analysis + username matching data)
 * are no longer selectable through the API — column-level grants keep them out
 * of every cross-user read. Each caller reads their own via this owner-scoped
 * security-definer function.
 */
export type PrivateProfile = {
  username: string | null;
  username_canonical: string | null;
  username_changed_at: string | null;
  productivity_dna: string | null;
};

export async function fetchMyPrivateProfile(
  supabase: SupabaseClient<never>,
): Promise<PrivateProfile | null> {
  const { data } = await (
    supabase as unknown as {
      rpc: (fn: string) => Promise<{ data: PrivateProfile[] | null }>;
    }
  ).rpc("get_my_private_profile");
  return data?.[0] ?? null;
}
