import { useEffect, useState } from "react";

import { onCelebration, type CelebrationTier } from "@/lib/celebration-bus";
import { CelebratePro } from "./celebrate-pro";
import { CelebrateElite } from "./celebrate-elite";

/**
 * Global celebration mount. Lives at the app root (in __root.tsx) so it can't
 * be unmounted by the entitlement flip that hides the upgrade UI. Listens on the
 * celebration bus and shows the tier-specific sequence.
 */
export function CelebrationHost() {
  const [tier, setTier] = useState<CelebrationTier | null>(null);

  useEffect(() => onCelebration(setTier), []);

  return (
    <>
      <CelebratePro open={tier === "pro"} onClose={() => setTier(null)} />
      <CelebrateElite open={tier === "elite"} onClose={() => setTier(null)} />
    </>
  );
}
