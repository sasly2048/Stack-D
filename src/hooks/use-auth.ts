import { useEffect, useState } from "react";
import type { Session, User } from "@supabase/supabase-js";
import { supabase } from "@/integrations/supabase/client";
import { setMyTimezone } from "@/lib/profile.functions";

/** Report the browser timezone once per (user, zone), deduped via localStorage. */
function reportTimezone(userId: string) {
  try {
    const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;
    if (!tz) return;
    const key = `tz:${userId}`;
    if (localStorage.getItem(key) === tz) return;
    void setMyTimezone({ data: { tz } }).then(() => localStorage.setItem(key, tz));
  } catch {
    // No Intl/localStorage (SSR/old engine) — day boundaries stay UTC. Fine.
  }
}

export function useAuth() {
  const [session, setSession] = useState<Session | null>(null);
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let mounted = true;

    supabase.auth.getSession().then(({ data }) => {
      if (!mounted) return;
      setSession(data.session);
      setUser(data.session?.user ?? null);
      setLoading(false);
      if (data.session?.user) reportTimezone(data.session.user.id);
    });

    const { data: sub } = supabase.auth.onAuthStateChange((_event, s) => {
      if (!mounted) return;
      setSession(s);
      setUser(s?.user ?? null);
      setLoading(false);
      if (s?.user) reportTimezone(s.user.id);
    });

    return () => {
      mounted = false;
      sub.subscription.unsubscribe();
    };
  }, []);

  return { session, user, loading };
}
