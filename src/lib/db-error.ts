/**
 * Sanitize a Supabase/Postgres write error before it reaches the client.
 *
 * Raw PostgREST/Postgres messages on INSERT/UPDATE/DELETE/RPC leak schema
 * internals — table and constraint names, and RLS-policy text like
 * `new row violates row-level security policy for table "vault_items"`. We log
 * the full detail server-side (where it's useful for debugging) and throw a
 * short, generic code the UI can map to a friendly message.
 *
 * Reads are left alone: PostgREST read errors are already generic and a failed
 * SELECT rarely carries constraint detail.
 */
export interface DbLikeError {
  message?: string;
  code?: string;
  details?: string;
  hint?: string;
}

export function publicDbError(error: DbLikeError | null, publicCode: string): Error {
  // Full detail to the server log only.
  console.error(
    `[db] ${publicCode}:`,
    JSON.stringify({
      message: error?.message,
      code: error?.code,
      details: error?.details,
      hint: error?.hint,
    }),
  );
  return new Error(publicCode);
}
