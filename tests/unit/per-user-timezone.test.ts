import { readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

/**
 * Day/hour boundaries were UTC-only; the DNA screen (tz-aware) and
 * refresh_personality (UTC) disagreed. This unifies them on a stored
 * profiles.timezone, read via user_timezone() with a safe fallback.
 */
const migration = readFileSync(
  join(process.cwd(), "supabase", "migrations", "20260824030000_per_user_timezone.sql"),
  "utf8",
);

describe("per-user timezone", () => {
  it("adds the timezone column defaulting to UTC", () => {
    expect(migration).toMatch(
      /ADD COLUMN IF NOT EXISTS timezone text NOT NULL DEFAULT 'UTC'/,
    );
  });

  it("resolves the zone with a safe fallback (bad value never throws AT TIME ZONE)", () => {
    // user_timezone joins pg_timezone_names so an invalid stored value yields
    // NULL -> COALESCE to 'UTC'.
    expect(migration).toMatch(/CREATE OR REPLACE FUNCTION public\.user_timezone\(_user_id uuid\)/);
    expect(migration).toMatch(/JOIN pg_timezone_names z ON z\.name = p\.timezone/);
    expect(migration).toMatch(/'UTC'\s*\n?\s*\);/);
  });

  it("validates the setter against real IANA zones", () => {
    expect(migration).toMatch(/CREATE OR REPLACE FUNCTION public\.set_my_timezone\(_tz text\)/);
    expect(migration).toMatch(/NOT EXISTS \(SELECT 1 FROM pg_timezone_names WHERE name = _tz\)/);
    expect(migration).toMatch(/RAISE EXCEPTION 'invalid_timezone'/);
  });

  it("computes every day/hour boundary in the user's zone, not UTC", () => {
    // No day/hour computation should reference the hardcoded UTC literal
    // anymore (the header comment mentions it as prior behavior; the code must
    // not). Both computation forms are gone:
    expect(migration).not.toMatch(/now\(\) AT TIME ZONE 'UTC'/);
    expect(migration).not.toMatch(/created_at AT TIME ZONE 'UTC'/);
    // daily reward, challenges, and personality all read the resolved zone.
    expect(migration).toMatch(/_today DATE := \(now\(\) AT TIME ZONE _tz\)::DATE;/); // claim_daily_reward
    expect(migration).toMatch(/AT TIME ZONE public\.user_timezone\(_user_id\)/); // evaluate_challenges
    expect(migration).toMatch(/EXTRACT\(HOUR FROM created_at AT TIME ZONE _tz\)/); // refresh_personality
  });

  it("keeps the setter callable by users but not anon", () => {
    expect(migration).toMatch(
      /REVOKE ALL ON FUNCTION public\.set_my_timezone\(text\) FROM PUBLIC, anon/,
    );
    expect(migration).toMatch(/GRANT EXECUTE ON FUNCTION public\.set_my_timezone\(text\) TO authenticated/);
  });
});
