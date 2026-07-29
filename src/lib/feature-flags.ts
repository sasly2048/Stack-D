/**
 * Feature flags — scope control for Stack'd.
 *
 * The product has exactly five *core* surfaces. Everything else is an
 * experiment ("lab") and stays out of navigation, the command palette and
 * marketing surfaces until explicitly enabled. Lab routes still exist and are
 * directly reachable by URL — we hide them, we don't delete them.
 *
 * Enable labs in a browser:
 *   ?labs=1  (persists)   |   ?labs=0  (clears)   |   localStorage stackd:labs = "1"
 */

export const CORE_ROUTES = [
  "/start", // Shared Focus Rooms
  "/room", //  Live Accountability (room.$code)
  "/dashboard", // Session Insights (primary)
  "/insights", // Session Insights (deep)
  "/leaderboard", // Progression
  "/seasons", // Progression
  "/achievements", // Progression
  "/challenges", // Progression
  "/friends", // Friends & Groups
  "/groups", // Friends & Groups
  "/profile", // account surface
] as const;

/** Secondary surfaces kept behind the labs flag. */
export const LAB_ROUTES = [
  "/vault",
  "/dna",
  "/replay",
  "/capsule",
  "/partners",
  "/webhooks",
  "/sdk",
  "/circles",
  "/companion",
  "/feed",
  "/timeline",
  "/trust",
  "/trust/moderation",
  "/integrations",
  "/catalog",
] as const;

const LABS_KEY = "stackd:labs";

export function labsEnabled(): boolean {
  if (typeof window === "undefined") return false;
  try {
    const param = new URLSearchParams(window.location.search).get("labs");
    if (param === "1") {
      localStorage.setItem(LABS_KEY, "1");
      return true;
    }
    if (param === "0") {
      localStorage.removeItem(LABS_KEY);
      return false;
    }
    return localStorage.getItem(LABS_KEY) === "1";
  } catch {
    return false;
  }
}

export function setLabsEnabled(on: boolean) {
  try {
    if (on) localStorage.setItem(LABS_KEY, "1");
    else localStorage.removeItem(LABS_KEY);
  } catch {
    /* ignore */
  }
}

const LAB_SET = new Set<string>(LAB_ROUTES);

/** True when a destination is visible for the current flag state. */
export function routeVisible(to: string, labs: boolean): boolean {
  return labs || !LAB_SET.has(to);
}

export function isLabRoute(to: string): boolean {
  return LAB_SET.has(to);
}
