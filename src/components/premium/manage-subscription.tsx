import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { toast } from "sonner";

import {
  cancelSubscription,
  getSubscriptionDetail,
  type SubscriptionDetail,
} from "@/lib/manage-subscription.functions";
import { invalidateEntitlement } from "@/lib/use-entitlement";
import { useEntitlement } from "@/lib/use-entitlement";
import { featuresFor } from "@/lib/premium-catalog";
import { feedback } from "@/lib/feedback";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";

const eyebrow = "font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim";

function fmtDate(iso: string | null): string {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleDateString(undefined, {
      year: "numeric",
      month: "short",
      day: "numeric",
    });
  } catch {
    return "—";
  }
}

/**
 * Membership management on the profile: current plan, billing interval, amount,
 * next renewal, and cancel. Renders nothing for free users. Lifetime/admin show
 * status without a cancel control (nothing to cancel).
 */
export function ManageSubscription() {
  const { isPremium } = useEntitlement();
  const load = useServerFn(getSubscriptionDetail);
  const cancel = useServerFn(cancelSubscription);
  const [detail, setDetail] = useState<SubscriptionDetail | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!isPremium) return;
    load()
      .then(setDetail)
      .catch(() => undefined);
  }, [isPremium, load]);

  if (!isPremium || !detail || detail.tier === "free") return null;

  const tierName = detail.tier === "elite" ? "Elite" : "Pro";
  const isLifetime = detail.source === "lifetime";
  const isAdmin = detail.source === "admin";

  const onCancel = async () => {
    if (busy) return;
    setBusy(true);
    try {
      const { result, message } = await cancel();
      if (result === "cancelled") {
        feedback("success");
        toast.success(message);
        invalidateEntitlement();
        setConfirming(false);
        load()
          .then(setDetail)
          .catch(() => undefined);
      } else {
        feedback("error");
        toast.error(message);
      }
    } catch (e: unknown) {
      feedback("error");
      toast.error(e instanceof Error ? e.message : "Cancel failed");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="rounded-lg border border-border bg-white/[0.02] p-5">
      <div className="flex items-center justify-between">
        <p className={eyebrow}>Membership</p>
        <span className="rounded-full border border-ember/50 bg-ember/10 px-2.5 py-0.5 font-mono text-[10px] uppercase tracking-widest text-ember">
          {isLifetime ? "Lifetime" : isAdmin ? "Admin" : tierName}
        </span>
      </div>

      <dl className="mt-4 grid grid-cols-2 gap-x-6 gap-y-3 text-sm">
        <div>
          <dt className={eyebrow}>Plan</dt>
          <dd className="mt-1 text-silver">{detail.displayName ?? `${tierName}`}</dd>
        </div>
        {detail.interval && (
          <div>
            <dt className={eyebrow}>Billing</dt>
            <dd className="mt-1 text-silver capitalize">{detail.interval}</dd>
          </div>
        )}
        {detail.priceInr != null && (
          <div>
            <dt className={eyebrow}>Amount</dt>
            <dd className="mt-1 text-silver">
              ₹{detail.priceInr}
              <span className="text-silver-dim">/{detail.interval === "annual" ? "yr" : "mo"}</span>
            </dd>
          </div>
        )}
        <div>
          <dt className={eyebrow}>{isLifetime || isAdmin ? "Access" : "Renews"}</dt>
          <dd className="mt-1 text-silver">
            {isLifetime || isAdmin ? "Never expires" : fmtDate(detail.currentPeriodEnd)}
          </dd>
        </div>
      </dl>

      {detail.cancellable && (
        <div className="mt-5 border-t border-border pt-4">
          <button
            onClick={() => setConfirming(true)}
            className="font-mono text-[11px] uppercase tracking-widest text-silver-dim transition hover:text-breach"
          >
            Cancel subscription
          </button>
        </div>
      )}

      {/* Retention prompt — a real "are you sure?" with what they'd lose, and
          "Keep" as the prominent action. */}
      <Dialog open={confirming} onOpenChange={(v) => !busy && setConfirming(v)}>
        <DialogContent className="max-w-md border-border bg-card">
          <p className={eyebrow}>Before you go</p>
          <DialogTitle className="mt-2 font-serif text-2xl text-silver font-normal">
            Keep your {tierName}?
          </DialogTitle>
          <p className="mt-2 text-sm text-silver-dim">
            Cancelling ends {tierName} on{" "}
            <span className="text-silver">{fmtDate(detail.currentPeriodEnd)}</span>. You&apos;ll
            keep full access until then — after that you&apos;d lose:
          </p>
          <ul className="mt-3 space-y-1.5">
            {featuresFor(detail.tier === "elite" ? "elite" : "pro")
              .slice(0, 5)
              .map((f) => (
                <li key={f.key} className="flex items-center gap-2 text-sm text-silver">
                  <span className="text-ember">·</span>
                  {f.uiLabel}
                </li>
              ))}
          </ul>
          <div className="mt-6 flex flex-col gap-2">
            <button
              onClick={() => setConfirming(false)}
              disabled={busy}
              className="w-full rounded-full border border-ember/50 bg-ember/10 py-2.5 font-mono text-[11px] uppercase tracking-widest text-ember transition hover:bg-ember/20 disabled:opacity-40"
            >
              Keep my {tierName}
            </button>
            <button
              onClick={onCancel}
              disabled={busy}
              className="w-full py-2 font-mono text-[11px] uppercase tracking-widest text-silver-dim transition hover:text-breach disabled:opacity-40"
            >
              {busy ? "Cancelling…" : "Cancel anyway"}
            </button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
