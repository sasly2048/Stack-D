import { feedback } from "@/lib/feedback";
import { useState } from "react";
import { X } from "lucide-react";

import { useEntitlement } from "@/lib/use-entitlement";
import { UpgradeDialog } from "./upgrade-dialog";

const DISMISS_KEY = "stackd:upgrade-card-dismissed";

/**
 * Quiet dashboard banner nudging free users toward Premium. Dismissible, and
 * the dismissal sticks (localStorage) so we never nag. Renders nothing for
 * premium users or once dismissed.
 */
export function UpgradeCard() {
  const { isPremium, loading } = useEntitlement();
  const [open, setOpen] = useState(false);
  const [dismissed, setDismissed] = useState(
    () => typeof window !== "undefined" && localStorage.getItem(DISMISS_KEY) === "1",
  );

  if (loading || isPremium || dismissed) return null;

  const dismiss = () => {
    localStorage.setItem(DISMISS_KEY, "1");
    setDismissed(true);
  };

  return (
    <>
      <div className="relative rounded-lg border border-ember/25 bg-ember/[0.03] p-4">
        <button
          onClick={dismiss}
          aria-label="Dismiss"
          className="absolute top-3 right-3 text-silver-dim hover:text-silver transition-colors"
        >
          <X className="size-4" />
        </button>
        <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Premium</p>
        <p className="mt-2 font-serif text-lg text-silver">See the full picture</p>
        <p className="mt-1 text-sm text-silver-dim">
          Unlimited history, advanced analytics, and more — from ₹75/mo on annual.
        </p>
        <button
          onClick={() => {
            feedback("open");
            setOpen(true);
          }}
          className="mt-3 rounded-full border border-ember/50 text-ember font-mono text-xs uppercase tracking-widest px-4 py-2 hover:bg-ember/10 transition"
        >
          See plans
        </button>
      </div>
      <UpgradeDialog open={open} onOpenChange={setOpen} />
    </>
  );
}
