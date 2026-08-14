import { Link } from "@tanstack/react-router";
import { Menu } from "lucide-react";
import { useState } from "react";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";

export type MobileNavLink = { to: string; label: string };

/**
 * On phones and tablets most nav links are hidden behind responsive `hidden`
 * classes, which left whole sections (Profile, Analytics…) unreachable. This
 * drawer lists every link the current user is allowed to see.
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
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger
        aria-label="Open navigation menu"
        className="grid size-9 shrink-0 place-items-center text-muted-foreground transition-[color,filter] duration-200 ease-[var(--ease-ritual)] hover:text-silver hover:[filter:drop-shadow(0_0_10px_var(--icon-glow-ember))] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember focus-visible:ring-offset-2 focus-visible:ring-offset-obsidian lg:hidden"
      >
        <Menu className="size-4" aria-hidden="true" />
      </SheetTrigger>

      <SheetContent
        side="right"
        className="w-[85vw] max-w-sm overflow-y-auto border-white/10 bg-obsidian text-silver"
      >
        <SheetHeader className="text-left">
          <SheetTitle className="font-mono text-xs uppercase tracking-[0.3em] text-silver">
            Navigate
          </SheetTitle>
          <SheetDescription className="font-mono text-[10px] uppercase tracking-widest text-muted-foreground">
            All sections
          </SheetDescription>
        </SheetHeader>

        <nav aria-label="All sections" className="mt-6 grid gap-1 pb-8">
          {links.map((item) => (
            <Link
              key={item.to}
              to={item.to}
              onClick={() => setOpen(false)}
              className="px-3 py-3 font-mono text-xs uppercase tracking-[0.2em] text-muted-foreground transition-[color,text-shadow] duration-200 ease-[var(--ease-ritual)] hover:text-silver hover:[text-shadow:0_0_14px_var(--text-glow-ember)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember"
              activeProps={{ className: "text-ember hover:text-ember [text-shadow:0_0_16px_var(--text-glow-ember-strong)]", "aria-current": "page" }}
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
      </SheetContent>
    </Sheet>
  );
}
