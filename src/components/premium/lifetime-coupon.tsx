import { useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { toast } from "sonner";

import { redeemLifetime } from "@/lib/subscription.functions";
import { useEntitlement, invalidateEntitlement } from "@/lib/use-entitlement";
import { feedback } from "@/lib/feedback";

/**
 * Lifetime redemption on the profile page. If the user already holds lifetime,
 * nothing renders here (the badge is shown by <LifetimeBadge> next to the name).
 * Otherwise a single coupon box — the code itself is set later by the operator.
 */
export function LifetimeCoupon() {
  const { entitlement } = useEntitlement();
  const redeem = useServerFn(redeemLifetime);
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);

  // Already lifetime (or admin) — nothing to redeem.
  if (!entitlement || entitlement.source === "lifetime" || entitlement.isAdmin) return null;

  const onRedeem = async () => {
    if (!code.trim() || busy) return;
    setBusy(true);
    try {
      const { result, message } = await redeem({ data: { code } });
      if (result === "ok") {
        feedback("success");
        toast.success(message);
        invalidateEntitlement();
        setCode("");
      } else {
        feedback("error");
        toast.error(message);
      }
    } catch (e: unknown) {
      feedback("error");
      toast.error(e instanceof Error ? e.message : "Redemption failed");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-3">
      <h2 className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">
        Redeem a code
      </h2>
      <div className="flex gap-2">
        <input
          value={code}
          onChange={(e) => setCode(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && onRedeem()}
          placeholder="Coupon code"
          className="flex-1 rounded-md border border-white/10 bg-black/50 px-3 py-2 text-sm text-silver placeholder:text-silver-dim focus:outline-none focus:border-ember/40"
        />
        <button
          onClick={onRedeem}
          disabled={!code.trim() || busy}
          className="rounded-full border border-ember/50 px-5 font-mono text-[11px] uppercase tracking-widest text-ember transition hover:bg-ember/10 disabled:opacity-40"
        >
          {busy ? "…" : "Redeem"}
        </button>
      </div>
    </div>
  );
}

/**
 * Profile membership tag. Shows the caller's premium standing next to the name:
 * Admin, Lifetime member, Elite, or Pro. Nothing for free users. Lifetime beats
 * the plain tier label since it's the more meaningful status.
 */
export function LifetimeBadge() {
  const { entitlement } = useEntitlement();
  if (!entitlement || !entitlement.isPremium) return null;

  const label = entitlement.isAdmin
    ? "Admin"
    : entitlement.source === "lifetime"
      ? "Lifetime member"
      : entitlement.tier === "elite"
        ? "Elite"
        : "Pro";

  return (
    <span className="rounded-full border border-ember/50 bg-ember/10 px-2.5 py-0.5 font-mono text-[10px] uppercase tracking-widest text-ember">
      {label}
    </span>
  );
}
