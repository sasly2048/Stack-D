import { useEffect, useRef, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { recommendNextSession, type SessionRecommendation } from "@/lib/ai.functions";
import { track } from "@/lib/observability";

/**
 * Atlas — the ambient companion. A small, dismissible presence card that
 * surfaces context-aware guidance without owning a page of its own. Reuses
 * the existing `recommendNextSession` server fn so no new backend work.
 *
 * Placement: Dashboard, Insights, post-session. Never blocks input.
 */
const DISMISS_KEY = "stackd:atlas:dismissed-until";

export function AtlasWhisper({ context = "dashboard" }: { context?: "dashboard" | "insights" | "post-session" }) {
  const recommend = useServerFn(recommendNextSession);
  const [rec, setRec] = useState<SessionRecommendation | null>(null);
  const [visible, setVisible] = useState(true);
  const shownRef = useRef(false);

  useEffect(() => {
    try {
      const until = Number(localStorage.getItem(DISMISS_KEY) ?? 0);
      if (until > Date.now()) { setVisible(false); return; }
    } catch { /* ignore */ }
    recommend().then(setRec).catch(() => undefined);
  }, []);

  useEffect(() => {
    if (rec && visible && !shownRef.current) {
      shownRef.current = true;
      track("atlas.recommendation_shown", { context, topic: rec.topic, confidence: rec.confidence });
    }
  }, [rec, visible, context]);

  if (!visible || !rec) return null;

  const dismiss = () => {
    try { localStorage.setItem(DISMISS_KEY, String(Date.now() + 1000 * 60 * 60 * 6)); } catch { /* ignore */ }
    track("atlas.recommendation_dismissed", { context, topic: rec.topic });
    setVisible(false);
  };

  const opener =
    context === "post-session" ? "Well held. Here's what I'd try next —"
    : context === "insights"   ? "A pattern I noticed —"
                               : "Atlas here.";

  return (
    <div className="glass rounded-2xl p-5 border border-ember/20 relative">
      <button
        onClick={dismiss}
        aria-label="Dismiss Atlas"
        className="absolute top-3 right-3 text-silver-dim hover:text-silver text-xs font-mono"
      >
        ✕
      </button>
      <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Atlas</p>
      <p className="mt-2 text-sm text-silver-dim">{opener}</p>
      <p className="mt-3 font-serif text-xl text-silver leading-snug">{rec.topic}</p>
      {rec.rationale && <p className="mt-2 text-sm text-silver-dim">{rec.rationale}</p>}
      <p className="mt-3 font-mono text-[10px] uppercase tracking-widest text-ember">
        {rec.durationMinutes} min · confidence {rec.confidence}
      </p>
    </div>
  );
}
