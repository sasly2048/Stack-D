import { useEffect, useState } from "react";
import { labsEnabled, setLabsEnabled } from "@/lib/feature-flags";

/** Hydration-safe read of the labs feature flag. */
export function useLabs(): [boolean, (on: boolean) => void] {
  const [labs, setLabs] = useState(false);

  useEffect(() => {
    setLabs(labsEnabled());
  }, []);

  const update = (on: boolean) => {
    setLabsEnabled(on);
    setLabs(on);
  };

  return [labs, update];
}
