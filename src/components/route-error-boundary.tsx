import { useEffect, useRef, useState } from "react";
import { useRouter } from "@tanstack/react-router";

import { reportLovableError } from "@/lib/lovable-error-reporting";
import { classifyRouteError, reloadOnceForStaleChunk } from "@/lib/error-recovery";
import { Button } from "@/components/ui/button";

const MAX_SILENT_ATTEMPTS = 2;

/** The quiet state: indistinguishable from a normal pending navigation. */
function RecoveringScreen() {
  return (
    <div
      className="flex min-h-screen items-center justify-center bg-obsidian"
      role="status"
      aria-live="polite"
    >
      <span className="sr-only">Reconnecting…</span>
      <span
        aria-hidden="true"
        className="size-1.5 animate-pulse rounded-full bg-ember"
        style={{ animationDuration: "1.4s" }}
      />
    </div>
  );
}

/**
 * Route error boundary that treats transient failures as recoverable: it
 * silently invalidates and retries (with a short backoff) before it ever shows
 * the user an error screen. The "Runtime Exception" surface is the last resort,
 * reserved for errors that survived recovery.
 */
export function RouteErrorBoundary({ error, reset }: { error: Error; reset: () => void }) {
  const router = useRouter();
  const [phase, setPhase] = useState<"recovering" | "fatal">(() =>
    classifyRouteError(error) === "fatal" ? "fatal" : "recovering",
  );
  const attempts = useRef(0);
  // Identity of the error currently being handled — a new error resets nothing
  // but the retry budget stays per-boundary-instance, which is what we want.
  const lastError = useRef<unknown>(null);

  useEffect(() => {
    if (lastError.current === error) return;
    lastError.current = error;

    const kind = classifyRouteError(error);

    if (kind === "reload" && reloadOnceForStaleChunk()) return;

    if (kind !== "fatal" && attempts.current < MAX_SILENT_ATTEMPTS) {
      attempts.current += 1;
      setPhase("recovering");
      const delay = 250 * attempts.current;
      const t = setTimeout(() => {
        void router.invalidate().finally(() => reset());
      }, delay);
      return () => clearTimeout(t);
    }

    setPhase("fatal");
    console.error(error);
    reportLovableError(error, { boundary: "tanstack_root_error_component" });
  }, [error, reset, router]);

  if (phase === "recovering") return <RecoveringScreen />;

  return (
    <div className="app-gutter flex min-h-screen items-center justify-center bg-obsidian py-12 text-silver safe-y">
      <div className="max-w-md text-center">
        <div className="font-mono text-[10px] tracking-[0.3em] text-breach uppercase mb-6">
          RUNTIME_EXCEPTION
        </div>
        <h1 className="text-2xl font-bold tracking-tight">Session interrupted</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          {error.message || "Something went off-protocol."}
        </p>
        <div className="mt-8 flex flex-col justify-center gap-3 sm:flex-row">
          <Button
            onClick={() => {
              attempts.current = 0;
              lastError.current = null;
              setPhase("recovering");
              void router.invalidate().finally(() => reset());
            }}
            className="w-full px-6 font-mono text-xs font-bold uppercase tracking-widest sm:w-auto"
          >
            Retry
          </Button>
          <Button asChild variant="outline" className="w-full px-6 font-mono text-xs uppercase tracking-widest sm:w-auto">
            <a href="/">Origin</a>
          </Button>
        </div>
      </div>
    </div>
  );
}
