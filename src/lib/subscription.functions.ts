import { createServerFn } from "@tanstack/react-start";
import { publicDbError } from "@/lib/db-error";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";

export type AccessTier = "free" | "pro" | "elite";

export interface Entitlement {
  tier: AccessTier;
  isAdmin: boolean;
  isPremium: boolean;
  source: string;
  expiresAt: string | null;
}

export interface LifetimePromoStatus {
  active: boolean;
  seatsTotal: number;
  seatsRemaining: number;
  endsAt: string | null;
  alreadyRedeemed: boolean;
}

export interface Plan {
  id: string;
  tier: AccessTier;
  interval: "monthly" | "annual";
  priceInr: number;
  displayName: string;
}

/**
 * The caller's effective access. Server-authoritative: resolves admin allowlist,
 * lifetime, and (expiry-aware) paid subscriptions inside a SECURITY DEFINER RPC.
 * The frontend only reflects this — it never decides entitlement itself.
 */
// RPC/table names below use `as never` because the generated Supabase
// `Database` types (src/integrations/supabase/types.ts) are regenerated from
// the live DB and don't yet include this migration's objects. This mirrors the
// existing convention in auth.functions.ts / seasons.functions.ts. Once types
// are regenerated the casts are harmless.
interface EntitlementRow {
  tier: AccessTier;
  is_admin: boolean;
  is_premium: boolean;
  source: string;
  expires_at: string | null;
}

export const getEntitlement = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<Entitlement> => {
    const { data, error } = await context.supabase.rpc("my_entitlement" as never);
    if (error) throw publicDbError(error, "db_write_failed");
    const rows = data as unknown as EntitlementRow[];
    const row = rows?.[0];
    if (!row) throw new Error("Entitlement not resolved");
    return {
      tier: row.tier,
      isAdmin: row.is_admin,
      isPremium: row.is_premium,
      source: row.source,
      expiresAt: row.expires_at,
    };
  });

/** Public pricing catalog (active plans only). */
export const getPlans = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<Plan[]> => {
    // `as never` on the table name for the same reason as the RPCs above:
    // `plans` isn't in the generated types until they're regenerated.
    const { data, error } = await context.supabase
      .from("plans" as never)
      .select("id,tier,interval,price_inr,display_name")
      .eq("is_active" as never, true as never)
      .order("sort_order" as never);
    if (error) throw new Error(error.message);
    const rows = (data ?? []) as unknown as PlanRow[];
    return rows.map((p) => ({
      id: p.id,
      tier: p.tier,
      interval: p.interval,
      priceInr: p.price_inr,
      displayName: p.display_name,
    }));
  });

interface PlanRow {
  id: string;
  tier: AccessTier;
  interval: "monthly" | "annual";
  price_inr: number;
  display_name: string;
}

/** Promo visibility for the UI — never exposes the coupon code. */
export const getLifetimePromoStatus = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<LifetimePromoStatus> => {
    const { data, error } = await context.supabase.rpc("lifetime_promo_status" as never);
    if (error) throw publicDbError(error, "db_write_failed");
    const rows = data as unknown as PromoRow[];
    const row = rows?.[0];
    if (!row) throw new Error("Promo status not resolved");
    return {
      active: row.active,
      seatsTotal: row.seats_total,
      seatsRemaining: row.seats_remaining,
      endsAt: row.ends_at,
      alreadyRedeemed: row.already_redeemed,
    };
  });

interface PromoRow {
  active: boolean;
  seats_total: number;
  seats_remaining: number;
  ends_at: string | null;
  already_redeemed: boolean;
}

export type RedeemResult = "ok" | "inactive" | "bad_code" | "sold_out" | "already" | "unauth";

const REDEEM_MESSAGES: Record<RedeemResult, string> = {
  ok: "Lifetime access unlocked. Welcome to Elite, forever.",
  inactive: "This promotion isn't currently active.",
  bad_code: "That coupon code isn't valid.",
  sold_out: "All lifetime seats have been claimed.",
  already: "You've already redeemed lifetime access.",
  unauth: "Please sign in to redeem.",
};

/**
 * Redeem a lifetime coupon. Atomicity, cap, duplicate-guard and code match all
 * live in the RPC (row-locked). This wrapper only maps the status to a message.
 */
export const redeemLifetime = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d: { code: string }) => ({
    code: typeof d?.code === "string" ? d.code.trim().slice(0, 120) : "",
  }))
  .handler(async ({ data, context }): Promise<{ result: RedeemResult; message: string }> => {
    const { data: res, error } = await context.supabase.rpc(
      "redeem_lifetime" as never,
      {
        _code: data.code,
      } as never,
    );
    if (error) throw publicDbError(error, "db_write_failed");
    const result = (res ?? "bad_code") as RedeemResult;
    return { result, message: REDEEM_MESSAGES[result] ?? REDEEM_MESSAGES.bad_code };
  });

export interface AiUsage {
  used: number;
  allowance: number;
  remaining: number;
  unlimited: boolean;
}

/** The caller's AI usage this billing period, for a transparent counter. */
export const getAiUsage = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<AiUsage> => {
    const { data, error } = await context.supabase.rpc("ai_usage_status" as never);
    if (error) throw publicDbError(error, "db_write_failed");
    const row = (Array.isArray(data) ? data[0] : data) as AiUsage | null;
    return row ?? { used: 0, allowance: 0, remaining: 0, unlimited: false };
  });
