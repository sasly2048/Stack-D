import { useEffect, useMemo, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { toast } from "sonner";
import { Check } from "lucide-react";

import { getEntitlement, getPlans, type Plan } from "@/lib/subscription.functions";
import { createSubscription } from "@/lib/razorpay.functions";
import { openRazorpayCheckout } from "@/lib/razorpay-checkout";
import { annualSavingsPct } from "@/lib/entitlement-rules";
import { PREMIUM_FEATURES, TIER_TAGLINE } from "@/lib/premium-catalog";
import {
  invalidateEntitlement,
  pollEntitlementUntilPremium,
  refreshEntitlement,
} from "@/lib/use-entitlement";
import { cn } from "@/lib/utils";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { CelebratePro } from "./celebrate-pro";
import { CelebrateElite } from "./celebrate-elite";

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
  const loadEntitlement = useServerFn(getEntitlement);
  const startCheckout = useServerFn(createSubscription);
  const [plans, setPlans] = useState<Plan[]>([]);
  const [interval, setInterval] = useState<Interval>("annual");
  const [checkingOut, setCheckingOut] = useState<string | null>(null);
  const [celebrate, setCelebrate] = useState<"pro" | "elite" | null>(null);

  const onCheckout = async (plan: Plan) => {
    if (checkingOut) return;
    setCheckingOut(plan.id);
    try {
      const { subscriptionId, keyId } = await startCheckout({ data: { planId: plan.id } });
      const tier = plan.tier === "elite" ? "elite" : "pro";
      // Close our Radix dialog BEFORE opening Razorpay. Radix keeps a focus trap
      // and sets pointer-events on everything outside its content while open;
      // Razorpay's checkout iframe is appended to <body>, outside that tree, so
      // leaving our dialog open makes the Razorpay modal unclickable (clicks
      // fall through to the page). Closing first hands pointer control to
      // Razorpay cleanly.
      onOpenChange(false);
      await openRazorpayCheckout({
        keyId,
        subscriptionId,
        description: `${plan.displayName} · Stack'd`,
        onSuccess: async () => {
          // Payment done client-side. Poll until the webhook has written the
          // subscription (server-authoritative), pushing the fresh entitlement
          // to every mounted component so the UI flips with no page refresh.
          const ok = await pollEntitlementUntilPremium(loadEntitlement);
          if (ok) {
            // Tier-specific celebration — Pro and Elite are entirely separate
            // sequences, not one recolored animation.
            setCelebrate(tier);
          } else {
            // Webhook hasn't landed yet; entitlement will catch up on next load.
            toast.success("Payment received — unlocking your account shortly.");
          }
        },
        onDismiss: () => {
          // Cancelled or closed pre-success: refetch in case it actually went
          // through, but don't celebrate.
          void refreshEntitlement(loadEntitlement).catch(() => invalidateEntitlement());
        },
      });
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "Checkout failed");
    } finally {
      setCheckingOut(null);
    }
  };

  useEffect(() => {
    if (!open) return;
    loadPlans()
      .then(setPlans)
      .catch(() => undefined);
  }, [open, loadPlans]);

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
    <>
      <CelebratePro open={celebrate === "pro"} onClose={() => setCelebrate(null)} />
      <CelebrateElite open={celebrate === "elite"} onClose={() => setCelebrate(null)} />
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
                    interval === iv
                      ? "bg-ember/15 text-ember"
                      : "text-silver-dim hover:text-silver",
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
              onCheckout={onCheckout}
              checkingOut={checkingOut}
            />
            <TierColumn
              name="Elite"
              tagline={TIER_TAGLINE.elite}
              best
              plan={interval === "annual" ? byTier.eliteAnnual : byTier.eliteMonthly}
              monthly={byTier.eliteMonthly}
              annual={byTier.eliteAnnual}
              interval={interval}
              onCheckout={onCheckout}
              checkingOut={checkingOut}
            />
          </div>

          {/* Feature comparison */}
          <div className="px-6 pb-5">
            <p className={eyebrow}>What&apos;s included</p>
            <ul className="mt-3 space-y-1.5">
              {PREMIUM_FEATURES.map((f) => (
                <li key={f.key} className="flex items-center gap-2.5 text-sm">
                  <Check className="size-3.5 text-ember shrink-0" />
                  <span className="text-silver">{f.uiLabel}</span>
                  {f.requiredTier === "elite" && (
                    <span className="ml-auto font-mono text-[9px] uppercase tracking-widest text-ember-glow">
                      Elite
                    </span>
                  )}
                </li>
              ))}
            </ul>
          </div>

          <p className="px-6 pb-6 text-center text-[11px] text-silver-dim">
            Cancel anytime. No auto-renewal surprises — we tell you before every charge.
          </p>
        </DialogContent>
      </Dialog>
    </>
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
  onCheckout,
  checkingOut,
}: {
  name: string;
  tagline: string;
  plan?: Plan;
  monthly?: Plan;
  annual?: Plan;
  interval: Interval;
  best?: boolean;
  onCheckout: (plan: Plan) => void;
  checkingOut: string | null;
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
        onClick={() => plan && onCheckout(plan)}
        disabled={!plan?.id || checkingOut !== null}
        className="mt-4 w-full rounded-full border border-ember/50 text-ember font-mono text-[11px] uppercase tracking-widest py-2 disabled:opacity-40 hover:bg-ember/10 transition"
      >
        {checkingOut === plan?.id ? "Opening…" : `Choose ${name}`}
      </button>
    </div>
  );
}
