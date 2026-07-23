import { useEffect, useRef } from "react";
import { getSensorAdapter } from "@/lib/sensor-adapter";

export type BreachReason =
  | "tilt"
  | "lift"
  | "shake"
  | "tab-hidden"
  | "wake-lost"
  | "manual";

export type BreachSeverity = "minor" | "severe";
export type EnforcementMode = "gentle" | "absolute";

interface Options {
  enabled: boolean;
  mode?: EnforcementMode;
  onBreach: (reason: BreachReason, severity: BreachSeverity) => void;
}

type WakeLockSentinel = EventTarget & { release: () => Promise<void> };

/**
 * Multi-signal "phone is face-down and still" detector.
 *
 * Optim 01: the rapid identity of `onBreach` (which the room rebuilds every
 * second when the timer ticks) is held in a ref so the effect below only
 * tears down when `enabled` actually flips. Otherwise we'd unbind and rebind
 * sensor listeners on every render — burning battery, dropping telemetry,
 * and leaking GC churn on mobile.
 *
 * Brief §1: the raw orientation/motion event sources are pulled through a
 * `SensorAdapter` so the same hook serves web and Capacitor builds without
 * branching here.
 */
export function useSensors({ enabled, mode = "absolute", onBreach }: Options) {
  const cbRef = useRef(onBreach);
  const modeRef = useRef<EnforcementMode>(mode);
  const wakeLockRef = useRef<WakeLockSentinel | null>(null);
  const wakeLockCancelledRef = useRef(false);
  cbRef.current = onBreach;
  modeRef.current = mode;

  useEffect(() => {
    if (!enabled) return;
    const adapter = getSensorAdapter();
    const state: {
      baseline: { beta: number; gamma: number } | null;
      tiltStartedAt: number;
      firedSevere: boolean;
      lastMinorAt: number;
    } = { baseline: null, tiltStartedAt: 0, firedSevere: false, lastMinorAt: 0 };
    wakeLockCancelledRef.current = false;

    const fireSevere = (reason: BreachReason) => {
      if (state.firedSevere) return;
      state.firedSevere = true;
      try { navigator.vibrate?.(200); } catch { /* noop */ }
      cbRef.current(reason, "severe");
    };
    const fireMinor = (reason: BreachReason) => {
      const now = Date.now();
      if (now - state.lastMinorAt < 3000) return;
      state.lastMinorAt = now;
      try { navigator.vibrate?.(60); } catch { /* noop */ }
      cbRef.current(reason, "minor");
    };

    const tiltThreshold = () => (modeRef.current === "gentle" ? 60 : 30);
    const shakeThreshold = () => (modeRef.current === "gentle" ? 22 : 16);

    const unsubOrient = adapter.subscribeOrientation(({ beta: b, gamma: g }) => {
      const beta = b ?? 0;
      const gamma = g ?? 0;
      if (!state.baseline) {
        state.baseline = { beta, gamma };
        return;
      }
      const db = Math.abs(beta - state.baseline.beta);
      const dg = Math.abs(gamma - state.baseline.gamma);
      const over = db > tiltThreshold() || dg > tiltThreshold();
      if (!over) {
        if (state.tiltStartedAt && Date.now() - state.tiltStartedAt < 3000 && modeRef.current === "gentle") {
          fireMinor("tilt");
        }
        state.tiltStartedAt = 0;
        return;
      }
      if (!state.tiltStartedAt) state.tiltStartedAt = Date.now();
      const held = Date.now() - state.tiltStartedAt;
      if (modeRef.current === "absolute" || held > 3000 || db > 90 || dg > 90) {
        fireSevere(db > 90 || dg > 90 ? "lift" : "tilt");
      }
    });

    const unsubMotion = adapter.subscribeMotion(({ accelerationIncludingGravity: a }) => {
      if (!a) return;
      const x = a.x ?? 0, y = a.y ?? 0, z = a.z ?? 0;
      const mag = Math.sqrt(x * x + y * y + z * z);
      if (mag > shakeThreshold()) fireSevere("shake");
    });

    const onVisibility = () => {
      if (typeof document !== "undefined" && document.visibilityState === "hidden") {
        fireSevere("tab-hidden");
      }
    };
    if (typeof document !== "undefined") {
      document.addEventListener("visibilitychange", onVisibility);
    }

    // Wake lock acquisition is async; the effect may tear down before the
    // promise resolves. The ref + cancelled flag ensure we release whichever
    // sentinel we end up holding, regardless of resolution order.
    (async () => {
      try {
        if (typeof navigator !== "undefined" && "wakeLock" in navigator) {
          const sentinel = await (navigator as Navigator & {
            wakeLock: { request: (t: "screen") => Promise<WakeLockSentinel> };
          }).wakeLock.request("screen");
          if (wakeLockCancelledRef.current) {
            // Cleanup already ran — release immediately, do not store.
            sentinel.release?.().catch(() => {});
            return;
          }
          wakeLockRef.current = sentinel;
          sentinel.addEventListener?.("release", () => {
            if (enabled && !wakeLockCancelledRef.current) fireSevere("wake-lost");
          });
        }
      } catch { /* noop */ }
    })();

    return () => {
      wakeLockCancelledRef.current = true;
      unsubOrient();
      unsubMotion();
      if (typeof document !== "undefined") {
        document.removeEventListener("visibilitychange", onVisibility);
      }
      const held = wakeLockRef.current;
      wakeLockRef.current = null;
      held?.release?.().catch(() => {});
    };
  }, [enabled]);
}

/** iOS 13+ requires explicit permission, called from a user gesture. */
export async function requestSensorPermissions(): Promise<void> {
  await getSensorAdapter().requestPermissions();
}
