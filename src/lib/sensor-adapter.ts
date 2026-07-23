/**
 * Hardware sensor adapter — Brief §1.
 *
 * Abstracts raw web `deviceorientation` / `devicemotion` listeners behind a
 * minimal interface so the room runtime can be swapped to Capacitor (or any
 * other native bridge) without touching the breach-detection state machine.
 *
 * Selection at module load:
 *   - If we're running inside a Capacitor native shell -> use `@capacitor/motion`.
 *   - Otherwise -> plain `window` event listeners (existing behaviour).
 *
 * Downstream consumers (use-sensors.ts) only see the shared shape:
 *   subscribeOrientation(handler) -> unsubscribe
 *   subscribeMotion(handler)      -> unsubscribe
 *   requestPermissions()          -> Promise<void>
 */

export interface OrientationSample {
  beta: number | null;
  gamma: number | null;
  alpha?: number | null;
}

export interface MotionSample {
  accelerationIncludingGravity: { x: number | null; y: number | null; z: number | null } | null;
}

export interface SensorAdapter {
  id: "web" | "capacitor";
  subscribeOrientation(handler: (s: OrientationSample) => void): () => void;
  subscribeMotion(handler: (s: MotionSample) => void): () => void;
  requestPermissions(): Promise<void>;
}

// ---------- Web adapter ----------
const webAdapter: SensorAdapter = {
  id: "web",
  subscribeOrientation(handler) {
    if (typeof window === "undefined") return () => {};
    const fn = (e: DeviceOrientationEvent) => handler({ beta: e.beta, gamma: e.gamma, alpha: e.alpha });
    window.addEventListener("deviceorientation", fn);
    return () => window.removeEventListener("deviceorientation", fn);
  },
  subscribeMotion(handler) {
    if (typeof window === "undefined") return () => {};
    const fn = (e: DeviceMotionEvent) => handler({ accelerationIncludingGravity: e.accelerationIncludingGravity });
    window.addEventListener("devicemotion", fn);
    return () => window.removeEventListener("devicemotion", fn);
  },
  async requestPermissions() {
    if (typeof window === "undefined") return;
    const DOE = (window as unknown as { DeviceOrientationEvent?: { requestPermission?: () => Promise<string> } }).DeviceOrientationEvent;
    const DME = (window as unknown as { DeviceMotionEvent?: { requestPermission?: () => Promise<string> } }).DeviceMotionEvent;
    try { if (DOE?.requestPermission) await DOE.requestPermission(); } catch { /* noop */ }
    try { if (DME?.requestPermission) await DME.requestPermission(); } catch { /* noop */ }
  },
};

// ---------- Capacitor adapter (native shell only) ----------
function createCapacitorAdapter(Motion: typeof import("@capacitor/motion").Motion): SensorAdapter {
  return {
    id: "capacitor",
    subscribeOrientation(handler) {
      const p = Motion.addListener("orientation", (e) => handler({ beta: e.beta, gamma: e.gamma, alpha: e.alpha }));
      return () => { p.then((h) => h.remove()).catch(() => {}); };
    },
    subscribeMotion(handler) {
      const p = Motion.addListener("accel", (e) => handler({ accelerationIncludingGravity: e.accelerationIncludingGravity ?? null }));
      return () => { p.then((h) => h.remove()).catch(() => {}); };
    },
    async requestPermissions() {
      // Capacitor native typically grants implicitly; no-op here. The web
      // adapter's iOS permission prompt is handled by webAdapter when used.
    },
  };
}

let cached: SensorAdapter | null = null;

export function getSensorAdapter(): SensorAdapter {
  if (cached) return cached;
  // Best-effort detection — synchronous so callers stay simple.
  // Native shells set `window.Capacitor.isNativePlatform()`.
  type Cap = { isNativePlatform?: () => boolean };
  const cap = (typeof window !== "undefined"
    ? (window as unknown as { Capacitor?: Cap }).Capacitor
    : undefined);
  if (cap?.isNativePlatform?.()) {
    try {
      // Dynamic require so the web bundle never trips on missing globals.
      // eslint-disable-next-line @typescript-eslint/no-require-imports
      const mod = require("@capacitor/motion") as typeof import("@capacitor/motion");
      cached = createCapacitorAdapter(mod.Motion);
      return cached;
    } catch {
      // Fall through to web.
    }
  }
  cached = webAdapter;
  return cached;
}
