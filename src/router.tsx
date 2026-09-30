import { QueryCache, QueryClient } from "@tanstack/react-query";
import { createRouter } from "@tanstack/react-router";
import { toast } from "sonner";
import { routeTree } from "./routeTree.gen";
import { RouteErrorBoundary } from "./components/route-error-boundary";
import { classifyRouteError } from "./lib/error-recovery";

/**
 * Session data is short-lived and shared: a room's participant list changes by
 * the second, but a leaderboard is fine for a minute. 30s is the compromise
 * that stops every navigation refetching while keeping numbers honest.
 */
const STALE_TIME = 30_000;

export const getRouter = () => {
  const queryClient = new QueryClient({
    // A failed background refetch used to be invisible: screens hand-rolled
    // their fetches and most swallowed the rejection, so a dead network looked
    // identical to "you have no data". This surfaces failures once, centrally,
    // without every screen re-implementing it.
    queryCache: new QueryCache({
      onError: (error, query) => {
        // Only shout about refetches. A first load has no data on screen, so
        // the screen's own error UI is the right place to explain it — a toast
        // there would double up.
        if (query.state.data === undefined) return;
        // Aborted/offline/token-hydration blips resolve themselves; the retry
        // and the offline banner already cover them.
        if (classifyRouteError(error) !== "fatal") return;
        const message = error instanceof Error ? error.message : "Something went wrong.";
        toast.error("Couldn't refresh", {
          id: `qc-${String(query.queryHash)}`,
          description: message.slice(0, 140),
        });
      },

    }),
    defaultOptions: {
      queries: {
        staleTime: STALE_TIME,
        // Two attempts, not three: this talks to Supabase directly from the
        // browser, so a failure is usually auth or connectivity, and neither
        // improves on the third try.
        retry: 2,
        retryDelay: (attempt) => Math.min(1000 * 2 ** attempt, 8000),
        // Refetching whenever a window regains focus is jarring on a
        // phone-focus app people deliberately leave alone; reconnect is the
        // signal that actually matters.
        refetchOnWindowFocus: false,
        refetchOnReconnect: true,
      },
      mutations: {
        // Mutations are user-initiated and mostly non-idempotent (seal a
        // capsule, send a tie). Retrying one behind the user's back can
        // duplicate it.
        retry: 0,
      },
    },
  });

  const router = createRouter({
    routeTree,
    context: { queryClient },
    scrollRestoration: true,
    defaultPreloadStaleTime: 0,
    // Any route without its own boundary gets the recovering-first boundary
    // instead of a bare error screen.
    defaultErrorComponent: RouteErrorBoundary,
  });


  return router;
};
