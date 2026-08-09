import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

/**
 * Permission regression guard for SECURITY DEFINER functions.
 *
 * A SECURITY DEFINER function runs with its owner's rights, bypassing RLS. If
 * one is added without revoking EXECUTE from `anon`, any unauthenticated
 * visitor can invoke privileged logic — and nothing in the build, the
 * typechecker or the test suite would have noticed. That is exactly the class
 * of mistake that stays invisible until it is exploited.
 *
 * This reads the migrations as text rather than connecting to a database, so
 * it runs in CI with no credentials and catches the mistake at review time.
 */

const MIGRATIONS_DIR = join(process.cwd(), "supabase", "migrations");

const sql = readdirSync(MIGRATIONS_DIR)
  .filter((f) => f.endsWith(".sql"))
  .map((f) => ({ file: f, body: readFileSync(join(MIGRATIONS_DIR, f), "utf8") }));

const allSql = sql.map((s) => s.body).join("\n");

/** Every function defined with SECURITY DEFINER, by name. */
function securityDefinerFunctions(): Set<string> {
  const found = new Set<string>();
  for (const { body } of sql) {
    // Match the CREATE ... FUNCTION header through to SECURITY DEFINER, which
    // may sit several option-lines below the signature.
    const re =
      /CREATE\s+(?:OR\s+REPLACE\s+)?FUNCTION\s+public\.([a-z0-9_]+)\s*\([\s\S]*?SECURITY\s+DEFINER/gi;
    let m: RegExpExecArray | null;
    while ((m = re.exec(body)) !== null) found.add(m[1].toLowerCase());
  }
  return found;
}

/** Function names that have EXECUTE revoked from anon (or PUBLIC) somewhere. */
function revokedFunctions(): Set<string> {
  const found = new Set<string>();
  const re = /REVOKE\s+[\s\S]*?ON\s+FUNCTION\s+public\.([a-z0-9_]+)\s*\(/gi;
  let m: RegExpExecArray | null;
  while ((m = re.exec(allSql)) !== null) found.add(m[1].toLowerCase());
  return found;
}

/**
 * Functions that are deliberately callable without a REVOKE.
 *
 * Keep this list short and justified — every entry is a decision to expose
 * privileged logic, and an unexplained addition here is the thing this test
 * exists to make visible.
 */
const INTENTIONALLY_UNREVOKED = new Set<string>([
  // Trigger functions: invoked by Postgres on row events, never by a client.
  // They take no arguments and cannot be usefully called directly.
  "set_updated_at",
  "handle_new_user",
  "update_updated_at_column",
  "trg_set_updated_at",
]);

describe("SECURITY DEFINER permission hygiene", () => {
  const definers = securityDefinerFunctions();
  const revoked = revokedFunctions();

  it("finds the SECURITY DEFINER functions it is meant to guard", () => {
    // A regex that silently matches nothing would make every assertion below
    // vacuously pass.
    expect(definers.size).toBeGreaterThan(10);
  });

  it("revokes EXECUTE from anon on every privileged function", () => {
    const exposed = [...definers]
      .filter((fn) => !revoked.has(fn))
      .filter((fn) => !INTENTIONALLY_UNREVOKED.has(fn))
      // Trigger functions are recognisable by returning a trigger; they are
      // never client-callable regardless of grants.
      .filter((fn) => !new RegExp(`FUNCTION public\\.${fn}\\s*\\(\\s*\\)[\\s\\S]{0,120}RETURNS\\s+trigger`, "i").test(allSql));

    expect(exposed).toEqual([]);
  });

  it("never grants a privileged function directly to anon", () => {
    // GRANT ... TO anon on a SECURITY DEFINER routine hands unauthenticated
    // callers owner-level rights. There is no legitimate use here.
    const grantsToAnon = allSql.match(
      /GRANT\s+EXECUTE\s+ON\s+FUNCTION\s+public\.[a-z0-9_]+[\s\S]{0,200}?TO\s+[^;]*\banon\b/gi,
    );
    expect(grantsToAnon).toBeNull();
  });

  it("pins search_path on every SECURITY DEFINER function", () => {
    // Without SET search_path, the schemas a SECURITY DEFINER body resolves
    // against are chosen by the caller, who can shadow the functions it calls
    // and have their own code run as the owner.
    //
    // Migrations replay in filename order and CREATE OR REPLACE wins, so only
    // the *last* definition of each function is what the database ends up
    // with. Checking every historical definition would flag a function that a
    // later migration already hardened.
    const latest = new Map<string, { file: string; pinned: boolean }>();
    for (const { file, body } of [...sql].sort((a, b) => a.file.localeCompare(b.file))) {
      const re =
        /CREATE\s+(?:OR\s+REPLACE\s+)?FUNCTION\s+public\.([a-z0-9_]+)\s*\(([\s\S]*?)\bAS\s*\$/gi;
      let m: RegExpExecArray | null;
      while ((m = re.exec(body)) !== null) {
        const [, name, header] = m;
        if (!/SECURITY\s+DEFINER/i.test(header)) continue;
        latest.set(name.toLowerCase(), {
          file,
          pinned: /SET\s+search_path/i.test(header),
        });
      }
    }

    const missing = [...latest.entries()]
      .filter(([, v]) => !v.pinned)
      .map(([name, v]) => `${name} (${v.file})`);

    expect(missing).toEqual([]);
  });
});
