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
        toast.success(message);
        invalidateEntitlement();
        setConfirming(false);
        load()
          .then(setDetail)
          .catch(() => undefined);
      } else {
        toast.error(message);
      }
    } catch (e: unknown) {
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
          {confirming ? (
            <div className="flex flex-wrap items-center gap-3">
              <p className="text-sm text-silver-dim">
                Cancel? You keep {tierName} until {fmtDate(detail.currentPeriodEnd)}.
              </p>
              <button
                onClick={onCancel}
                disabled={busy}
                className="rounded-full border border-breach/50 px-4 py-1.5 font-mono text-[11px] uppercase tracking-widest text-breach transition hover:bg-breach/10 disabled:opacity-40"
              >
                {busy ? "Cancelling…" : "Confirm cancel"}
              </button>
              <button
                onClick={() => setConfirming(false)}
                disabled={busy}
                className="font-mono text-[11px] uppercase tracking-widest text-silver-dim hover:text-silver"
              >
                Keep it
              </button>
            </div>
          ) : (
            <button
              onClick={() => setConfirming(true)}
              className="font-mono text-[11px] uppercase tracking-widest text-silver-dim transition hover:text-breach"
            >
              Cancel subscription
            </button>
          )}
        </div>
      )}
    </div>
  );
}
