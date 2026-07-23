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
    <div className="glass rounded-2xl p-10 text-center">
      <div className="text-4xl mb-3">{icon}</div>
      <div className="font-mono text-[10px] uppercase tracking-[0.3em] text-muted-foreground">
        A quiet space
      </div>
      <div className="mt-2 text-lg font-semibold text-silver">{title}</div>
      <p className="mt-2 text-sm text-muted-foreground max-w-sm mx-auto">{description}</p>
      {action && <div className="mt-6">{action}</div>}
    </div>
  );
}
