import { createFileRoute, Outlet, redirect } from "@tanstack/react-router";
import { supabase } from "@/integrations/supabase/client";

/**
 * Shown while `beforeLoad` is in flight.
 *
 * This route is `ssr: false` and its guard makes a network call to Supabase, so
 * there is a real gap between navigation and the redirect resolving. Without
 * this the user stares at an empty obsidian page and the app looks hung —
 * particularly on a cold load, where the bundle has to boot first.
 */
function AuthPending() {
  return (
    <div
      className="flex min-h-screen items-center justify-center bg-obsidian"
      role="status"
      aria-live="polite"
    >
      <span className="sr-only">Checking your session…</span>
      <span
        aria-hidden="true"
        className="size-1.5 animate-pulse rounded-full bg-ember"
        style={{ animationDuration: "1.4s" }}
      />
    </div>
  );
}

export const Route = createFileRoute("/_authenticated")({
  ssr: false,
  beforeLoad: async () => {
    const { data, error } = await supabase.auth.getUser();
    if (error || !data.user) {
      throw redirect({ to: "/auth" });
    }
    return { user: data.user };
  },
  component: () => <Outlet />,
  pendingComponent: AuthPending,
});
