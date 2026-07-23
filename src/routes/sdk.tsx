import { createFileRoute } from "@tanstack/react-router";
import { Nav } from "@/components/nav";

export const Route = createFileRoute("/sdk")({
  head: () => ({
    meta: [
      { title: "Stack'd SDK — Public API" },
      { name: "description", content: "Tiny TypeScript client for the Stack'd webhook & public API surface." },
      { property: "og:title", content: "Stack'd SDK" },
      { property: "og:description", content: "Tiny TypeScript client for the Stack'd webhook & public API surface." },
    ],
  }),
  component: SdkPage,
});

const INSTALL = `# via npm
npm install @stackd/sdk

# or copy src/lib/sdk/stackd-client.ts directly`;

const USAGE = `import { StackdClient } from "@stackd/sdk";

const stackd = new StackdClient({
  baseUrl: "https://stack-d.lovable.app",
});

// 1. Verify an incoming webhook
export async function handleWebhook(req: Request) {
  const raw = await req.text();
  const sig = req.headers.get("x-stackd-signature") ?? "";
  const ok = await stackd.verifyWebhookSignature(raw, sig, process.env.STACKD_SECRET!);
  if (!ok) return new Response("bad sig", { status: 401 });

  const event = await stackd.parseEvent(raw);
  switch (event.type) {
    case "session.completed": /* … */ break;
    case "achievement.unlocked": /* … */ break;
  }
  return new Response("ok");
}`;

const EVENTS = [
  ["session.completed", "A user finished a focus session"],
  ["session.started", "A user began a focus session"],
  ["achievement.unlocked", "A user earned an achievement"],
  ["challenge.completed", "A user completed a challenge"],
  ["prestige.reached", "A user ascended prestige"],
  ["friend.added", "A user added a friend"],
];

function CodeBlock({ code }: { code: string }) {
  return (
    <pre className="bg-black/60 border border-white/10 rounded-md p-4 overflow-x-auto text-xs text-silver font-mono">
      <code>{code}</code>
    </pre>
  );
}

function SdkPage() {
  return (
    <div className="min-h-screen bg-obsidian text-silver">
      <Nav />
      <main className="max-w-4xl mx-auto px-6 pt-28 pb-24 space-y-12">
        <header>
          <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Developers</p>
          <h1 className="mt-3 text-4xl md:text-6xl font-serif">Stack'd SDK</h1>
          <p className="mt-3 text-silver-dim max-w-xl">
            Tiny, zero-dependency TypeScript client for the Stack'd webhook surface. Runs in
            Node, Deno, Bun, browsers, and edge workers.
          </p>
        </header>

        <section>
          <h2 className="font-serif text-2xl">Install</h2>
          <div className="mt-4"><CodeBlock code={INSTALL} /></div>
        </section>

        <section>
          <h2 className="font-serif text-2xl">Verify a webhook</h2>
          <p className="mt-2 text-sm text-silver-dim">HMAC-SHA256, timing-safe compare, WebCrypto under the hood.</p>
          <div className="mt-4"><CodeBlock code={USAGE} /></div>
        </section>

        <section>
          <h2 className="font-serif text-2xl">Event types</h2>
          <ul className="mt-4 border border-white/10 rounded-md divide-y divide-white/5">
            {EVENTS.map(([name, desc]) => (
              <li key={name} className="p-4 flex justify-between items-center text-sm">
                <code className="font-mono text-ember">{name}</code>
                <span className="text-silver-dim">{desc}</span>
              </li>
            ))}
          </ul>
        </section>

        <section>
          <h2 className="font-serif text-2xl">Endpoints</h2>
          <ul className="mt-4 space-y-2 text-sm">
            <li className="border border-white/10 rounded p-3 font-mono text-xs text-silver-dim">
              <span className="text-ember">GET</span> /api/public/health · liveness
            </li>
          </ul>
        </section>

        <section className="border border-ember/30 rounded-md p-6 bg-ember/[0.04]">
          <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Manage webhooks</p>
          <p className="mt-2 text-silver">
            Create, rotate secrets, and pick events at{" "}
            <a href="/webhooks" className="text-ember underline">/webhooks</a>.
          </p>
        </section>
      </main>
    </div>
  );
}
