import { Link, useRouterState } from "@tanstack/react-router";

/**
 * Explore's merged entries (Android parity): overlapping pages read as one
 * destination with tabs between the members, instead of eight separate links.
 * Renders nothing outside a hub, so it can sit at the top of any page.
 */
const HUBS: { to: string; label: string }[][] = [
  [
    { to: "/feed", label: "Feed" },
    { to: "/friends", label: "Friends" },
    { to: "/circles", label: "Circles" },
    { to: "/partners", label: "Partners" },
    { to: "/groups", label: "Groups" },
  ],
  [
    { to: "/timeline", label: "Timeline" },
    { to: "/replay", label: "Replay" },
    { to: "/wrapped", label: "Wrapped" },
    { to: "/dna", label: "Focus DNA" },
  ],
];

export function HubTabs({ className = "" }: { className?: string }) {
  const path = useRouterState({ select: (s) => s.location.pathname });
  const hub = HUBS.find((h) => h.some((t) => t.to === path));
  if (!hub) return null;
  return (
    // Phones: the row runs past the edge, so it fades there (says "more this
    // way") and pads its end so the last tab can scroll clear of the fade.
    <div
      role="tablist"
      className={`-mt-2 mb-8 flex gap-2 overflow-x-auto overscroll-x-contain pb-1 max-sm:pr-10 max-sm:[mask-image:linear-gradient(to_right,#000_82%,transparent)] ${className}`}
    >
      {hub.map((t) => {
        const on = t.to === path;
        return (
          <Link
            key={t.to}
            to={t.to}
            role="tab"
            aria-selected={on}
            className={`shrink-0 rounded-full px-4 py-2 text-sm transition-colors duration-200 ease-[var(--ease-ritual)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember ${
              on
                ? "bg-silver font-semibold text-obsidian"
                : "bg-white/[0.06] text-silver hover:bg-white/10"
            }`}
          >
            {t.label}
          </Link>
        );
      })}
    </div>
  );
}
