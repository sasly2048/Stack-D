import { useEffect, useState } from "react";
import { useNavigate } from "@tanstack/react-router";
import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator,
} from "@/components/ui/command";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/use-auth";
import { useLabs } from "@/hooks/use-labs";
import { routeVisible } from "@/lib/feature-flags";

type Cmd = { label: string; hint?: string; to?: string; run?: () => void };

export function CommandPalette() {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const { user } = useAuth();
  const [labs] = useLabs();

  // Cmd+K / Ctrl+K toggle only. Single-letter shortcuts were removed because
  // they yanked users off active pages (including live focus sessions) whenever
  // a button or other non-field element held focus.
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      const isMod = e.metaKey || e.ctrlKey;
      if (isMod && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setOpen((v) => !v);
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [navigate, user]);

  const go = (to: string) => () => {
    setOpen(false);
    navigate({ to });
  };

  const allNav: Cmd[] = user
    ? [
        { label: "Analytics", to: "/dashboard" },
        { label: "New Session", to: "/start" },
        { label: "Timeline", to: "/timeline" },
        { label: "Insights", to: "/insights" },
        { label: "Friends", to: "/friends" },
        { label: "Achievements", to: "/achievements" },
        { label: "Stack Wrapped", to: "/wrapped" },
        { label: "Challenges", to: "/challenges" },
        { label: "Circles", to: "/groups" },
        { label: "Leaderboard", to: "/leaderboard" },
        { label: "Feed", to: "/feed" },
        { label: "Memory Vault", to: "/vault" },
        { label: "Study Circles", to: "/circles" },
        { label: "Seasons", to: "/seasons" },
        { label: "Focus Replay", to: "/replay" },
        { label: "Productivity DNA", to: "/dna" },
        { label: "Webhooks", to: "/webhooks" },
        { label: "SDK", to: "/sdk" },
        { label: "Time Capsule", to: "/capsule" },
        { label: "Trust & Safety", to: "/trust" },
        { label: "Moderation (hosts)", to: "/trust/moderation" },
        { label: "Study Companion", to: "/companion" },
        { label: "Partners", to: "/partners" },
        { label: "Profile", to: "/profile" },
      ]
    : [
        { label: "Home", to: "/" },
        { label: "Philosophy", to: "/philosophy" },
        { label: "Sign in", to: "/auth" },
      ];

  const nav: Cmd[] = allNav.filter((c) => !c.to || routeVisible(c.to, labs));

  const actions: Cmd[] = user
    ? [
        {
          label: "Sign out",
          run: async () => {
            setOpen(false);
            await supabase.auth.signOut();
            navigate({ to: "/", replace: true });
          },
        },
      ]
    : [];

  return (
    <CommandDialog open={open} onOpenChange={setOpen}>
      <CommandInput aria-label="Search commands" placeholder="Type a command or search…" />
      <CommandList>
        {/* "No results." alone leaves the user guessing whether they mistyped
            or the thing simply isn't reachable from here. */}
        <CommandEmpty>
          <span className="block text-sm text-silver-dim">Nothing matches that.</span>
          <span className="mt-1 block font-mono text-[10px] uppercase tracking-widest text-muted-foreground">
            Try a page name, or press Esc to close
          </span>
        </CommandEmpty>
        <CommandGroup heading="Navigate">
          {nav.map((c) => (
            <CommandItem key={c.label} onSelect={c.to ? go(c.to) : c.run}>
              <span className="flex-1">{c.label}</span>
            </CommandItem>
          ))}
        </CommandGroup>
        {actions.length > 0 && (
          <>
            <CommandSeparator />
            <CommandGroup heading="Actions">
              {actions.map((c) => (
                <CommandItem key={c.label} onSelect={c.run}>
                  {c.label}
                </CommandItem>
              ))}
            </CommandGroup>
          </>
        )}
      </CommandList>
    </CommandDialog>
  );
}
