import type { ReactNode } from "react";

import { EmptyState } from "@/components/empty-state";

/**
 * The four states every data screen owes the user: loading, error, empty, and
 * content. Screens used to hand-roll these — 11 rendered a bare "Loading…"
 * string, 10 rendered nothing at all, and four showed their *empty* state
 * during load, which told people with data that they had none.
 *
 * `QueryBoundary` makes the correct order the default: never show "nothing
 * here" before knowing whether there is something here.
 */

/** Shimmering placeholder block. Decorative — never announced. */
export function Skeleton({ className = "" }: { className?: string }) {
  return (
    <div
      aria-hidden="true"
      className={`rounded-lg bg-white/[0.04] animate-[skeleton-shimmer_2.4s_ease-in-out_infinite] ${className}`}
    />
  );
}

/** Stand-in for a list, sized to roughly match the rows it replaces. */
export function SkeletonList({ rows = 4, className = "" }: { rows?: number; className?: string }) {
  return (
    <div className={`space-y-3 ${className}`} aria-hidden="true">
      {Array.from({ length: rows }).map((_, i) => (
        <Skeleton
          key={i}
          className="h-16 w-full"
          // Slight downward fade so a long list doesn't read as a solid block.
          {...{ style: { opacity: Math.max(0.25, 1 - i * 0.14) } }}
        />
      ))}
    </div>
  );
}

/** Stand-in for a stat/card grid. */
export function SkeletonCards({ count = 3, className = "" }: { count?: number; className?: string }) {
  return (
    <div className={`grid gap-6 sm:grid-cols-2 lg:grid-cols-3 ${className}`} aria-hidden="true">
      {Array.from({ length: count }).map((_, i) => (
        <Skeleton key={i} className="h-40 w-full" />
      ))}
    </div>
  );
}

/**
 * Loading announcement for assistive tech.
 *
 * The visual skeleton is aria-hidden, so without this a screen-reader user
 * hears silence between navigating and the content arriving.
 */
export function LoadingAnnouncer({ label = "Loading" }: { label?: string }) {
  return (
    <span role="status" aria-live="polite" className="sr-only">
      {label}
    </span>
  );
}

/**
 * Error panel with a retry. `role="alert"` because a failure that appears after
 * the user is already reading the page must interrupt — this was previously
 * absent from every authenticated screen.
 */
export function ErrorPanel({
  title = "Couldn't load this.",
  message,
  onRetry,
  className = "",
}: {
  title?: string;
  message?: string;
  onRetry?: () => void;
  className?: string;
}) {
  return (
    <div
      role="alert"
      className={`glass rounded-2xl border border-breach/20 p-8 text-center ${className}`}
    >
      <div className="font-mono text-[10px] uppercase tracking-[0.3em] text-breach">
        Signal lost
      </div>
      <p className="mt-3 text-sm text-silver">{title}</p>
      {message && <p className="mt-2 text-xs text-muted-foreground">{message}</p>}
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-6 cursor-pointer rounded-lg border border-silver/25 px-5 py-2 font-mono text-[10px] uppercase tracking-widest text-silver transition-colors hover:bg-white/5"
        >
          Retry
        </button>
      )}
    </div>
  );
}

/**
 * Which of the four states should render, given a query's flags.
 *
 * Extracted from the component so the precedence — the thing that was actually
 * wrong across four screens — is testable without a DOM renderer.
 */
export type BoundaryState = "loading" | "error" | "empty" | "content";

export function resolveBoundaryState({
  isPending,
  isError = false,
  isEmpty = false,
  hasEmptyUi = false,
}: {
  isPending: boolean;
  isError?: boolean;
  isEmpty?: boolean;
  hasEmptyUi?: boolean;
}): BoundaryState {
  // Loading first: "there is nothing" is not knowable until the fetch lands.
  if (isPending) return "loading";
  if (isError) return "error";
  if (isEmpty && hasEmptyUi) return "empty";
  return "content";
}

type QueryBoundaryProps = {
  isPending: boolean;
  isError?: boolean;
  error?: unknown;
  onRetry?: () => void;
  /** Render the empty state instead of children. Only consulted once loaded. */
  isEmpty?: boolean;
  loadingLabel?: string;
  skeleton?: ReactNode;
  empty?: ReactNode;
  errorTitle?: string;
  children: ReactNode;
};

/**
 * Renders exactly one of: skeleton, error, empty, content — in that precedence.
 *
 * The precedence is the whole point. `isEmpty` is only consulted after loading
 * finishes and no error occurred, which is what stops "Your circle is empty"
 * appearing while the circle is still being fetched.
 */
export function QueryBoundary({
  isPending,
  isError = false,
  error,
  onRetry,
  isEmpty = false,
  loadingLabel = "Loading",
  skeleton,
  empty,
  errorTitle,
  children,
}: QueryBoundaryProps) {
  const state = resolveBoundaryState({
    isPending,
    isError,
    isEmpty,
    hasEmptyUi: empty !== undefined && empty !== null,
  });

  if (state === "loading") {
    return (
      <>
        <LoadingAnnouncer label={loadingLabel} />
        {skeleton ?? <SkeletonList />}
      </>
    );
  }

  if (state === "error") {
    return (
      <ErrorPanel
        title={errorTitle}
        message={error instanceof Error ? error.message : undefined}
        onRetry={onRetry}
      />
    );
  }

  if (state === "empty") return <>{empty}</>;

  return <>{children}</>;
}

/** Re-exported so screens reach for one import when wiring their four states. */
export { EmptyState };
