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
import { QueueBadge } from "@/components/queue-badge";
import { SessionCeremony } from "@/components/session-ceremony";


function NotFoundComponent() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-obsidian px-4 text-silver">
      <div className="max-w-md text-center">
        <div className="font-mono text-[10px] tracking-[0.3em] text-muted-foreground uppercase mb-6">
          ERR / 404 / PATH_NOT_FOUND
        </div>
        <h1 className="text-7xl font-extrabold tracking-tighter">404</h1>
        <p className="mt-4 text-sm text-muted-foreground">
          This signal does not exist.
        </p>
        <Link
          to="/"
          className="mt-8 inline-block bg-silver text-obsidian px-8 py-3 rounded-lg font-mono text-xs uppercase tracking-widest font-bold hover:invert transition-all"
        >
          Return to Origin
        </Link>
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
        <h1 className="text-2xl font-bold tracking-tight">Ritual interrupted</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          {error.message || "Something went off-protocol."}
        </p>
        <div className="mt-8 flex gap-3 justify-center">
          <button
            onClick={() => { router.invalidate(); reset(); }}
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
      { name: "viewport", content: "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover" },
      { name: "theme-color", content: "#0A0A0A" },
      { title: "Stack'd — Presence is the new luxury" },
      { name: "description", content: "Stack your phones with friends, hold the silence, and earn back the time." },
      { property: "og:title", content: "Stack'd — Presence is the new luxury" },
      { property: "og:description", content: "Stack your phones with friends, hold the silence, and earn back the time." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
      { name: "twitter:title", content: "Stack'd — Presence is the new luxury" },
      { name: "twitter:description", content: "Stack your phones with friends, hold the silence, and earn back the time." },
      { property: "og:image", content: "https://pub-bb2e103a32db4e198524a2e9ed8f35b4.r2.dev/dbf64533-6a19-4488-b439-6da7960f679f/id-preview-4e044855--4583512a-abdb-4f6c-bcfb-adf0289b6c2b.lovable.app-1784255570712.png" },
      { name: "twitter:image", content: "https://pub-bb2e103a32db4e198524a2e9ed8f35b4.r2.dev/dbf64533-6a19-4488-b439-6da7960f679f/id-preview-4e044855--4583512a-abdb-4f6c-bcfb-adf0289b6c2b.lovable.app-1784255570712.png" },
    ],
    links: [
      { rel: "stylesheet", href: appCss },
      { rel: "preconnect", href: "https://fonts.googleapis.com" },
      { rel: "preconnect", href: "https://fonts.gstatic.com", crossOrigin: "anonymous" },
      { rel: "stylesheet", href: "https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;800&family=JetBrains+Mono:wght@400;500&display=swap" },
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

  useEffect(() => {
    const { data: sub } = supabase.auth.onAuthStateChange((event) => {
      if (event !== "SIGNED_IN" && event !== "SIGNED_OUT" && event !== "USER_UPDATED") return;
      router.invalidate();
      if (event !== "SIGNED_OUT") queryClient.invalidateQueries();
    });
    return () => sub.subscription.unsubscribe();
  }, [queryClient, router]);

  // Shared Element Transitions: use View Transitions API on route changes when supported.
  useEffect(() => {
    const doc = typeof document !== "undefined" ? (document as Document & { startViewTransition?: (cb: () => void) => unknown }) : null;
    if (!doc?.startViewTransition) return;
    const unsub = router.subscribe("onBeforeNavigate", () => {
      // Wrap the pending nav in a view transition (fire-and-forget).
      try { doc.startViewTransition?.(() => {}); } catch { /* noop */ }
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
    return () => { cancelled = true; unsub?.(); };
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
      <SessionCeremony />
      <Toaster theme="dark" position="top-center" toastOptions={{
        style: { background: "#111", color: "#E2E2E2", border: "1px solid rgba(255,255,255,0.08)", fontFamily: "var(--font-display)" }
      }} />
    </QueryClientProvider>
  );
}

