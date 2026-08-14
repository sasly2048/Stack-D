import { Link, useNavigate } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/use-auth";
import { Logo } from "@/components/logo";
import { useLabs } from "@/hooks/use-labs";
import { routeVisible } from "@/lib/feature-flags";
import { useNavTier, type NavTier } from "@/hooks/use-nav-tier";
import { MobileNavMenu } from "@/components/mobile-nav-menu";

type NavItem = { to: string; label: string; visibility: string };

// Tablet and below show only "New Session" + the drawer trigger, so every link
// here is desktop-only (`lg`). The drawer is the single source of truth for
// tablet/phone navigation.
const AUTHED_ITEMS: NavItem[] = [
  { to: "/dashboard", label: "Analytics", visibility: "hidden lg:inline" },
  { to: "/groups", label: "Circles", visibility: "hidden lg:inline" },
  { to: "/seasons", label: "Seasons", visibility: "hidden xl:inline" },
  { to: "/leaderboard", label: "Ranks", visibility: "hidden lg:inline" },
  { to: "/challenges", label: "Rites", visibility: "hidden xl:inline" },
  { to: "/insights", label: "Insights", visibility: "hidden xl:inline" },
  { to: "/timeline", label: "Timeline", visibility: "hidden 2xl:inline" },
  { to: "/feed", label: "Feed", visibility: "hidden 2xl:inline" },
  { to: "/friends", label: "Friends", visibility: "hidden xl:inline" },
  { to: "/achievements", label: "Marks", visibility: "hidden 2xl:inline" },
  { to: "/wrapped", label: "Wrapped", visibility: "hidden 2xl:inline" },
  { to: "/vault", label: "Vault", visibility: "hidden 2xl:inline" },
  { to: "/dna", label: "DNA", visibility: "hidden 2xl:inline" },
  { to: "/replay", label: "Replay", visibility: "hidden 2xl:inline" },
  { to: "/partners", label: "Partners", visibility: "hidden 2xl:inline" },
  { to: "/capsule", label: "Capsule", visibility: "hidden 2xl:inline" },
  { to: "/profile", label: "Profile", visibility: "hidden lg:inline" },
  { to: "/companion", label: "Atlas", visibility: "hidden 2xl:inline" },
];

/** Shared hover treatment: soft ember glow + 1px lift, no layout shift. */
const NAV_GLOW =
  "rounded px-2 py-1 transition-[color,background-color,box-shadow,transform] duration-200 ease-[var(--ease-ritual)] hover:text-silver hover:bg-white/5 hover:shadow-[0_0_14px_-4px_var(--color-ember,#F0A968)] hover:-translate-y-px";


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

  // Every signed-in user gets the identical menu. Tier is a progression signal,
  // not a permission, so it no longer hides destinations — access is enforced by
  // route guards and RLS, and hiding links only made the app feel broken.
  const menuLinks = AUTHED_ITEMS.filter((item) => routeVisible(item.to, labs)).map((item) => ({
    to: item.to,
    label: item.label,
  }));


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
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between gap-6 sm:gap-10 lg:gap-12">
        <Link to={user ? "/dashboard" : "/"} className="mr-auto flex min-w-0 items-center gap-2 sm:gap-3">
          <Logo className="size-7 shrink-0" />
          <span className="whitespace-nowrap font-mono text-xs tracking-[0.3em] uppercase">
            Stack&apos;d{" "}
            <span className="hidden text-muted-foreground sm:inline">/ Protocol.01</span>
          </span>
        </Link>
        <div className="flex shrink-0 items-center gap-4 lg:gap-6 text-[10px] font-mono uppercase tracking-widest text-muted-foreground">
          {user ? (
            <>
              {AUTHED_ITEMS.map((item) => {
                if (!routeVisible(item.to, labs)) return null;
                return (
                  <Link
                    key={item.to}
                    to={item.to}
                    // aria-current is what actually tells a screen reader which
                    // page you are on; the ember colour alone conveys it to
                    // sighted users only.
                    className={`relative ${NAV_GLOW} focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember focus-visible:ring-offset-4 focus-visible:ring-offset-obsidian ${item.visibility}`}
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
                className={`hidden lg:inline cursor-pointer ${NAV_GLOW} active:scale-[0.99]`.concat(" ")+" disabled:cursor-not-allowed disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember focus-visible:ring-offset-4 focus-visible:ring-offset-obsidian"
              >
                {signingOut ? "Exiting…" : "Exit"}
              </button>
              <MobileNavMenu links={menuLinks} onSignOut={signOut} signingOut={signingOut} />
            </>
          ) : (
            <>
              <Link
                to="/philosophy"
                className={`relative hidden sm:inline-flex items-center gap-2 ${NAV_GLOW}`}
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
                to="/philosophy"
                className={`sm:hidden ${NAV_GLOW}`}
                activeProps={{ className: "!text-ember" }}
              >
                Philosophy
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
