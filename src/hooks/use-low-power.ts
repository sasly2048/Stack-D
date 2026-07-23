import { useEffect, useState } from "react";

/**
 * Low Power Mode — user- or system-triggered signal for expensive visual
 * effects (particles, meteors, orbits, parallax). Respects OS
 * `prefers-reduced-motion` and a low-battery heuristic when available.
 *
 * Components that opt in should either render a lighter variant or skip
 * animation entirely when `useLowPower()` returns true.
 */
const KEY = "stackd:low-power";

interface BatteryLike { level: number; charging: boolean; addEventListener?: (e: string, cb: () => void) => void; removeEventListener?: (e: string, cb: () => void) => void; }

export function useLowPower(): boolean {
  const [on, setOn] = useState<boolean>(() => {
    if (typeof window === "undefined") return false;
    try {
      const forced = localStorage.getItem(KEY);
      if (forced === "1") return true;
      if (forced === "0") return false;
    } catch { /* ignore */ }
    if (typeof window.matchMedia === "function" && window.matchMedia("(prefers-reduced-motion: reduce)").matches) return true;
    return false;
  });

  useEffect(() => {
    const onStorage = (e: StorageEvent) => {
      if (e.key === KEY) setOn(e.newValue === "1");
    };
    window.addEventListener("storage", onStorage);

    let battery: BatteryLike | undefined;
    const nav = navigator as Navigator & { getBattery?: () => Promise<BatteryLike> };
    let listener: (() => void) | undefined;
    if (typeof nav.getBattery === "function" && localStorage.getItem(KEY) === null) {
      nav.getBattery().then((b) => {
        battery = b;
        listener = () => {
          if (!b.charging && b.level < 0.2) setOn(true);
        };
        listener();
        b.addEventListener?.("levelchange", listener);
        b.addEventListener?.("chargingchange", listener);
      }).catch(() => undefined);
    }

    return () => {
      window.removeEventListener("storage", onStorage);
      if (battery && listener) {
        battery.removeEventListener?.("levelchange", listener);
        battery.removeEventListener?.("chargingchange", listener);
      }
    };
  }, []);

  return on;
}

export function setLowPower(on: boolean | "auto") {
  try {
    if (on === "auto") localStorage.removeItem(KEY);
    else localStorage.setItem(KEY, on ? "1" : "0");
    window.dispatchEvent(new StorageEvent("storage", { key: KEY, newValue: on === "auto" ? null : on ? "1" : "0" }));
  } catch { /* ignore */ }
}
