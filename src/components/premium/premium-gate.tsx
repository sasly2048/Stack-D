import { feedback } from "@/lib/feedback";
import { useState, type ReactNode } from "react";
import { Lock } from "lucide-react";

import { useEntitlement } from "@/lib/use-entitlement";
import { PREMIUM_FEATURES } from "@/lib/premium-catalog";
import type { AccessTier } from "@/lib/subscription.functions";
import { UpgradeDialog } from "./upgrade-dialog";

/**
 * Soft paywall around premium content. If the user meets the required tier,
 * children render. Otherwise a quiet locked panel invites an upgrade — it never
 * blocks repeatedly or nags; one click opens the dialog.
 *
 * This is UX only. The real enforcement is the server gate on whatever data the
 * children fetch — a determined client can render `children`, but the API won't
 * serve premium data without has_tier() passing.
 */
export function PremiumGate({
  feature,
  tier,
  children,
  label,
}: {
  /** Feature key from premium-catalog; determines the required tier + copy. */
  feature?: string;
  /** Explicit tier override if not using a catalog feature. */
  tier?: Exclude<AccessTier, "free">;
  children: ReactNode;
  /** Optional custom locked-state heading. */
  label?: string;
}) {
  const { has, loading } = useEntitlement();
  const [open, setOpen] = useState(false);

  const cat = feature ? PREMIUM_FEATURES.find((f) => f.key === feature) : undefined;
  const required: Exclude<AccessTier, "free"> = tier ?? cat?.requiredTier ?? "pro";

  // While loading, render nothing rather than flashing the lock or the content.
  if (loading) return null;
  if (has(required)) return <>{children}</>;

  const heading = label ?? cat?.uiLabel ?? "A premium feature";
  const tierName = required === "elite" ? "Elite" : "Pro";

  return (
    <>
      <div className="rounded-lg border border-border bg-white/[0.02] p-6 text-center">
        <Lock className="mx-auto size-5 text-ember" />
        <p className="mt-3 font-serif text-base text-silver">{heading}</p>
        <p className="mt-1 text-sm text-silver-dim">Available on {tierName}.</p>
        <button
          onClick={() => {
            feedback("open");
            setOpen(true);
          }}
          className="mt-4 rounded-full border border-ember/50 text-ember font-mono text-xs uppercase tracking-widest px-4 py-2 hover:bg-ember/10 transition"
        >
          Unlock with {tierName}
        </button>
      </div>
      <UpgradeDialog
        open={open}
        onOpenChange={setOpen}
        reason={`${heading} is ${required === "elite" ? "an Elite" : "a Pro"} feature.`}
      />
    </>
  );
}
