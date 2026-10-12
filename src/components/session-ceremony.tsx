import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { haptic } from "@/lib/haptics";
import { HoldButton } from "@/components/hold-button";
import { feedback } from "@/lib/feedback";
import { copy } from "@/lib/copy";
import { getSessionSummary, type SessionSummary } from "@/lib/session-summary.functions";
import { updateSessionMeta } from "@/lib/challenges.functions";
import { useEntitlement } from "@/lib/use-entitlement";
import { UpgradeDialog } from "@/components/premium/upgrade-dialog";

/** True while the ceremony is on screen; realtime toasts hold their own results. */
let ceremonyOpen = false;
export const isCeremonyOpen = () => ceremonyOpen;

/**
 * The post-session Premium offer is rationed so it never becomes the reason
 * someone quits: only after a clean stack, never in the first three, at most
 * once a week, and two "Not now"s buy thirty days of quiet. Android parity.
 */
function premiumAskAfterCleanStack(): boolean {
  try {
    const now = Date.now();
    const clean = Number(localStorage.getItem("stackd.clean_seen") ?? 0) + 1;
    localStorage.setItem("stackd.clean_seen", String(clean));
    const quiet = Number(localStorage.getItem("stackd.ask_quiet_until") ?? 0);
    const shown = Number(localStorage.getItem("stackd.ask_shown_at") ?? 0);
    const show = clean > 3 && now >= quiet && now - shown >= 7 * 86_400_000;
    if (show) localStorage.setItem("stackd.ask_shown_at", String(now));
    return show;
  } catch {
    return false;
  }
}

function dismissPremiumAsk() {
  try {
    const n = Number(localStorage.getItem("stackd.ask_dismisses") ?? 0) + 1;
    if (n >= 2) {
      localStorage.setItem("stackd.ask_quiet_until", String(Date.now() + 30 * 86_400_000));
      localStorage.setItem("stackd.ask_dismisses", "0");
    } else {
      localStorage.setItem("stackd.ask_dismisses", String(n));
    }
  } catch {
    /* storage unavailable: the ask simply stays rationed by its other rules */
  }
}

export interface CeremonyDetail {
  durationSeconds: number;
  xpEarned: number;
  score?: number;
  tier?: string;
  achievements?: string[];
  streak?: number;
  /** When present, the ceremony fetches the full multi-beat summary. */
  historyId?: string;
  /** What the user said in the lobby they were stacking for; asked back here. */
  intention?: string;
  /** No breaches: gates the rationed Premium offer. */
  clean?: boolean;
}

type BeatKey =
  | "score"
  | "xp"
  | "level"
  | "achievements"
  | "milestones"
  | "rank"
  | "friends"
  | "continue";

function useReducedMotion() {
  const [reduce, setReduce] = useState(false);
  useEffect(() => {
    setReduce(window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false);
  }, []);
  return reduce;
}

/** Eased count-up. Never snaps straight to the final value. */
function useCountUp(target: number, active: boolean, duration = 1800) {
  const [value, setValue] = useState(0);
  useEffect(() => {
    if (!active) return;
    let raf = 0;
    const start = performance.now();
    const tick = (t: number) => {
      const p = Math.min(1, (t - start) / duration);
      const eased = 1 - Math.pow(1 - p, 4);
      setValue(Math.round(target * eased));
      if (p < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [target, active, duration]);
  return value;
}

/**
 * Cinematic post-session moment. Mount once in __root; dispatch from anywhere:
 *
 *   window.dispatchEvent(new CustomEvent("stackd:ceremony", { detail: {...} }))
 *
 * Beat order: Focus Score → XP → Level/Prestige → Achievements → Milestones →
 * Rank change → Friends finished → Continue.
 */
export function SessionCeremony() {
  const [detail, setDetail] = useState<CeremonyDetail | null>(null);
  const [summary, setSummary] = useState<SessionSummary | null>(null);
  const [beat, setBeat] = useState(0);
  const reduce = useReducedMotion();
  const fetchSummary = useServerFn(getSessionSummary);
  const closeRef = useRef<HTMLButtonElement | null>(null);
  const saveMeta = useServerFn(updateSessionMeta);
  const ent = useEntitlement();
  // The user performs the reveal: hold to lift the stack (reduced motion skips).
  const [lifted, setLifted] = useState(false);
  const [mood, setMood] = useState<string | null>(null);
  const [landed, setLanded] = useState<string | null>(null);
  const [ask, setAsk] = useState(false);
  const [shared, setShared] = useState(false);
  const [upgradeOpen, setUpgradeOpen] = useState(false);

  const close = useCallback(() => {
    ceremonyOpen = false;
    setDetail(null);
    setSummary(null);
    setBeat(0);
    setLifted(false);
    setMood(null);
    setLanded(null);
    setAsk(false);
    setShared(false);
  }, []);

  // One notes+tags write for both answers, so neither overwrites the other.
  const persist = (nextMood: string | null, nextLanded: string | null) => {
    if (!detail?.historyId) return;
    const tags = [nextMood, nextLanded ? `intention: ${nextLanded.toLowerCase()}` : null].filter(
      Boolean,
    ) as string[];
    void saveMeta({
      data: {
        historyId: detail.historyId,
        notes: detail.intention ? `Stacking for: ${detail.intention}` : "",
        tags,
      },
    }).catch(() => {});
  };

  useEffect(() => {
    const handler = (e: Event) => {
      const d = (e as CustomEvent<CeremonyDetail>).detail;
      if (!d) return;
      ceremonyOpen = true;
      setDetail(d);
      setSummary(null);
      setBeat(0);
      setLifted(false);
    };
    window.addEventListener("stackd:ceremony", handler);
    return () => window.removeEventListener("stackd:ceremony", handler);
  }, []);

  // Pull the rich summary when a history id is available.
  useEffect(() => {
    if (!detail?.historyId) return;
    let alive = true;
    fetchSummary({ data: { historyId: detail.historyId } })
      .then((s) => {
        if (alive) setSummary(s);
      })
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, [detail?.historyId, fetchSummary]);

  const beats = useMemo<BeatKey[]>(() => {
    const list: BeatKey[] = ["score", "xp"];
    if (summary) {
      list.push("level");
      if (summary.achievements.length) list.push("achievements");
      if (summary.milestones.length) list.push("milestones");
      if (summary.rankNow !== summary.rankBefore) list.push("rank");
      if (summary.friendsFinished.length) list.push("friends");
    }
    list.push("continue");
    return list;
  }, [summary]);

  useEffect(() => {
    if (reduce && detail) setLifted(true);
  }, [reduce, detail]);

  // The peak lands when the stack lifts: sound + haptic, and decide (once)
  // whether this clean stack earns the rationed Premium offer.
  useEffect(() => {
    if (!detail || !lifted) return;
    feedback("success");
    if (detail.clean && ent && !ent.isPremium && premiumAskAfterCleanStack()) setAsk(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lifted, detail]);

  // Advance beats on a timer (reduced motion shows everything at once).
  useEffect(() => {
    if (!detail || reduce || !lifted) return;
    if (beat >= beats.length - 1) return;
    const delay = beats[beat] === "xp" ? 2400 : 1500;
    const t = window.setTimeout(() => {
      setBeat((b) => Math.min(b + 1, beats.length - 1));
      haptic("tap");
    }, delay);
    return () => window.clearTimeout(t);
  }, [detail, beat, beats, reduce, lifted]);

  useEffect(() => {
    if (!detail) return;
    const esc = (e: KeyboardEvent) => {
      if (e.key === "Escape") close();
    };
    window.addEventListener("keydown", esc);
    return () => window.removeEventListener("keydown", esc);
  }, [detail, close]);

  useEffect(() => {
    if (detail && beats[beat] === "continue") closeRef.current?.focus();
  }, [detail, beat, beats]);

  const xpTarget = summary?.xpEarned ?? detail?.xpEarned ?? 0;
  const xpActive = !!detail && lifted && (reduce || beat >= beats.indexOf("xp"));
  const xpDisplay = useCountUp(xpTarget, xpActive, reduce ? 300 : 1800);

  if (!detail) return null;

  if (!lifted) {
    return (
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Session complete"
        className="fixed inset-0 z-[100] flex items-center justify-center bg-obsidian/95 backdrop-blur-xl animate-fade-in"
      >
        <div className="w-full max-w-xs px-6 text-center">
          <div aria-hidden="true" className="relative mx-auto mb-10 h-44 w-28">
            {[3, 2, 1, 0].map((i) => (
              <div
                key={i}
                className="absolute inset-0 rounded-[22px] border border-ember/40 bg-gradient-to-br from-[#1D1915] to-[#0E0C0A] shadow-2xl"
                style={{ transform: `translateY(${-i * 6}px) rotate(${(i - 1.5) * 3}deg)` }}
              />
            ))}
          </div>
          <p className="font-serif text-2xl text-silver">Hold to lift the stack</p>
          <p className="mt-2 mb-8 text-sm text-muted-foreground">
            Everyone picks their phone back up.
          </p>
          <HoldButton label="Hold" onConfirm={() => setLifted(true)} />
        </div>
      </div>
    );
  }

  const visible = (k: BeatKey) => reduce || beat >= beats.indexOf(k);
  const mins = Math.max(1, Math.round(detail.durationSeconds / 60));
  const score = summary?.score ?? detail.score ?? 0;
  const tier = summary?.tier ?? detail.tier;
  const rankDelta = summary ? summary.rankBefore - summary.rankNow : 0;
  const levelPct = summary
    ? Math.min(100, Math.round((summary.levelXpInto / Math.max(1, summary.levelXpSpan)) * 100))
    : 0;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-live="polite"
      aria-label="Session complete"
      className="fixed inset-0 z-[100] flex items-center justify-center overflow-y-auto bg-obsidian/90 backdrop-blur-xl animate-fade-in"
    >
      <div
        className="absolute inset-0 pointer-events-none"
        style={{
          background:
            "radial-gradient(ellipse at center, rgba(240,169,104,0.22) 0%, rgba(240,169,104,0.07) 32%, transparent 66%)",
          animation: "ceremony-glow 1.8s ease-out",
        }}
      />

      <div className="relative w-full max-w-md px-6 py-16 text-center">
        <p className="font-mono text-[10px] tracking-[0.35em] uppercase text-ember">Held</p>
        <p className="mt-3 font-serif text-3xl text-silver leading-none">
          {copy.session.completedBody(mins)}
        </p>

        {/* 1. Focus Score */}
        <div className="mt-10 animate-scale-in">
          <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">
            Focus Score
          </p>
          <p className="mt-2 font-serif text-7xl text-silver tabular-nums leading-none">{score}</p>
          {tier && (
            <p className="mt-2 font-mono text-[10px] tracking-[0.3em] uppercase text-ember">
              {tier}
            </p>
          )}
        </div>

        {/* 2. XP */}
        <div
          className={`mt-8 transition-all duration-500 ${visible("xp") ? "opacity-100 translate-y-0" : "opacity-0 translate-y-3"}`}
        >
          <div className="flex items-baseline justify-center gap-2">
            <span className="font-serif text-6xl text-ember tabular-nums">
              +{xpDisplay.toLocaleString()}
            </span>
            <span className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">XP</span>
          </div>
        </div>

        {/* 3. Level / Prestige */}
        {summary && (
          <div
            className={`mt-8 transition-all duration-500 ${visible("level") ? "opacity-100 translate-y-0" : "opacity-0 translate-y-3"}`}
          >
            <div className="flex items-center justify-between font-mono text-[10px] tracking-[0.25em] uppercase text-silver-dim">
              <span>
                {summary.prestige > 0 && <span className="text-ember">P{summary.prestige} · </span>}
                Level {summary.level}
              </span>
              <span>
                {summary.levelXpInto.toLocaleString()} / {summary.levelXpSpan.toLocaleString()}
              </span>
            </div>
            <div className="mt-2 h-1.5 w-full rounded-full bg-white/5 overflow-hidden">
              <div
                className="h-full rounded-full bg-ember transition-[width] duration-[1200ms] ease-out"
                style={{ width: `${visible("level") ? levelPct : 0}%` }}
              />
            </div>
          </div>
        )}

        {/* 4. Achievements */}
        {summary && summary.achievements.length > 0 && (
          <div
            className={`mt-8 space-y-1 transition-all duration-500 ${visible("achievements") ? "opacity-100" : "opacity-0"}`}
          >
            {summary.achievements.map((a) => (
              <p
                key={a.id}
                className="font-mono text-xs tracking-widest uppercase text-ember"
                title={a.description}
              >
                ◆ {a.name}
              </p>
            ))}
          </div>
        )}

        {/* 4b. Lifetime milestones */}
        {summary && summary.milestones.length > 0 && (
          <div
            className={`mt-6 space-y-2 transition-all duration-500 ${visible("milestones") ? "opacity-100" : "opacity-0"}`}
          >
            {summary.milestones.map((m) => (
              <div
                key={m.id}
                className="rounded-xl border border-ember/40 bg-ember/5 px-4 py-3 text-left"
              >
                <p className="font-mono text-[9px] tracking-[0.3em] uppercase text-ember">
                  Lifetime Milestone
                </p>
                <p className="mt-1 font-serif text-xl text-silver">{m.name}</p>
                <p className="text-xs text-silver-dim">{m.description}</p>
              </div>
            ))}
          </div>
        )}

        {/* 5. Rank change */}
        {summary && rankDelta !== 0 && (
          <p
            className={`mt-8 font-mono text-[11px] tracking-[0.3em] uppercase transition-all duration-500 ${visible("rank") ? "opacity-100" : "opacity-0"} ${rankDelta > 0 ? "text-ember" : "text-silver-dim"}`}
          >
            {rankDelta > 0 ? "▲" : "▼"} {Math.abs(rankDelta)} · Rank #{summary.rankNow}
          </p>
        )}

        {/* 6. Friends finished */}
        {summary && summary.friendsFinished.length > 0 && (
          <div
            className={`mt-8 transition-all duration-500 ${visible("friends") ? "opacity-100" : "opacity-0"}`}
          >
            <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">
              Also finished today
            </p>
            <p className="mt-2 text-sm text-silver">
              {summary.friendsFinished
                .slice(0, 4)
                .map((f) => f.display_name ?? "Anon")
                .join(" · ")}
            </p>
          </div>
        )}

        {/* Your note, asked back */}
        {detail.intention && visible("continue") && (
          <div className="mt-10">
            <p className="text-sm font-semibold text-silver">You said: {detail.intention}</p>
            <div className="mt-3 flex justify-center gap-2">
              {["Done", "Partly", "Not yet"].map((o) => (
                <button
                  key={o}
                  type="button"
                  onClick={() => {
                    setLanded(o);
                    persist(mood, o);
                  }}
                  className={`rounded-full border px-4 py-2 text-sm transition-colors ${landed === o ? "border-ember bg-ember/15 text-ember" : "border-white/10 text-silver hover:border-white/30"}`}
                >
                  {o}
                </button>
              ))}
            </div>
          </div>
        )}

        {visible("continue") && (
          <div className="mt-8">
            <p className="text-sm font-semibold text-silver">How did it feel?</p>
            <div className="mt-3 flex justify-center gap-2">
              {["Deep flow", "Distracted", "Low energy"].map((o) => (
                <button
                  key={o}
                  type="button"
                  onClick={() => {
                    setMood(o);
                    persist(o, landed);
                  }}
                  className={`rounded-full border px-4 py-2 text-sm transition-colors ${mood === o ? "border-ember bg-ember/15 text-ember" : "border-white/10 text-silver hover:border-white/30"}`}
                >
                  {o}
                </button>
              ))}
            </div>
            {/* The ending points somewhere instead of just stopping. */}
            <p className="mt-8 text-sm text-muted-foreground">
              {(summary?.streak ?? detail.streak ?? 0) > 0
                ? `Come back tomorrow to make it ${(summary?.streak ?? detail.streak ?? 0) + 1} days.`
                : "Stack again tomorrow to start a streak."}
            </p>
            {ask && (
              <p className="mt-4 flex justify-center gap-4 font-mono text-[11px] uppercase tracking-widest">
                <button
                  type="button"
                  onClick={() => setUpgradeOpen(true)}
                  className="text-ember hover:underline"
                >
                  See what Premium adds
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setAsk(false);
                    dismissPremiumAsk();
                  }}
                  className="text-muted-foreground hover:text-silver"
                >
                  Not now
                </button>
              </p>
            )}
            <button
              type="button"
              onClick={async () => {
                const text = `The stack held. ${mins} min · ${score} · Stack'd`;
                try {
                  if (navigator.share)
                    await navigator.share({ text, url: "https://stackd.raghav.studio" });
                  else await navigator.clipboard.writeText(`${text} https://stackd.raghav.studio`);
                  setShared(true);
                } catch {
                  /* share sheet dismissed */
                }
              }}
              className="mt-6 font-mono text-[11px] uppercase tracking-widest text-ember hover:underline"
            >
              {shared ? "Shared" : "Share this stack"}
            </button>
          </div>
        )}

        {/* 7. Continue */}
        <button
          ref={closeRef}
          type="button"
          onClick={close}
          className={`mt-10 w-full rounded-full border border-white/15 py-3 font-mono text-[11px] tracking-[0.3em] uppercase text-silver transition-all duration-500 hover:border-ember hover:text-ember ${visible("continue") ? "opacity-100" : "opacity-40"}`}
        >
          Continue
        </button>
      </div>
      <UpgradeDialog open={upgradeOpen} onOpenChange={setUpgradeOpen} />
    </div>
  );
}

/** Convenience for callers. */
export function celebrateSession(detail: CeremonyDetail) {
  window.dispatchEvent(new CustomEvent("stackd:ceremony", { detail }));
}
