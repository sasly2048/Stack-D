import { Link, useNavigate } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/use-auth";
import { Logo } from "@/components/logo";
import { useLabs } from "@/hooks/use-labs";
import { routeVisible } from "@/lib/feature-flags";
import { useNavTier, tierUnlocked, NAV_MIN_TIER, type NavTier } from "@/hooks/use-nav-tier";

type NavItem = { to: string; label: string; visibility: string };

const AUTHED_ITEMS: NavItem[] = [
  { to: "/dashboard", label: "Analytics", visibility: "hidden sm:inline" },
  { to: "/groups", label: "Circles", visibility: "hidden sm:inline" },
  { to: "/seasons", label: "Seasons", visibility: "hidden md:inline" },
  { to: "/leaderboard", label: "Ranks", visibility: "hidden sm:inline" },
  { to: "/challenges", label: "Rites", visibility: "hidden md:inline" },
  { to: "/insights", label: "Insights", visibility: "hidden md:inline" },
  { to: "/timeline", label: "Timeline", visibility: "hidden md:inline" },
  { to: "/feed", label: "Feed", visibility: "hidden md:inline" },
  { to: "/friends", label: "Friends", visibility: "hidden md:inline" },
  { to: "/achievements", label: "Marks", visibility: "hidden md:inline" },
  { to: "/wrapped", label: "Wrapped", visibility: "hidden md:inline" },
  { to: "/vault", label: "Vault", visibility: "hidden md:inline" },
  { to: "/dna", label: "DNA", visibility: "hidden lg:inline" },
  { to: "/replay", label: "Replay", visibility: "hidden lg:inline" },
  { to: "/partners", label: "Partners", visibility: "hidden lg:inline" },
  { to: "/capsule", label: "Capsule", visibility: "hidden lg:inline" },
  { to: "/profile", label: "Profile", visibility: "hidden sm:inline" },
  { to: "/companion", label: "Atlas", visibility: "hidden lg:inline" },
];

const TIER_LABEL: Record<NavTier, string> = {
  starter: "Starter",
  intermediate: "Intermediate",
  advanced: "Advanced",
};

export function Nav() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { tier, power } = useNavTier();
  const [labs] = useLabs();

  const [signingOut, setSigningOut] = useState(false);
  // Resolved after mount: navigator doesn't exist during SSR, and showing a Mac
  // user "Ctrl" (or the reverse) is worse than showing nothing.
  const [modKeyLabel, setModKeyLabel] = useState("Ctrl ");
  useEffect(() => {
    setModKeyLabel(/Mac|iPhone|iPad/.test(navigator.platform || navigator.userAgent) ? "⌘" : "Ctrl ");
  }, []);

  const signOut = async () => {
    // Guarded because sign-out is a network call: a second click while the
    // first is in flight raced two navigations.
    if (signingOut) return;
    setSigningOut(true);
    try {
      await supabase.auth.signOut();
      navigate({ to: "/", replace: true });
    } catch {
      // Failing silently would leave the user apparently signed in with no
      // idea the attempt happened.
      toast.error("Couldn't sign out. Check your connection and retry.");
      setSigningOut(false);
    }
  };

  return (
    <nav className="fixed top-0 inset-x-0 z-50 border-b border-white/5 bg-obsidian/80 backdrop-blur-md safe-top">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between gap-3">
        <Link to={user ? "/dashboard" : "/"} className="flex min-w-0 items-center gap-2 sm:gap-3">
          <Logo className="size-7 shrink-0" />
          <span className="whitespace-nowrap font-mono text-xs tracking-[0.3em] uppercase">
            Stack&apos;d{" "}
            <span className="hidden text-muted-foreground sm:inline">/ Protocol.01</span>
          </span>
        </Link>
        <div className="flex items-center gap-6 text-[10px] font-mono uppercase tracking-widest text-muted-foreground">
          {user ? (
            <>
              {AUTHED_ITEMS.map((item) => {
                if (!routeVisible(item.to, labs)) return null;
                const needed = NAV_MIN_TIER[item.to] ?? "starter";
                const unlocked = tierUnlocked(tier, needed, power);
                if (!unlocked) return null;
                return (
                  <Link
                    key={item.to}
                    to={item.to}
                    // aria-current is what actually tells a screen reader which
                    // page you are on; the ember colour alone conveys it to
                    // sighted users only.
                    className={`relative rounded transition-colors hover:text-silver focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember focus-visible:ring-offset-4 focus-visible:ring-offset-obsidian ${item.visibility}`}
                    activeProps={{
                      className: "text-ember",
                      "aria-current": "page",
                    }}
                  >
                    {item.label}
                  </Link>
                );
              })}
              <span
                title={`${TIER_LABEL[tier]} tier${power ? " · Power" : ""}`}
                className="hidden xl:inline font-mono text-[9px] tracking-[0.3em] text-silver-dim"
              >
                · {TIER_LABEL[tier].slice(0, 3)}
              </span>
              {/* The command palette is bound to Cmd/Ctrl+K but nothing
                  advertised it, so it existed only for people who guessed.
                  Hidden on touch widths, where there is no such key. */}
              <kbd
                aria-hidden="true"
                title="Open the command palette"
                className="hidden lg:inline-flex items-center gap-1 rounded border border-white/10 bg-white/5 px-1.5 py-0.5 font-mono text-[9px] tracking-widest text-muted-foreground"
              >
                {modKeyLabel}K
              </kbd>
              <Link
                to="/start"
                className="btn-ember px-4 py-1.5 border border-silver/20 rounded-full text-silver"
              >
                New Session
              </Link>
              <button
                type="button"
                onClick={signOut}
                disabled={signingOut}
                aria-busy={signingOut}
                className="cursor-pointer rounded transition-all duration-200 ease-[var(--ease-ritual)] hover:text-silver active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember focus-visible:ring-offset-4 focus-visible:ring-offset-obsidian"
              >
                {signingOut ? "Exiting…" : "Exit"}
              </button>
            </>
          ) : (
            <>
              <Link
                to="/philosophy"
                className="relative hover:text-silver transition-colors hidden sm:inline-flex items-center gap-2"
                activeProps={{ className: "!text-ember" }}
              >
                {({ isActive }) => (
                  <>
                    <span
                      className={`size-1 rounded-full transition-all ${
                        isActive ? "bg-ember scale-100" : "bg-transparent scale-0"
                      }`}
                    />
                    Philosophy
                  </>
                )}
              </Link>
              <Link
                to="/auth"
                className="btn-ember px-5 py-1.5 border border-silver/30 rounded-full text-silver"
              >
                Enter
              </Link>
            </>
          )}
        </div>
      </div>
    </nav>
  );
}
