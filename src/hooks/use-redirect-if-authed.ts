import { useEffect, useRef } from "react";
import { useNavigate } from "@tanstack/react-router";
import { supabase } from "@/integrations/supabase/client";

/**
 * Public-only pages (landing, login) must not be reachable once a session
 * exists. The check runs client-side because these routes are SSR'd for SEO —
 * the server has no session — and it re-runs on auth state changes so
 * back/forward navigation and refreshes are covered too.
 */
export function useRedirectIfAuthed(to = "/dashboard", options?: { initialOnly?: boolean }) {
  const navigate = useNavigate();
  const done = useRef(false);
  const initialOnly = options?.initialOnly ?? false;

  useEffect(() => {
    let mounted = true;

    const go = () => {
      if (!mounted || done.current) return;
      done.current = true;
      navigate({ to, replace: true });
    };

    void supabase.auth.getSession().then(({ data }) => {
      if (data.session) go();
    });

    // On the login page a session appearing mid-flow is the user signing in
    // right now, which has its own confirmation step — don't hijack it.
    if (initialOnly) return () => { mounted = false; };

    const { data: sub } = supabase.auth.onAuthStateChange((_e, session) => {
      if (session) go();
    });

    return () => {
      mounted = false;
      sub.subscription.unsubscribe();
    };
  }, [navigate, to, initialOnly]);
}

