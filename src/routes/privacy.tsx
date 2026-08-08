import { createFileRoute, Link } from "@tanstack/react-router";

import { Nav } from "@/components/nav";
import { siteUrl } from "@/lib/site";

export const Route = createFileRoute("/privacy")({
  head: () => ({
    meta: [
      { title: "Privacy — Stack'd" },
      {
        name: "description",
        content:
          "What Stack'd collects, why it collects it, and how to get it deleted. Motion data never leaves your device.",
      },
      { property: "og:title", content: "Privacy — Stack'd" },
      {
        property: "og:description",
        content:
          "What Stack'd collects, why it collects it, and how to get it deleted. Motion data never leaves your device.",
      },
      { property: "og:type", content: "article" },
      { name: "twitter:card", content: "summary_large_image" },
      { property: "og:url", content: siteUrl("/privacy") },
    ],
    links: [{ rel: "canonical", href: siteUrl("/privacy") }],
  }),
  component: Privacy,
});

/**
 * Each entry describes something the app genuinely does — account rows in
 * Supabase, breach events written by the sensor loop, the pre-auth audit log.
 * Keep it that way: a privacy page that drifts from the code is worse than
 * none, because it is a promise you have stopped keeping.
 */
const SECTIONS: Array<{ k: string; title: string; body: React.ReactNode }> = [
  {
    k: "01",
    title: "What we store",
    body: (
      <>
        An account is an email address, a display name, and the sessions you choose to run. Room
        membership, session length, breach counts and the resulting scores are stored so the room
        can show everyone the same history. That is the whole product surface — there is no ad
        profile behind it.
      </>
    ),
  },
  {
    k: "02",
    title: "What never leaves your device",
    body: (
      <>
        Motion is read locally. The accelerometer and orientation streams that detect a tilt, a lift
        or a screen wake are processed on your own device and discarded. What reaches the server is
        the conclusion —{" "}
        <span className="text-silver">&ldquo;a break happened at 12:04&rdquo;</span> — never the raw
        sensor trace.
      </>
    ),
  },
  {
    k: "03",
    title: "Security records",
    body: (
      <>
        Sign-in attempts are logged with a timestamp, the result, and a coarse network address. This
        exists to rate-limit credential stuffing and to lock an account after repeated failures. It
        is retained briefly and used for nothing else.
      </>
    ),
  },
  {
    k: "04",
    title: "Who else sees it",
    body: (
      <>
        People in your rooms see your display name and your session results — that visibility is the
        point of a shared room. Beyond that, nothing is sold, and nothing is shared with
        advertisers. Infrastructure providers process data strictly to run the service.
      </>
    ),
  },
  {
    k: "05",
    title: "Deleting it",
    body: (
      <>
        Ask and your account and its history are removed. Write to{" "}
        <a
          href="mailto:hello@stackd.raghav.studio"
          className="text-ember underline underline-offset-4"
        >
          hello@stackd.raghav.studio
        </a>{" "}
        and the deletion is permanent — sessions, scores, memberships and security records included.
      </>
    ),
  },
];

function Privacy() {
  return (
    <div className="min-h-screen overflow-x-hidden bg-obsidian text-silver">
      <Nav />

      <main id="main" className="px-6 pb-24 pt-32">
        <header className="mx-auto max-w-3xl">
          <div className="mb-6 font-mono text-[10px] uppercase tracking-[0.3em] text-ember">
            Protocol / Privacy
          </div>
          <h1 className="text-4xl font-extrabold tracking-tighter sm:text-5xl">
            The short version: we keep less than you expect.
          </h1>
          <p className="mt-6 text-base leading-relaxed text-silver-dim">
            Stack&apos;d exists to help people put phones down together. Collecting a detailed
            record of those people would contradict the entire premise, so we don&apos;t.
          </p>
        </header>

        <div className="mx-auto mt-16 max-w-3xl space-y-12">
          {SECTIONS.map((s) => (
            <section key={s.k}>
              <div className="mb-3 flex items-baseline gap-4">
                <span className="font-mono text-[10px] tracking-[0.3em] text-muted-foreground">
                  {s.k}
                </span>
                <h2 className="text-xl font-semibold tracking-tight">{s.title}</h2>
              </div>
              <p className="pl-[3.25rem] text-sm leading-relaxed text-silver-dim">{s.body}</p>
            </section>
          ))}
        </div>

        <div className="mx-auto mt-20 max-w-3xl border-t border-white/5 pt-8">
          <p className="font-mono text-[10px] uppercase tracking-widest text-muted-foreground">
            Questions about any of this →{" "}
            <a href="mailto:hello@stackd.raghav.studio" className="hover:text-ember">
              hello@stackd.raghav.studio
            </a>
          </p>
          <Link
            to="/"
            className="mt-8 inline-block font-mono text-[10px] uppercase tracking-widest text-muted-foreground transition-colors hover:text-ember"
          >
            ← Return to origin
          </Link>
        </div>
      </main>

      <footer className="border-t border-white/5 px-6 py-12">
        <div className="mx-auto max-w-7xl text-center font-mono text-[10px] uppercase tracking-[0.5em] text-muted-foreground">
          Non-digital space is a human right.
        </div>
      </footer>
    </div>
  );
}
