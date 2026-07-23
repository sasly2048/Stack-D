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

type Cmd = { label: string; hint?: string; to?: string; run?: () => void; shortcut?: string };

export function CommandPalette() {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const { user } = useAuth();

  // Cmd+K / Ctrl+K toggle; also `?` shows shortcuts (opens palette)
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      const isMod = e.metaKey || e.ctrlKey;
      const target = e.target as HTMLElement | null;
      const inField =
        target && ((target.tagName === "INPUT" && target.getAttribute("type") !== "checkbox") ||
          target.tagName === "TEXTAREA" ||
          target.isContentEditable);

      if (isMod && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setOpen((v) => !v);
        return;
      }
      if (inField) return;

      // Single-key shortcuts (only when authed)
      if (!user) return;
      const map: Record<string, string> = {
        g: "/dashboard",
        s: "/start",
        f: "/friends",
        t: "/timeline",
        i: "/insights",
        a: "/achievements",
        c: "/challenges",
        v: "/vault",
      };
      const dest = map[e.key.toLowerCase()];
      if (dest && !isMod && !e.shiftKey && !e.altKey) {
        e.preventDefault();
        navigate({ to: dest });
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [navigate, user]);

  const go = (to: string) => () => {
    setOpen(false);
    navigate({ to });
  };

  const nav: Cmd[] = user
    ? [
        { label: "Analytics", to: "/dashboard", shortcut: "G" },
        { label: "New Session", to: "/start", shortcut: "S" },
        { label: "Timeline", to: "/timeline", shortcut: "T" },
        { label: "Insights", to: "/insights", shortcut: "I" },
        { label: "Friends", to: "/friends", shortcut: "F" },
        { label: "Achievements", to: "/achievements", shortcut: "A" },
        { label: "Challenges", to: "/challenges", shortcut: "C" },
        { label: "Circles", to: "/groups" },
        { label: "Leaderboard", to: "/leaderboard" },
        { label: "Feed", to: "/feed" },
        { label: "Memory Vault", to: "/vault", shortcut: "V" },
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
      <CommandInput placeholder="Type a command or search…" />
      <CommandList>
        <CommandEmpty>No results.</CommandEmpty>
        <CommandGroup heading="Navigate">
          {nav.map((c) => (
            <CommandItem key={c.label} onSelect={c.to ? go(c.to) : c.run}>
              <span className="flex-1">{c.label}</span>
              {c.shortcut && (
                <span className="font-mono text-[10px] tracking-widest text-muted-foreground">
                  {c.shortcut}
                </span>
              )}
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
