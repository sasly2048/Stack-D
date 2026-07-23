import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { getWeeklyStory, discoverPatterns } from "@/lib/ai-narrative.functions";

export function WeeklyNarrativeCard() {
  const story = useServerFn(getWeeklyStory);
  const patterns = useServerFn(discoverPatterns);
  const [text, setText] = useState<string>("");
  const [pats, setPats] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([story(), patterns()])
      .then(([s, p]) => {
        setText(s.story);
        setPats(p.patterns);
      })
      .catch(() => undefined)
      .finally(() => setLoading(false));
  }, []);

  return (
    <section className="border border-ember/30 rounded-md p-6 bg-ember/[0.03]">
      <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Narrative · This Week</p>
      {loading ? (
        <p className="mt-4 text-sm text-silver-dim animate-pulse">Composing…</p>
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
