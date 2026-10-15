import { Link } from "@tanstack/react-router";
import { Menu } from "lucide-react";
import { useState } from "react";
import { Drawer } from "vaul";

export type MobileNavLink = { to: string; label: string };

/**
 * On phones and tablets most nav links are hidden behind responsive `hidden`
 * classes, which left whole sections (Profile, Analytics…) unreachable. This
 * drawer lists every link the current user is allowed to see. It is a real
 * drawer: it follows the finger, flicks shut with velocity, and resists at
 * its edge, entering and leaving along the same path from the right.
 */
export function MobileNavMenu({
  links,
  onSignOut,
  signingOut,
}: {
  links: MobileNavLink[];
  onSignOut: () => void;
  signingOut: boolean;
}) {
  const [open, setOpen] = useState(false);

  return (
    <Drawer.Root direction="right" open={open} onOpenChange={setOpen}>
      <Drawer.Trigger
        aria-label="Open navigation menu"
        className="grid size-11 shrink-0 place-items-center text-muted-foreground transition-[color,filter] duration-200 ease-[var(--ease-ritual)] hover:text-silver hover:[filter:drop-shadow(0_0_10px_var(--icon-glow-ember))] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember focus-visible:ring-offset-2 focus-visible:ring-offset-obsidian"
      >
        <Menu className="size-4" aria-hidden="true" />
      </Drawer.Trigger>

      <Drawer.Portal>
        <Drawer.Overlay className="fixed inset-0 z-[var(--z-overlay)] bg-obsidian/70" />
        <Drawer.Content className="fixed inset-y-0 right-0 z-[var(--z-dialog)] flex w-3/4 max-w-sm flex-col overflow-y-auto overscroll-contain border-l border-white/10 bg-obsidian p-5 pt-[max(1.25rem,env(safe-area-inset-top))] pb-[calc(1.25rem+env(safe-area-inset-bottom))] text-silver shadow-[var(--shadow-overlay)] outline-none sm:p-6">
          <div className="text-left">
            <Drawer.Title className="font-mono text-xs uppercase tracking-[0.3em] text-silver">
              Explore
            </Drawer.Title>
            <Drawer.Description className="mt-2 font-mono text-[10px] uppercase tracking-widest text-muted-foreground">
              All sections
            </Drawer.Description>
          </div>

          <nav aria-label="All sections" className="mt-6 grid gap-1 pb-8">
            {links.map((item) => (
              <Link
                key={item.to}
                to={item.to}
                onClick={() => setOpen(false)}
                className="px-3 py-3 font-mono text-xs uppercase tracking-[0.2em] text-muted-foreground transition-[color,text-shadow] duration-200 ease-[var(--ease-ritual)] hover:text-silver hover:[text-shadow:0_0_14px_var(--text-glow-ember)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember"
                activeProps={{
                  className:
                    "text-ember hover:text-ember [text-shadow:0_0_16px_var(--text-glow-ember-strong)]",
                  "aria-current": "page",
                }}
              >
                {item.label}
              </Link>
            ))}

            <button
              type="button"
              onClick={() => {
                setOpen(false);
                onSignOut();
              }}
              disabled={signingOut}
              className="mt-4 cursor-pointer px-3 py-3 text-left font-mono text-xs uppercase tracking-[0.2em] text-muted-foreground transition-[color,text-shadow] duration-200 ease-[var(--ease-ritual)] hover:text-silver hover:[text-shadow:0_0_14px_var(--text-glow-ember)] disabled:opacity-50"
            >
              {signingOut ? "Exiting…" : "Exit"}
            </button>
          </nav>
        </Drawer.Content>
      </Drawer.Portal>
    </Drawer.Root>
  );
}
