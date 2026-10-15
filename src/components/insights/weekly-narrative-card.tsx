import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { getWeeklyStory, discoverPatterns } from "@/lib/ai-narrative.functions";

export function WeeklyNarrativeCard() {
  const story = useServerFn(getWeeklyStory);
  const patterns = useServerFn(discoverPatterns);
  const [text, setText] = useState<string>("");
  const [pats, setPats] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  const run = () => {
    setLoading(true);
    setFailed(false);
    Promise.all([story(), patterns()])
      .then(([s, p]) => {
        setText(s.story);
        setPats(p.patterns);
      })
      .catch(() => setFailed(true))
      .finally(() => setLoading(false));
  };

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(run, []);

  return (
    <section className="border border-ember/30 rounded-md p-6 bg-ember/[0.03]">
      <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">
        Narrative · This Week
      </p>
      {loading ? (
        <p className="mt-4 text-sm text-silver-dim animate-pulse">Composing…</p>
      ) : failed || !text.trim() ? (
        // Never an empty box: say why, and offer the way back.
        <p className="mt-4 text-sm text-silver-dim">
          {failed
            ? "This week's story didn't come through."
            : "A few more stacks this week and the story writes itself."}{" "}
          {failed && (
            <button type="button" onClick={run} className="underline hover:text-ember">
              Retry
            </button>
          )}
        </p>
      ) : (
        <>
          <p className="mt-4 text-xl md:text-2xl font-serif text-silver leading-relaxed">{text}</p>
          {pats.length > 0 && (
            <ul className="mt-6 space-y-2">
              {pats.map((p, i) => (
                <li key={i} className="flex gap-3 text-sm text-silver-dim">
                  <span className="text-ember">·</span>
                  <span>{p}</span>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </section>
  );
}
