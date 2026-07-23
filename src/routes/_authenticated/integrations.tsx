import { createFileRoute } from "@tanstack/react-router";
import { useEffect } from "react";
import { Nav } from "@/components/nav";
import { track } from "@/lib/observability";

export const Route = createFileRoute("/_authenticated/integrations")({
  head: () => ({
    meta: [
      { title: "Integrations — Stack'd" },
      { name: "description", content: "Connect Stack'd to the calendars, notes, and rooms you already keep." },
      { property: "og:title", content: "Stack'd · Integrations" },
      { property: "og:description", content: "Connect Stack'd to the calendars, notes, and rooms you already keep." },
    ],
  }),
  component: IntegrationsPage,
});

interface Integration {
  slug: string;
  name: string;
  tagline: string;
  status: "shipped" | "beta" | "soon";
  href?: string;
}

const INTEGRATIONS: Integration[] = [
  { slug: "webhooks", name: "Webhooks",       tagline: "Push every session event to your own endpoint.", status: "shipped", href: "/webhooks" },
  { slug: "sdk",      name: "TypeScript SDK", tagline: "Verify signatures and parse events in five lines.", status: "shipped", href: "/sdk" },
  { slug: "mcp",      name: "Agent (MCP)",    tagline: "Let Claude or Cursor read your focus history.", status: "shipped", href: "/mcp" },
  { slug: "calendar", name: "Calendar",       tagline: "Auto-block deep-work slots on Google or Apple Calendar.", status: "soon" },
  { slug: "notion",   name: "Notion",         tagline: "Send session notes and tags straight into a database.", status: "soon" },
  { slug: "discord",  name: "Discord",        tagline: "Announce room openings and streak milestones to a channel.", status: "soon" },
  { slug: "slack",    name: "Slack",          tagline: "Focus-mode presence and shared session invites.", status: "soon" },
  { slug: "raycast",  name: "Raycast",        tagline: "Start a session without leaving your keyboard.", status: "soon" },
];

function Badge({ status }: { status: Integration["status"] }) {
  const map = {
    shipped: "border-pulse/50 text-pulse",
    beta:    "border-ember/50 text-ember",
    soon:    "border-white/20 text-silver-dim",
  }[status];
  const label = { shipped: "Live", beta: "Beta", soon: "Soon" }[status];
  return (
    <span className={`font-mono text-[9px] tracking-[0.3em] uppercase px-2 py-1 rounded-full border ${map}`}>
      {label}
    </span>
  );
}

function IntegrationsPage() {
  useEffect(() => { track("integration.viewed"); }, []);
  return (
    <div className="min-h-screen bg-obsidian text-silver">
      <Nav />
      <main className="max-w-5xl mx-auto px-6 pt-28 pb-24 space-y-12">
        <header>
          <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Ecosystem</p>
          <h1 className="mt-3 text-4xl md:text-6xl font-serif">Integrations</h1>
          <p className="mt-3 text-silver-dim max-w-xl">
            Stack'd is a small, well-behaved neighbor. Wire it into the tools you already use.
          </p>
        </header>

        <section className="grid gap-4 md:grid-cols-2">
          {INTEGRATIONS.map((i) => {
            const inner = (
              <div className="h-full glass rounded-2xl p-6 border border-white/10 hover:border-ember/40 transition group">
                <div className="flex items-start justify-between">
                  <h2 className="font-serif text-xl text-silver group-hover:text-ember-glow transition">{i.name}</h2>
                  <Badge status={i.status} />
                </div>
                <p className="mt-3 text-sm text-silver-dim leading-relaxed">{i.tagline}</p>
                {i.href && (
                  <p className="mt-4 font-mono text-[10px] tracking-[0.3em] uppercase text-ember">
                    Open →
                  </p>
                )}
              </div>
            );
            return i.href
              ? <a key={i.slug} href={i.href} className="block">{inner}</a>
              : <div key={i.slug}>{inner}</div>;
          })}
        </section>

        <section className="border border-ember/30 rounded-md p-6 bg-ember/[0.04]">
          <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Building one?</p>
          <p className="mt-2 text-silver">
            The public webhook surface and TypeScript SDK are enough to build any of the "Soon" tiles yourself.
            See <a href="/sdk" className="text-ember underline">/sdk</a> for the contract.
          </p>
        </section>
      </main>
    </div>
  );
}
