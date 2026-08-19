import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  Outlet,
  Link,
  createRootRouteWithContext,
  useRouter,
  HeadContent,
  Scripts,
} from "@tanstack/react-router";
import { useEffect, type ReactNode } from "react";
import { Toaster } from "sonner";

import appCss from "../styles.css?url";
import { reportLovableError } from "../lib/lovable-error-reporting";
import { supabase } from "@/integrations/supabase/client";
import { SmoothScroll } from "@/components/smooth-scroll";
import { CommandPalette } from "@/components/command-palette";
import { FloatingTimer } from "@/components/floating-timer";
import { GlobalRealtimeToasts } from "@/components/global-realtime-toasts";
import { CelebrationHost } from "@/components/premium/celebration-host";
import { QueueBadge } from "@/components/queue-badge";
import { useXpSync, XP_DERIVED_QUERY_KEYS } from "@/lib/xp-sync";
import { OfflineBanner } from "@/components/offline-banner";
import { SessionCeremony } from "@/components/session-ceremony";
import { siteUrl, SOCIAL_PROFILES, X_HANDLE } from "@/lib/site";

function NotFoundComponent() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-obsidian px-4 text-silver">
      <div className="max-w-md text-center">
        <div className="font-mono text-[10px] tracking-[0.3em] text-muted-foreground uppercase mb-6">
          ERR / 404 / PATH_NOT_FOUND
        </div>
        <h1 className="text-7xl font-extrabold tracking-tighter">404</h1>
        <p className="mt-4 text-sm text-muted-foreground">This signal does not exist.</p>
        {/* Two ways out, not one: someone who mistyped a room code wants to go
            back, not to the marketing page. */}
        <div className="mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row">
          <Link
            to="/"
            className="inline-block cursor-pointer rounded-lg bg-silver px-8 py-3 font-mono text-xs font-bold uppercase tracking-widest text-obsidian transition-all duration-200 ease-[var(--ease-ritual)] hover:invert active:scale-[0.99] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember focus-visible:ring-offset-2 focus-visible:ring-offset-obsidian"
          >
            Return to Origin
          </Link>
          <button
            type="button"
            onClick={() => window.history.back()}
            className="inline-block cursor-pointer rounded-lg border border-silver/20 px-8 py-3 font-mono text-xs uppercase tracking-widest text-silver transition-all duration-200 ease-[var(--ease-ritual)] hover:bg-white/5 active:scale-[0.99] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember focus-visible:ring-offset-2 focus-visible:ring-offset-obsidian"
          >
            Go Back
          </button>
        </div>
      </div>
    </div>
  );
}

function ErrorComponent({ error, reset }: { error: Error; reset: () => void }) {
  const router = useRouter();
  useEffect(() => {
    reportLovableError(error, { boundary: "tanstack_root_error_component" });
  }, [error]);

  return (
    <div className="flex min-h-screen items-center justify-center bg-obsidian px-4 text-silver">
      <div className="max-w-md text-center">
        <div className="font-mono text-[10px] tracking-[0.3em] text-breach uppercase mb-6">
          RUNTIME_EXCEPTION
        </div>
        <h1 className="text-2xl font-bold tracking-tight">Session interrupted</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          {error.message || "Something went off-protocol."}
        </p>
        <div className="mt-8 flex gap-3 justify-center">
          <button
            onClick={() => {
              router.invalidate();
              reset();
            }}
            className="bg-silver text-obsidian px-6 py-2.5 rounded-lg font-mono text-xs uppercase tracking-widest font-bold hover:invert transition-all"
          >
            Retry
          </button>
          <a
            href="/"
            className="border border-silver/20 px-6 py-2.5 rounded-lg font-mono text-xs uppercase tracking-widest hover:bg-white/5 transition-all"
          >
            Origin
          </a>
        </div>
      </div>
    </div>
  );
}

export const Route = createRootRouteWithContext<{ queryClient: QueryClient }>()({
  head: () => ({
    meta: [
      { charSet: "utf-8" },
      {
        name: "viewport",
        content: "width=device-width, initial-scale=1, viewport-fit=cover",
      },
      { name: "theme-color", content: "#0A0A0A" },
      {
        name: "google-site-verification",
        content: "sDulKjsnJ8aQiu2O-2aI7QG1jG0pTNVrfwhdchNoUR4",
      },
      {
        name: "google-site-verification",
        content: "LN3qXRnHURVxbmeyt2-nDqpYbYv8ASbhTz6jtNXIvsg",
      },
      { title: "Stack'd" },
      {
        name: "description",
        content: "Stack your phones with friends, hold the silence, and earn back the time.",
      },
      { property: "og:site_name", content: "Stack'd" },
      { property: "og:title", content: "Stack'd" },
      {
        property: "og:description",
        content: "Stack your phones with friends, hold the silence, and earn back the time.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
      // Attributes shared links to the account, so a card reads "from
      // @StackD_HQ" instead of rendering unattributed.
      { name: "twitter:site", content: `@${X_HANDLE}` },
      { name: "twitter:creator", content: `@${X_HANDLE}` },
      { name: "twitter:title", content: "Stack'd" },
      {
        name: "twitter:description",
        content: "Stack your phones with friends, hold the silence, and earn back the time.",
      },
      // Served from our own origin. This previously pointed at a Lovable
      // preview screenshot on a third-party R2 bucket — an asset we neither
      // control nor can keep from going stale.
      { property: "og:image", content: siteUrl("/og-image.png") },
      { property: "og:image:width", content: "1200" },
      { property: "og:image:height", content: "630" },
      { property: "og:image:alt", content: "Stack'd — presence is the new luxury" },
      { name: "twitter:image", content: siteUrl("/og-image.png") },
    ],
    links: [
      { rel: "stylesheet", href: appCss },
      // Declared explicitly rather than relying on the browser's implicit
      // /favicon.ico probe, which never yields a home-screen icon.
      { rel: "icon", href: "/favicon.ico", sizes: "any" },
      { rel: "apple-touch-icon", href: "/apple-touch-icon.png" },
      { rel: "manifest", href: "/site.webmanifest" },
      { rel: "preconnect", href: "https://fonts.googleapis.com" },
      { rel: "preconnect", href: "https://fonts.gstatic.com", crossOrigin: "anonymous" },
      {
        rel: "stylesheet",
        href: "https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;800&family=JetBrains+Mono:wght@400;500&display=swap",
      },
    ],
    scripts: [
      {
        type: "application/ld+json",
        children: JSON.stringify({
          "@context": "https://schema.org",
          "@graph": [
            {
              "@type": "Organization",
              name: "Stack'd",
              url: siteUrl("/"),
              // sameAs is how a search engine ties this site to the same entity
              // elsewhere — without it the X account and the site look like two
              // unrelated things.
              sameAs: SOCIAL_PROFILES,
              description:
                "Stack'd builds shared focus rooms where friends stack their phones and hold the silence together.",
            },
            {
              "@type": "WebSite",
              name: "Stack'd",
              url: siteUrl("/"),
              description:
                "Presence is the new luxury. Stack your phones with friends, hold the silence, and earn back the time.",
            },
          ],
        }),
      },
    ],
  }),
  shellComponent: RootShell,
  component: RootComponent,
  notFoundComponent: NotFoundComponent,
  errorComponent: ErrorComponent,
});

function RootShell({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <head>
        <HeadContent />
      </head>
      <body className="bg-obsidian text-silver">
        {children}
        <Scripts />
      </body>
    </html>
  );
}

function RootComponent() {
  const { queryClient } = Route.useRouteContext();
  const router = useRouter();

  // One subscriber for every XP-derived surface. Screens used to opt in
  // individually, so leaderboard, groups and achievements silently showed
  // stale totals after a session finalised — they simply had not subscribed.
  // Centralising it means a new screen using one of these keys is correct
  // without having to remember this rule exists.
  useXpSync(() => {
    for (const key of XP_DERIVED_QUERY_KEYS) {
      queryClient.invalidateQueries({ queryKey: [key] });
    }
  });

  useEffect(() => {
    const { data: sub } = supabase.auth.onAuthStateChange((event) => {
      if (event !== "SIGNED_IN" && event !== "SIGNED_OUT" && event !== "USER_UPDATED") return;
      router.invalidate();
      if (event === "SIGNED_OUT") {
        // Clear, don't invalidate. Query keys here are unscoped by design
        // (["friends"], ["analytics"], ["leaderboard"]…), so leaving the cache
        // populated after sign-out means the next person to sign in on this
        // device is served the previous user's data until each query happens
        // to refetch. invalidateQueries would also refetch immediately — as
        // the signed-out user — so removal is the correct operation.
        queryClient.clear();
      } else {
        queryClient.invalidateQueries();
      }
    });
    return () => sub.subscription.unsubscribe();
  }, [queryClient, router]);

  // Shared Element Transitions: use View Transitions API on route changes when supported.
  useEffect(() => {
    const doc =
      typeof document !== "undefined"
        ? (document as Document & { startViewTransition?: (cb: () => void) => unknown })
        : null;
    if (!doc?.startViewTransition) return;
    const unsub = router.subscribe("onBeforeNavigate", () => {
      // Wrap the pending nav in a view transition (fire-and-forget).
      try {
        doc.startViewTransition?.(() => {});
      } catch {
        /* noop */
      }
    });
    return () => unsub();
  }, [router]);

  // Listen for incoming Focus Circle sprint invites (postgres-backed) and auto-route.
  useEffect(() => {
    let unsub: (() => void) | undefined;
    let cancelled = false;
    (async () => {
      const { data } = await supabase.auth.getUser();
      if (cancelled || !data.user) return;
      const { subscribeToGroupSprints } = await import("@/lib/invite-channel");
      const { flushFinalizeQueue } = await import("@/lib/finalize-queue");
      // Best-effort: replay any finalize calls that failed while offline.
      flushFinalizeQueue(data.user.id).catch(() => {});
      unsub = subscribeToGroupSprints(data.user.id, (p) => {
        const id = `invite-${p.roomCode}`;
        Promise.all([import("sonner"), import("@/lib/copy")]).then(([{ toast }, { copy }]) => {
          toast(copy.realtime.groupSprint(p.fromName, p.groupName), {
            id,
            description: `Room ${p.roomCode}`,
            duration: 30000,
            action: {
              label: "Join",
              onClick: () => router.navigate({ to: "/room/$code", params: { code: p.roomCode } }),
            },
          });
        });
      });
    })();
    return () => {
      cancelled = true;
      unsub?.();
    };
  }, [router]);

  return (
    <QueryClientProvider client={queryClient}>
      <SmoothScroll>
        <Outlet />
      </SmoothScroll>
      <CommandPalette />
      <FloatingTimer />
      <GlobalRealtimeToasts />
      <QueueBadge />
      <OfflineBanner />
      <SessionCeremony />
      <CelebrationHost />
      <Toaster
        theme="dark"
        position="top-center"
        toastOptions={{
          style: {
            background: "#111",
            color: "#E2E2E2",
            border: "1px solid rgba(255,255,255,0.08)",
            fontFamily: "var(--font-display)",
          },
        }}
      />
    </QueryClientProvider>
  );
}
