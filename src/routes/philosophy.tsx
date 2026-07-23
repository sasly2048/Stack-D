import { createFileRoute, Link } from "@tanstack/react-router";
import { Nav } from "@/components/nav";
import { ScrollVelocity } from "@/components/fx/scroll-velocity";
import { useBrandProse } from "@/components/ai-prose";

export const Route = createFileRoute("/philosophy")({
  head: () => ({
    meta: [
      { title: "Philosophy — Stack'd" },
      { name: "description", content: "The protocol behind Stack'd: kinetic verdict, atomic sync, and earned time. A manifesto for presence." },
      { property: "og:title", content: "Philosophy — Stack'd" },
      { property: "og:description", content: "A manifesto for presence in a notification-saturated world." },
    ],
  }),
  component: Philosophy,
});

const PILLARS = [
  {
    k: "01",
    h: "Kinetic Verdict",
    p: "Multi-axis tilt, accelerometer spikes, the loss of a wake-lock. The phone itself is the witness. There is no scoreboard to game — only the steady proof of stillness.",
  },
  {
    k: "02",
    h: "Atomic Sync",
    p: "Every device in the room shares one truth, broadcast in milliseconds. No host machine, no spectator mode. When one of you breaks, all of you feel it.",
  },
  {
    k: "03",
    h: "Earned Time",
    p: "Lifetime presence accumulates the way wealth used to: slowly, deliberately, in private. Every minute disconnected is a minute reclaimed from the feed.",
  },
];

const TENETS = [
  "Attention is the last finite currency.",
  "Stillness is a practice, not an absence.",
  "Protocol outperforms restriction.",
  "Multiplayer accountability beats solo discipline.",
  "The room sets the standard. The protocol enforces it.",
  "Non-digital space is a human right.",
];

function Philosophy() {
  const { data: prose } = useBrandProse();
  return (
    <div className="min-h-screen bg-obsidian text-silver overflow-x-hidden">
      <Nav />

      <header className="pt-40 pb-20 px-6 max-w-6xl mx-auto animate-entrance">
        <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember mb-8">
          Manifesto / Protocol.00
        </div>
        <h1 className="text-5xl sm:text-6xl lg:text-[88px] font-extrabold leading-[0.9] tracking-tighter mb-10 text-balance max-w-4xl">
          We did not lose<br /> our attention.<br />
          <span className="text-muted-foreground">It was harvested.</span>
        </h1>
        <p className="max-w-2xl text-lg text-silver-dim leading-relaxed mb-6 text-balance">
          {prose.philosophyOpener}
        </p>
        <p className="max-w-2xl text-lg text-silver-dim leading-relaxed">
          Stack&apos;d is a private protocol for reclaiming the room. Phones go down, the
          deep work session begins, and a shared timer becomes the only thing on screen — until
          even that fades. What follows is what we built it on.
        </p>
      </header>

      <section className="py-20 border-t border-white/5 bg-neutral-900/30">
        <div className="max-w-6xl mx-auto px-6 grid md:grid-cols-3 gap-12">
          {PILLARS.map((f) => (
            <div key={f.k} className="border-t border-white/10 pt-8">
              <div className="font-mono text-[10px] tracking-[0.3em] text-ember mb-6">{f.k}</div>
              <h3 className="text-2xl font-bold tracking-tight mb-3">{f.h}</h3>
              <p className="text-sm text-silver-dim leading-relaxed">{f.p}</p>
            </div>
          ))}
        </div>
      </section>

      <div className="border-y border-white/5 py-8 bg-obsidian">
        <ScrollVelocity words={["Presence", "Ritual", "Signal", "Stillness", "Protocol", "Focus"]} />
      </div>


      <section className="py-24 px-6 max-w-5xl mx-auto">
        <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground mb-10">
          Tenets / Read aloud before the session
        </div>
        <ol className="space-y-6">
          {TENETS.map((t, i) => (
            <li key={t} className="grid grid-cols-[auto_1fr] gap-8 items-baseline border-b border-white/5 pb-6">
              <span className="font-mono text-xs text-ember tabular-nums">
                {String(i + 1).padStart(2, "0")}
              </span>
              <p className="text-2xl sm:text-3xl font-bold tracking-tight text-balance">{t}</p>
            </li>
          ))}
        </ol>
      </section>

      <section className="py-24 px-6 border-t border-white/5 bg-neutral-900/30">
        <div className="max-w-4xl mx-auto text-center">
          <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember mb-6">
            The Invitation
          </div>
          <h2 className="text-4xl sm:text-5xl font-extrabold tracking-tighter mb-6">
            Hold the silence. With friends.
          </h2>
          <p className="text-base text-silver-dim italic max-w-xl mx-auto mb-10 text-balance">
            &ldquo;{prose.philosophyClosing}&rdquo;
          </p>
          <Link
            to="/auth"
            className="btn-ember inline-block px-10 py-4 border border-silver/40 rounded-full font-mono text-xs uppercase tracking-widest text-silver"
          >
            Enter the Protocol
          </Link>
        </div>
      </section>

      <footer className="py-12 px-6 border-t border-white/5">
        <div className="max-w-7xl mx-auto text-[10px] font-mono text-muted-foreground/50 uppercase tracking-[0.5em] text-center">
          Non-digital space is a human right.
        </div>
      </footer>
    </div>
  );
}
