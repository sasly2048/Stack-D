import { useEffect, useMemo, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { toast } from "sonner";
import { Check } from "lucide-react";

import { getEntitlement, getPlans, type Plan } from "@/lib/subscription.functions";
import { createSubscription } from "@/lib/razorpay.functions";
import {
  getSubscriptionDetail,
  type SubscriptionDetail,
} from "@/lib/manage-subscription.functions";
import { openRazorpayCheckout } from "@/lib/razorpay-checkout";
import { annualSavingsPct } from "@/lib/entitlement-rules";
import { PREMIUM_FEATURES, STATUS_LABEL, TIER_TAGLINE } from "@/lib/premium-catalog";
import {
  invalidateEntitlement,
  pollEntitlementUntilPremium,
  refreshEntitlement,
} from "@/lib/use-entitlement";
import { triggerCelebration } from "@/lib/celebration-bus";
import { feedback } from "@/lib/feedback";
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
  const loadEntitlement = useServerFn(getEntitlement);
  const startCheckout = useServerFn(createSubscription);
  const loadSubscription = useServerFn(getSubscriptionDetail);
  const [current, setCurrent] = useState<SubscriptionDetail | null>(null);
  const [plans, setPlans] = useState<Plan[]>([]);
  const [interval, setInterval] = useState<Interval>("annual");
  const [checkingOut, setCheckingOut] = useState<string | null>(null);

  const onCheckout = async (plan: Plan) => {
    if (checkingOut) return;
    feedback("purchase"); // selecting a plan / starting checkout
    setCheckingOut(plan.id);
    try {
      const { subscriptionId, keyId, startsAt } = await startCheckout({
        data: { planId: plan.id },
      });
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
        onSuccess: () => {
          // Celebrate IMMEDIATELY — the payment succeeded, so don't make the
          // user wait on the webhook round-trip. Fired on the global bus so it
          // survives UpgradeCard unmounting when entitlement flips premium.
          triggerCelebration(tier);
          if (startsAt) {
            toast.success(
              `Plan change confirmed. ${plan.displayName} starts ${new Date(
                startsAt,
              ).toLocaleDateString()} — you keep your current plan until then.`,
            );
          }
          // In the background, poll until the webhook has written the sub so the
          // gates/UI unlock server-authoritatively (no page refresh needed).
          void pollEntitlementUntilPremium(loadEntitlement);
        },
        onDismiss: () => {
          // Cancelled or closed pre-success: refetch in case it actually went
          // through, but don't celebrate.
          void refreshEntitlement(loadEntitlement).catch(() => invalidateEntitlement());
        },
      });
    } catch (e: unknown) {
      feedback("error");
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
    loadSubscription()
      .then(setCurrent)
      .catch(() => setCurrent(null));
  }, [open, loadPlans, loadSubscription]);

  // Lifetime/admin members have nothing to buy; everyone else can switch plans.
  const permanent = current?.source === "lifetime" || current?.source === "admin";

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

  // The toggle's savings label must match the per-tier numbers shown on the
  // cards, not a hardcoded guess. Show the best available annual saving (Pro at
  // ₹129/₹899 = 42%; Elite at ₹249/₹1799 = 40%) so "save up to N%" is always
  // exactly what a card below it displays.
  const maxSavings = useMemo(() => {
    const pairs = [
      [byTier.proMonthly, byTier.proAnnual],
      [byTier.eliteMonthly, byTier.eliteAnnual],
    ] as const;
    return pairs.reduce((max, [m, a]) => {
      if (!m || !a) return max;
      return Math.max(max, annualSavingsPct(m.priceInr, a.priceInr));
    }, 0);
  }, [byTier]);

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
                {iv === "annual" && maxSavings > 0 && (
                  <span className="ml-1.5 text-pulse normal-case tracking-normal">
                    save up to {maxSavings}%
                  </span>
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
            currentPlanId={current?.planId ?? null}
            permanent={permanent}
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
            currentPlanId={current?.planId ?? null}
            permanent={permanent}
          />
        </div>

        {/* Feature comparison */}
        <div className="px-6 pb-5">
          <p className={eyebrow}>What&apos;s included</p>
          <ul className="mt-3 space-y-1.5">
            {PREMIUM_FEATURES.map((f) => (
              <li key={f.key} className="flex items-center gap-2.5 text-sm">
                <Check
                  className={cn(
                    "size-3.5 shrink-0",
                    f.status === "soon" ? "text-silver-dim" : "text-ember",
                  )}
                />
                <span className={f.status === "soon" ? "text-silver-dim" : "text-silver"}>
                  {f.uiLabel}
                </span>
                {f.status !== "live" && (
                  <span
                    className={cn(
                      "rounded-full px-1.5 py-0.5 font-mono text-[8px] uppercase tracking-widest",
                      f.status === "beta"
                        ? "bg-pulse/10 text-pulse"
                        : "bg-white/[0.06] text-silver-dim",
                    )}
                  >
                    {STATUS_LABEL[f.status]}
                  </span>
                )}
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
  currentPlanId,
  permanent,
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
  currentPlanId?: string | null;
  permanent?: boolean;
}) {
  const isCurrent = Boolean(plan?.id && currentPlanId && plan.id === currentPlanId);
  const hasPaidPlan = Boolean(currentPlanId);
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
        disabled={!plan?.id || checkingOut !== null || isCurrent || permanent}
        className="mt-4 w-full rounded-full border border-ember/50 text-ember font-mono text-[11px] uppercase tracking-widest py-2 disabled:opacity-40 hover:bg-ember/10 transition"
      >
        {checkingOut === plan?.id
          ? "Opening…"
          : isCurrent
            ? "Current plan"
            : permanent
              ? "Included"
              : hasPaidPlan
                ? `Switch to ${name}`
                : `Choose ${name}`}
      </button>
      {!isCurrent && !permanent && hasPaidPlan && plan?.id && (
        <p className="mt-2 text-center text-[10px] text-silver-dim">
          Starts when your current period ends — no double charge.
        </p>
      )}
    </div>
  );
}
