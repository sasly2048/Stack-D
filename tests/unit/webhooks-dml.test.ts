import { readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

/**
 * P1 (Codex #25): a client must not be able to INSERT/UPDATE webhooks directly
 * (which would bypass the SSRF URL validation in createWebhook). DML is revoked;
 * the server functions write via the admin client after validating.
 */
const migration = readFileSync(
  join(process.cwd(), "supabase", "migrations", "20260825020000_webhooks_revoke_client_dml.sql"),
  "utf8",
);
const fn = readFileSync(join(process.cwd(), "src", "lib", "webhooks.functions.ts"), "utf8");

describe("webhooks direct-DML lockdown", () => {
  it("revokes INSERT/UPDATE from client roles", () => {
    expect(migration).toMatch(/REVOKE INSERT, UPDATE ON public\.webhooks FROM authenticated, anon/);
  });

  it("createWebhook still validates the URL as a public http(s) endpoint", () => {
    expect(fn).toMatch(/isPublicHttpUrl/);
  });

  it("create + toggle write through the admin client (post-validation)", () => {
    // both handlers import supabaseAdmin and write with it
    const adminWrites = fn.match(/supabaseAdmin[\s\S]*?\.from\("webhooks"\)/g) ?? [];
    expect(adminWrites.length).toBeGreaterThanOrEqual(2);
    // toggle stays scoped to the owner (admin bypasses RLS)
    expect(fn).toMatch(/\.update\(\{ active: data\.active \}\)[\s\S]*?\.eq\("user_id", context\.userId\)/);
  });
});
