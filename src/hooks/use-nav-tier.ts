import { useEffect, useState } from "react";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/use-auth";

export type NavTier = "starter" | "intermediate" | "advanced";

interface TierState {
  tier: NavTier;
  power: boolean;
  lifetimeXp: number;
  streak: number;
  sessions: number;
}

const CACHE_KEY = "stackd:nav-tier:v1";
const POWER_KEY = "stackd:power-user";

function computeTier(xp: number, streak: number, sessions: number): NavTier {
  if (xp >= 2000 || streak >= 7 || sessions >= 20) return "advanced";
  if (xp >= 300 || streak >= 3 || sessions >= 5) return "intermediate";
  return "starter";
}

/**
 * Progressive-disclosure signal for Nav + Cmd+K.
 * - Real signals from `profiles` (lifetime_xp, current_focus_streak, total_sessions).
 * - Persists Power-User override in localStorage so nothing snaps back on reload.
 */
export function useNavTier(): TierState {
  const { user } = useAuth();
  const [state, setState] = useState<TierState>(() => {
    try {
      const raw = typeof window !== "undefined" ? localStorage.getItem(CACHE_KEY) : null;
      const power = typeof window !== "undefined" ? localStorage.getItem(POWER_KEY) === "1" : false;
      if (raw) return { ...(JSON.parse(raw) as TierState), power };
    } catch { /* ignore */ }
    return { tier: "starter", power: false, lifetimeXp: 0, streak: 0, sessions: 0 };
  });

  useEffect(() => {
    if (!user) return;
    let cancelled = false;
    (async () => {
      const [{ data: prof }, { count }] = await Promise.all([
        supabase
          .from("profiles")
          .select("lifetime_xp,current_focus_streak")
          .eq("id", user.id)
          .maybeSingle(),
        supabase
          .from("focus_history")
          .select("id", { count: "exact", head: true })
          .eq("profile_id", user.id),
      ]);
      if (cancelled || !prof) return;
      const xp = prof.lifetime_xp ?? 0;
      const streak = prof.current_focus_streak ?? 0;
      const sessions = count ?? 0;
      const next: TierState = {
        tier: computeTier(xp, streak, sessions),
        power: localStorage.getItem(POWER_KEY) === "1",
        lifetimeXp: xp,
        streak,
        sessions,
      };
      try { localStorage.setItem(CACHE_KEY, JSON.stringify(next)); } catch { /* ignore */ }
      setState(next);
    })();
    return () => { cancelled = true; };
  }, [user?.id]);

  return state;
}

export function setPowerUser(on: boolean) {
  try { localStorage.setItem(POWER_KEY, on ? "1" : "0"); } catch { /* ignore */ }
  window.dispatchEvent(new StorageEvent("storage", { key: POWER_KEY }));
}

/** Which tier a nav destination requires. */
export const NAV_MIN_TIER: Record<string, NavTier> = {
  "/dashboard": "starter",
  "/friends": "starter",
  "/start": "starter",
  "/profile": "starter",
  "/leaderboard": "starter",
  "/groups": "intermediate",
  "/seasons": "intermediate",
  "/insights": "intermediate",
  "/achievements": "intermediate",
  "/challenges": "intermediate",
  "/feed": "intermediate",
  "/timeline": "intermediate",
  "/companion": "intermediate",
  "/replay": "advanced",
  "/dna": "advanced",
  "/capsule": "advanced",
  "/vault": "advanced",
  "/partners": "advanced",
  "/webhooks": "advanced",
  "/sdk": "advanced",
};

const ORDER: NavTier[] = ["starter", "intermediate", "advanced"];
export function tierUnlocked(current: NavTier, needed: NavTier, power: boolean) {
  if (power) return true;
  return ORDER.indexOf(current) >= ORDER.indexOf(needed);
}
