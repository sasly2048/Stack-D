import { readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

/**
 * Guards the webhook reliability contract (received -> processing ->
 * processed/failed). The real logic is SQL + the handler, neither runnable
 * without infra here, so this asserts the invariants that keep a failed
 * provisioning retryable instead of silently deduped away.
 */
const migration = readFileSync(
  join(process.cwd(), "supabase", "migrations", "20260818120000_webhook_processing_state.sql"),
  "utf8",
);
const handler = readFileSync(
  join(process.cwd(), "src", "routes", "api", "public", "razorpay-webhook.ts"),
  "utf8",
);

describe("webhook processing state (migration)", () => {
  it("only skips genuinely processed events, not failed/processing ones", () => {
    // begin_webhook_event returns 'processed' (skip) ONLY for a processed row;
    // a 'failed' or 'processing' row must be re-claimable so a retry re-runs.
    expect(migration).toMatch(/RETURN 'processed';\s*--[^\n]*skip/);
    expect(migration).toMatch(/allow retry/i);
  });

  it("locks the row before deciding, to serialize concurrent deliveries", () => {
    expect(migration).toMatch(/FOR UPDATE/);
  });

  it("keeps the state functions server-only (revoked from anon + authenticated)", () => {
    for (const fn of ["begin_webhook_event", "complete_webhook_event", "fail_webhook_event"]) {
      expect(migration).toMatch(
        new RegExp(`REVOKE ALL ON FUNCTION public\\.${fn}[\\s\\S]*?anon, authenticated`),
      );
    }
  });
});

describe("webhook handler ordering", () => {
  it("claims the event before provisioning and completes only after grant", () => {
    const beginIdx = handler.indexOf("begin_webhook_event");
    const grantIdx = handler.indexOf("grant_subscription");
    const completeIdx = handler.indexOf("complete_webhook_event");
    expect(beginIdx).toBeGreaterThan(-1);
    expect(grantIdx).toBeGreaterThan(beginIdx); // claim before grant
    expect(completeIdx).toBeGreaterThan(grantIdx); // complete only after grant
  });

  it("marks the event failed (retryable) when provisioning cannot complete", () => {
    expect(handler).toMatch(/fail_webhook_event/);
    // failAndRetry returns 5xx so Razorpay redelivers.
    expect(handler).toMatch(/failAndRetry[\s\S]*?status: 500/);
  });
});
