import { useEffect, useMemo, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { toast } from "sonner";
import { Check } from "lucide-react";

import {
  getLifetimePromoStatus,
  getPlans,
  redeemLifetime,
  type LifetimePromoStatus,
  type Plan,
} from "@/lib/subscription.functions";
import { annualSavingsPct } from "@/lib/entitlement-rules";
import { PREMIUM_FEATURES, TIER_TAGLINE } from "@/lib/premium-catalog";
import { invalidateEntitlement } from "@/lib/use-entitlement";
import { haptic } from "@/lib/haptics";
import { cn } from "@/lib/utils";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";

type Interval = "monthly" | "annual";

const eyebrow = "font-mono text-[10px] tracking-[0.3em] uppercase text-ember";

export function UpgradeDialog({
  open,
  onOpenChange,
  reason,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  /** Optional contextual line, e.g. "Unlimited history is a Pro feature". */
  reason?: string;
}) {
  const loadPlans = useServerFn(getPlans);
  const loadPromo = useServerFn(getLifetimePromoStatus);
  const [plans, setPlans] = useState<Plan[]>([]);
  const [promo, setPromo] = useState<LifetimePromoStatus | null>(null);
  const [interval, setInterval] = useState<Interval>("annual");

  useEffect(() => {
    if (!open) return;
    loadPlans()
      .then(setPlans)
      .catch(() => undefined);
    loadPromo()
      .then(setPromo)
      .catch(() => undefined);
  }, [open, loadPlans, loadPromo]);

  const byTier = useMemo(() => {
    const pick = (tier: string, iv: Interval) =>
      plans.find((p) => p.tier === tier && p.interval === iv);
    return {
      proMonthly: pick("pro", "monthly"),
      proAnnual: pick("pro", "annual"),
      eliteMonthly: pick("elite", "monthly"),
      eliteAnnual: pick("elite", "annual"),
    };
  }, [plans]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl border-border bg-card p-0 gap-0 overflow-hidden">
        {/* Header */}
        <div className="px-6 pt-6 pb-5 border-b border-border">
          <p className={eyebrow}>Stack&apos;d Premium</p>
          <DialogTitle className="mt-2 font-serif text-2xl text-silver font-normal">
            Go deeper on your focus
          </DialogTitle>
          <DialogDescription className="mt-1 text-sm text-silver-dim">
            {reason ?? "Unlock advanced analytics, unlimited history, and more."}
          </DialogDescription>

          {/* Monthly / Annual toggle */}
          <div className="mt-5 inline-flex items-center gap-1 rounded-full border border-border p-1">
            {(["monthly", "annual"] as const).map((iv) => (
              <button
                key={iv}
                onClick={() => setInterval(iv)}
                className={cn(
                  "px-4 py-1.5 rounded-full font-mono text-[11px] uppercase tracking-widest transition-colors",
                  interval === iv ? "bg-ember/15 text-ember" : "text-silver-dim hover:text-silver",
                )}
              >
                {iv}
                {iv === "annual" && (
                  <span className="ml-1.5 text-pulse normal-case tracking-normal">save 40%+</span>
                )}
              </button>
            ))}
          </div>
        </div>

        {/* Tiers */}
        <div className="grid sm:grid-cols-2 gap-3 px-6 py-5">
          <TierColumn
            name="Pro"
            tagline={TIER_TAGLINE.pro}
            plan={interval === "annual" ? byTier.proAnnual : byTier.proMonthly}
            monthly={byTier.proMonthly}
            annual={byTier.proAnnual}
            interval={interval}
          />
          <TierColumn
            name="Elite"
            tagline={TIER_TAGLINE.elite}
            best
            plan={interval === "annual" ? byTier.eliteAnnual : byTier.eliteMonthly}
            monthly={byTier.eliteMonthly}
            annual={byTier.eliteAnnual}
            interval={interval}
          />
        </div>

        {/* Feature comparison */}
        <div className="px-6 pb-5">
          <p className={eyebrow}>What&apos;s included</p>
          <ul className="mt-3 space-y-1.5">
            {PREMIUM_FEATURES.map((f) => (
              <li key={f.key} className="flex items-center gap-2.5 text-sm">
                <Check className="size-3.5 text-ember shrink-0" />
                <span className="text-silver">{f.label}</span>
                {f.minTier === "elite" && (
                  <span className="ml-auto font-mono text-[9px] uppercase tracking-widest text-ember-glow">
                    Elite
                  </span>
                )}
              </li>
            ))}
          </ul>
        </div>

        {/* Lifetime promo — only when active */}
        {promo?.active && (
          <LifetimeSection promo={promo} onRedeemed={() => loadPromo().then(setPromo)} />
        )}

        <p className="px-6 pb-6 text-center text-[11px] text-silver-dim">
          Cancel anytime. No auto-renewal surprises — we tell you before every charge.
        </p>
      </DialogContent>
    </Dialog>
  );
}

function TierColumn({
  name,
  tagline,
  plan,
  monthly,
  annual,
  interval,
  best,
}: {
  name: string;
  tagline: string;
  plan?: Plan;
  monthly?: Plan;
  annual?: Plan;
  interval: Interval;
  best?: boolean;
}) {
  const savings = monthly && annual ? annualSavingsPct(monthly.priceInr, annual.priceInr) : 0;
  const perMonth =
    interval === "annual" && annual ? Math.round(annual.priceInr / 12) : plan?.priceInr;

  return (
    <div
      className={cn(
        "rounded-lg border p-4 flex flex-col",
        best ? "border-ember/50 bg-ember/[0.04]" : "border-border bg-white/[0.02]",
      )}
    >
      <div className="flex items-center justify-between">
        <p className="font-serif text-lg text-silver">{name}</p>
        {best && (
          <span className="font-mono text-[9px] uppercase tracking-widest text-ember">
            Best value
          </span>
        )}
      </div>
      <p className="mt-0.5 text-xs text-silver-dim">{tagline}</p>

      <div className="mt-4 flex items-baseline gap-1">
        <span className="font-serif text-2xl text-silver">₹{perMonth ?? "—"}</span>
        <span className="text-xs text-silver-dim">/mo</span>
      </div>
      {interval === "annual" && annual && (
        <p className="mt-1 text-[11px] text-silver-dim">
          ₹{annual.priceInr}/yr billed once
          {savings > 0 && <span className="text-pulse"> · save {savings}%</span>}
        </p>
      )}

      <button
        // Checkout wiring lands with the Razorpay follow-up; disabled for now
        // so nothing looks broken and no charge path exists yet.
        disabled
        className="mt-4 w-full rounded-full border border-ember/50 text-ember font-mono text-[11px] uppercase tracking-widest py-2 disabled:opacity-40"
        title="Payments arrive soon"
      >
        Coming soon
      </button>
    </div>
  );
}

function LifetimeSection({
  promo,
  onRedeemed,
}: {
  promo: LifetimePromoStatus;
  onRedeemed: () => void;
}) {
  const redeem = useServerFn(redeemLifetime);
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const claimedPct = Math.min(
    100,
    Math.round(((promo.seatsTotal - promo.seatsRemaining) / promo.seatsTotal) * 100),
  );

  const onRedeem = async () => {
    if (!code.trim() || busy) return;
    setBusy(true);
    try {
      const { result, message } = await redeem({ data: { code } });
      if (result === "ok") {
        haptic("success");
        toast.success(message);
        invalidateEntitlement();
        onRedeemed();
        setCode("");
      } else {
        toast.error(message);
      }
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "Redemption failed");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-6 mb-5 rounded-lg border border-ember/30 bg-ember/[0.04] p-4">
      <div className="flex items-center justify-between">
        <p className={eyebrow}>Lifetime · limited</p>
        <p className="font-mono text-[11px] text-silver-dim">
          <span className="text-ember">{promo.seatsRemaining}</span> of {promo.seatsTotal} seats
          left
        </p>
      </div>
      <p className="mt-2 text-sm text-silver">
        Elite access, once — no renewals, ever. Have a code?
      </p>

      {/* Seat counter — the signature scarcity bar */}
      <div className="mt-3 h-1 rounded-full bg-white/[0.06] overflow-hidden">
        <div className="h-full bg-ember/70" style={{ width: `${claimedPct}%` }} />
      </div>

      {promo.alreadyRedeemed ? (
        <p className="mt-3 text-xs text-pulse font-mono uppercase tracking-widest">
          ✓ Lifetime unlocked
        </p>
      ) : (
        <div className="mt-3 flex gap-2">
          <input
            value={code}
            onChange={(e) => setCode(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && onRedeem()}
            placeholder="Coupon code"
            className="flex-1 rounded-md border border-border bg-background px-3 py-2 text-sm text-silver placeholder:text-silver-dim focus:outline-none focus:ring-1 focus:ring-ring"
          />
          <button
            onClick={onRedeem}
            disabled={!code.trim() || busy}
            className="rounded-full border border-ember/50 text-ember font-mono text-[11px] uppercase tracking-widest px-4 disabled:opacity-40 hover:bg-ember/10 transition"
          >
            {busy ? "…" : "Redeem"}
          </button>
        </div>
      )}
    </div>
  );
}
