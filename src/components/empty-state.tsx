import type { ReactNode } from "react";

export function EmptyState({
  icon = "✦",
  title,
  description,
  action,
}: {
  icon?: string;
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    // role="status" so a screen reader hears "no results" rather than silence
    // after a list finishes loading with nothing in it.
    <div role="status" className="glass mx-auto w-full rounded-lg px-5 py-8 text-center sm:p-10">
      <div aria-hidden="true" className="mb-3 text-4xl leading-none">
        {icon}
      </div>
      <div className="font-mono text-[10px] uppercase tracking-[0.3em] text-muted-foreground">
        A quiet space
      </div>
      <div className="mx-auto mt-2 max-w-md text-lg font-semibold leading-snug text-silver">{title}</div>
      <p className="mx-auto mt-2 max-w-sm text-sm leading-relaxed text-muted-foreground">{description}</p>
      {action && <div className="mt-6">{action}</div>}
    </div>
  );
}
