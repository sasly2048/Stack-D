import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";

import { getEntitlement, type AccessTier, type Entitlement } from "./subscription.functions";
import { meetsTier } from "./entitlement-rules";

/**
 * Shared entitlement state. One fetch, shared across every gate/banner on the
 * page — components subscribe rather than each hitting the server. The frontend
 * only reflects this; the authoritative check is server-side has_tier().
 */
let cache: Entitlement | null = null;
let inflight: Promise<Entitlement> | null = null;
const listeners = new Set<(e: Entitlement) => void>();

function broadcast(e: Entitlement) {
  cache = e;
  listeners.forEach((l) => l(e));
}

/** Force a re-fetch (e.g. after a successful redeem / upgrade). */
export function invalidateEntitlement() {
  cache = null;
  inflight = null;
}

/**
 * Refetch entitlement now and push it to every mounted component — no page
 * refresh needed. Takes the bound server-fn loader (from useServerFn) since the
 * fetch needs the caller's auth. Returns the fresh entitlement.
 */
export async function refreshEntitlement(load: () => Promise<Entitlement>): Promise<Entitlement> {
  const e = await load();
  broadcast(e);
  return e;
}

/**
 * Poll entitlement until the user is premium, or attempts run out. Used right
 * after a Razorpay payment: the client sees success before the webhook has
 * written the subscription row, so we re-check every `intervalMs` until the
 * server confirms premium (then the UI flips live). Resolves true once premium,
 * false if it never lands in time (payment still succeeded server-side; the
 * user will see it on next load).
 */
export async function pollEntitlementUntilPremium(
  load: () => Promise<Entitlement>,
  { attempts = 20, intervalMs = 800 }: { attempts?: number; intervalMs?: number } = {},
): Promise<boolean> {
  for (let i = 0; i < attempts; i++) {
    try {
      const e = await load();
      broadcast(e);
      if (e.isPremium) return true;
    } catch {
      // transient — keep polling
    }
    await new Promise((r) => setTimeout(r, intervalMs));
  }
  return false;
}

export function useEntitlement() {
  const load = useServerFn(getEntitlement);
  const [ent, setEnt] = useState<Entitlement | null>(cache);

  useEffect(() => {
    listeners.add(setEnt);
    if (cache) {
      setEnt(cache);
    } else {
      inflight ??= load()
        .then((e) => {
          broadcast(e);
          return e;
        })
        .catch(() => {
          // Fail closed: treat as free on error, don't unlock premium on a
          // network blip. Server gates remain the real enforcement anyway.
          const free: Entitlement = {
            tier: "free",
            isAdmin: false,
            isPremium: false,
            source: "none",
            expiresAt: null,
          };
          broadcast(free);
          return free;
        });
    }
    return () => {
      listeners.delete(setEnt);
    };
  }, [load]);

  return {
    entitlement: ent,
    loading: ent === null,
    isPremium: ent?.isPremium ?? false,
    isAdmin: ent?.isAdmin ?? false,
    /** Does the user meet at least this tier? */
    has: (required: AccessTier) => (ent ? meetsTier(ent, required) : false),
  };
}
