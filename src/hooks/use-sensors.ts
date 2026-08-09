import { useEffect, useRef } from "react";
import { getSensorAdapter } from "@/lib/sensor-adapter";
import {
  computeBaseline,
  isCalibrationComplete,
  isShake,
  pruneWindow,
} from "@/lib/sensor-rules";

export type BreachReason = "tilt" | "lift" | "shake" | "tab-hidden" | "wake-lost" | "manual";

export type BreachSeverity = "minor" | "severe";
export type EnforcementMode = "gentle" | "absolute";

/** How the screen wake lock actually turned out on this device. */
export type WakeLockState =
  /** Not attempted yet. */
  | "idle"
  /** navigator.wakeLock does not exist here — no screen protection possible. */
  | "unsupported"
  /** The API exists but refused (permissions policy, non-secure context, …). */
  | "denied"
  /** Held. The screen will stay on. */
  | "active"
  /** Was held and the OS took it back — this is the one that means a breach. */
  | "released";

interface Options {
  enabled: boolean;
  mode?: EnforcementMode;
  onBreach: (reason: BreachReason, severity: BreachSeverity) => void;
  /** Fires once the orientation baseline settles and detection is armed. */
  onCalibrated?: () => void;
  /**
   * Reports wake-lock capability separately from breaches. "unsupported" and
   * "denied" are device facts the user should be told about; only "released"
   * means someone woke the screen.
   */
  onWakeLockState?: (state: WakeLockState) => void;
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
export function useSensors({
  enabled,
  mode = "absolute",
  onBreach,
  onCalibrated,
  onWakeLockState,
}: Options) {
  const cbRef = useRef(onBreach);
  const modeRef = useRef<EnforcementMode>(mode);
  const wakeLockRef = useRef<WakeLockSentinel | null>(null);
  const wakeLockCancelledRef = useRef(false);
  const onCalibratedRef = useRef(onCalibrated);
  const onWakeLockStateRef = useRef(onWakeLockState);
  cbRef.current = onBreach;
  modeRef.current = mode;
  onCalibratedRef.current = onCalibrated;
  onWakeLockStateRef.current = onWakeLockState;

  useEffect(() => {
    if (!enabled) return;
    const adapter = getSensorAdapter();
    const state: {
      baseline: { beta: number; gamma: number } | null;
      /** Orientation samples collected while calibrating, before arming. */
      calibration: Array<{ beta: number; gamma: number }>;
      calibrationStartedAt: number;
      tiltStartedAt: number;
      firedSevere: boolean;
      lastMinorAt: number;
      /** Recent acceleration magnitudes, newest last — the shake window. */
      accelWindow: Array<{ mag: number; at: number }>;
    } = {
      baseline: null,
      calibration: [],
      calibrationStartedAt: 0,
      tiltStartedAt: 0,
      firedSevere: false,
      lastMinorAt: 0,
      accelWindow: [],
    };
    wakeLockCancelledRef.current = false;

    const fireSevere = (reason: BreachReason) => {
      if (state.firedSevere) return;
      state.firedSevere = true;
      try {
        navigator.vibrate?.(200);
      } catch {
        /* noop */
      }
      cbRef.current(reason, "severe");
    };
    const fireMinor = (reason: BreachReason) => {
      const now = Date.now();
      if (now - state.lastMinorAt < 3000) return;
      state.lastMinorAt = now;
      try {
        navigator.vibrate?.(60);
      } catch {
        /* noop */
      }
      cbRef.current(reason, "minor");
    };

    const tiltThreshold = () => (modeRef.current === "gentle" ? 60 : 30);
    const shakeThreshold = () => (modeRef.current === "gentle" ? 22 : 16);

    const unsubOrient = adapter.subscribeOrientation(({ beta: b, gamma: g }) => {
      const beta = b ?? 0;
      const gamma = g ?? 0;

      // Calibrate before arming. The baseline used to be the very first
      // sample, which is taken while the phone is still being set down — so a
      // phone caught mid-placement defined "flat" as a tilted pose, and every
      // reading afterwards was measured against a wrong zero. That produced
      // false breaches on honest users and, worse, could mask real ones.
      if (!state.baseline) {
        const now = Date.now();
        if (!state.calibrationStartedAt) state.calibrationStartedAt = now;
        state.calibration.push({ beta, gamma });

        if (!isCalibrationComplete(state.calibration.length, now - state.calibrationStartedAt)) {
          return;
        }

        state.baseline = computeBaseline(state.calibration);
        state.calibration = [];
        onCalibratedRef.current?.();
        return;
      }
      const db = Math.abs(beta - state.baseline.beta);
      const dg = Math.abs(gamma - state.baseline.gamma);
      const over = db > tiltThreshold() || dg > tiltThreshold();
      if (!over) {
        if (
          state.tiltStartedAt &&
          Date.now() - state.tiltStartedAt < 3000 &&
          modeRef.current === "gentle"
        ) {
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
      // Motion before the baseline settles is the user placing the phone.
      if (!state.baseline) return;
      const x = a.x ?? 0,
        y = a.y ?? 0,
        z = a.z ?? 0;
      const mag = Math.sqrt(x * x + y * y + z * z);

      const now = Date.now();
      state.accelWindow.push({ mag, at: now });
      // Keep the window bounded — this runs at sensor frequency for the whole
      // session, so it must not grow.
      state.accelWindow = pruneWindow(state.accelWindow, now);

      // A shake is sustained agitation, not one spike. The old test fired an
      // irreversible severe breach on a single sample over threshold, so a
      // table bump or a passing truck ended someone's session.
      if (isShake(state.accelWindow, shakeThreshold(), now)) fireSevere("shake");
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
      // Capability and breach are different facts. Previously an unsupported
      // API and a denied request both fell into an empty catch, so on those
      // devices there was silently no screen protection at all and nobody was
      // told — while a legitimate OS-initiated release fired an irreversible
      // severe breach. Report the three outcomes separately.
      if (typeof navigator === "undefined" || !("wakeLock" in navigator)) {
        onWakeLockStateRef.current?.("unsupported");
        return;
      }
      try {
        const sentinel = await (
          navigator as Navigator & {
            wakeLock: { request: (t: "screen") => Promise<WakeLockSentinel> };
          }
        ).wakeLock.request("screen");
        if (wakeLockCancelledRef.current) {
          // Cleanup already ran — release immediately, do not store.
          sentinel.release?.().catch(() => {});
          return;
        }
        wakeLockRef.current = sentinel;
        onWakeLockStateRef.current?.("active");
        sentinel.addEventListener?.("release", () => {
          if (enabled && !wakeLockCancelledRef.current) {
            onWakeLockStateRef.current?.("released");
            fireSevere("wake-lost");
          }
        });
      } catch {
        // Present but refused: insecure context, permissions policy, or the
        // user agent declining. Not a breach — the protocol just cannot hold
        // the screen here.
        onWakeLockStateRef.current?.("denied");
      }
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
