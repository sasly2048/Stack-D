import { afterEach, describe, expect, it, vi } from "vitest";

import { publicDbError } from "@/lib/db-error";

/**
 * P2-20: write-path errors must not leak Postgres/PostgREST internals (table,
 * constraint, RLS-policy names) to the client. publicDbError returns a generic
 * code and keeps the raw detail server-side only.
 */
describe("publicDbError", () => {
  afterEach(() => vi.restoreAllMocks());

  it("returns an Error whose message is the generic code, not the raw detail", () => {
    const raw = {
      message: 'new row violates row-level security policy for table "memory_vault_items"',
      code: "42501",
      details: "Failing row contains (secret-uuid, ...)",
      hint: "check policy vault_owner_elite",
    };
    const spy = vi.spyOn(console, "error").mockImplementation(() => {});
    const err = publicDbError(raw, "db_write_failed");

    expect(err).toBeInstanceOf(Error);
    expect(err.message).toBe("db_write_failed");
    // None of the sensitive strings survive into what the client sees.
    expect(err.message).not.toContain("memory_vault_items");
    expect(err.message).not.toContain("row-level security");
    expect(err.message).not.toContain("secret-uuid");
    // But the raw detail IS logged server-side for debugging.
    expect(spy).toHaveBeenCalledOnce();
    expect(String(spy.mock.calls[0])).toContain("memory_vault_items");
  });

  it("tolerates a null error", () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    expect(publicDbError(null, "db_write_failed").message).toBe("db_write_failed");
  });
});
