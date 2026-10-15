import { haptic } from "@/lib/haptics";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { supabase } from "@/integrations/supabase/client";
import { Nav } from "@/components/nav";
import { generateRoomCode } from "@/lib/room";
import type { EnforcementMode } from "@/hooks/use-sensors";
import { useServerFn } from "@tanstack/react-start";
import {
  listRoomTemplates,
  createRoomFromTemplate,
  type RoomTemplate,
} from "@/lib/rooms2.functions";
import {
  getLastSessionMinutes,
  setLastSessionMinutes,
  hasCompletedSession,
  isTipDismissed,
  dismissTip,
} from "@/lib/prefs";
import { BadgeHint } from "@/components/ui/badge-hint";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";

export const Route = createFileRoute("/_authenticated/start")({
  head: () => ({
    meta: [
      { title: "New Session — Stack'd" },
      {
        name: "description",
        content:
          "Open a new Stack'd focus room: pick a duration, choose an enforcement mode and invite friends to stack.",
      },
      { property: "og:title", content: "New Session — Stack'd" },
      {
        property: "og:description",
        content: "Open a focus room, set a duration and invite friends to stack their phones.",
      },
      { name: "robots", content: "noindex" },
    ],
  }),
  component: Start,
});

/**
 * The app's suggested length, and the value the slider starts on. Named so the
 * "Recommended" badge and the initial state can never drift apart.
 */
const RECOMMENDED_MINUTES = 30;
/** The only lengths on offer: the slider snaps to these, so it and the chips always agree. */
const STOPS = [15, 25, 30, 45, 60, 90, 120, 180];
/** The few shortcuts shown as chips; the slider reaches every stop. */
const CHIPS = [25, 45, 90];
const snap = (m: number) =>
  STOPS.reduce((best, s) => (Math.abs(s - m) < Math.abs(best - m) ? s : best), STOPS[0]);

function meaningOf(m: number): string {
  switch (m) {
    case 15:
      return "A quick reset";
    case 25:
      return "One classic focus block";
    case 30:
      return "A steady half hour";
    case 45:
      return "A proper deep-work block";
    case 60:
      return "A full hour of deep work";
    case 90:
      return "One full deep-work cycle";
    case 120:
      return "A long haul";
    case 180:
      return "The deep end";
    default:
      return "Your own length";
  }
}

/**
 * The headline choice (Android parity): a big serif number with its meaning,
 * a slider that snaps to the same stops as the chips (a light haptic per
 * stop), and an ember halo that blooms every time it reaches the longest stack.
 */
function DurationHero({
  duration,
  lastMinutes,
  onPick,
}: {
  duration: number;
  lastMinutes: number | null;
  onPick: (m: number) => void;
}) {
  const max = STOPS[STOPS.length - 1];
  const atMax = duration === max;
  const ends = new Date(Date.now() + duration * 60_000).toLocaleTimeString([], {
    hour: "numeric",
    minute: "2-digit",
  });
  const pick = (m: number) => {
    if (m === duration) return;
    haptic(m === max ? "success" : "tap");
    onPick(m);
  };
  // The number rolls the way the choice moved: longer rises in, shorter drops in.
  const prev = useRef(duration);
  const rise = duration >= prev.current;
  useEffect(() => {
    prev.current = duration;
  }, [duration]);
  const label = duration % 60 === 0 && duration >= 60 ? `${duration / 60}` : `${duration}`;
  const unit = duration % 60 === 0 && duration >= 60 ? (duration === 60 ? "hour" : "hours") : "min";
  return (
    <div className="rounded-3xl bg-gradient-to-b from-ember/10 to-white/[0.03] px-6 py-8 text-center">
      <p className="font-mono text-[10px] uppercase tracking-[0.3em] text-muted-foreground">
        Focus for
      </p>
      <div className="relative mx-auto mt-2 grid place-items-center">
        <div
          aria-hidden="true"
          className="absolute h-32 w-60 rounded-full transition-opacity duration-700"
          style={{
            opacity: atMax ? 1 : 0,
            background:
              "radial-gradient(ellipse, rgba(255,196,138,0.45), rgba(240,169,104,0.15) 45%, transparent 70%)",
          }}
        />
        <p className="relative">
          <span
            key={duration}
            className={`inline-block font-serif text-7xl tabular-nums text-silver ${rise ? "animate-num-rise" : "animate-num-drop"}`}
          >
            {label}
          </span>
          <span className="ml-2 text-muted-foreground">{unit}</span>
        </p>
      </div>
      <p className="mt-2 text-sm text-ember">
        {meaningOf(duration)} · ends {ends}
      </p>
      <input
        type="range"
        min={0}
        max={STOPS.length - 1}
        step={1}
        value={STOPS.indexOf(snap(duration))}
        onChange={(e) => pick(STOPS[Number(e.target.value)])}
        aria-label="Focus length"
        aria-valuetext={`${duration} minutes`}
        className={`mt-6 w-full accent-[var(--color-ember)] ${atMax ? "drop-shadow-[0_0_10px_rgba(255,196,138,0.8)]" : ""}`}
      />
      <div role="radiogroup" aria-label="Quick lengths" className="mt-5 flex justify-center gap-2">
        {CHIPS.map((m) => {
          const on = duration === m;
          return (
            <button
              key={m}
              type="button"
              role="radio"
              aria-checked={on}
              onClick={() => pick(m)}
              className={`rounded-full px-5 py-2 text-sm font-semibold transition-[color,background-color,transform] duration-200 ease-[var(--ease-ritual)] select-none active:scale-[0.97] active:duration-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember ${on ? "bg-silver text-obsidian" : "bg-white/[0.06] text-silver hover:bg-white/10"}`}
            >
              {m >= 60 && m % 60 === 0 ? `${m / 60}h` : `${m}m`}
              {lastMinutes === m && !on && <span className="ml-1 text-xs text-ember">last</span>}
            </button>
          );
        })}
      </div>
    </div>
  );
}

function Start() {
  const navigate = useNavigate();
  const [duration, setDuration] = useState(RECOMMENDED_MINUTES);
  // localStorage-backed, so read after mount — during render there is no
  // localStorage on the server and the markup would desync on hydration.
  const [lastMinutes, setLastMinutes] = useState<number | null>(null);
  const [showIntro, setShowIntro] = useState(false);
  const [busy, setBusy] = useState(false);
  const [profile, setProfile] = useState<{ display_name: string } | null>(null);
  const [mode, setMode] = useState<EnforcementMode>("absolute");
  const [templates, setTemplates] = useState<RoomTemplate[]>([]);
  const [tplKey, setTplKey] = useState<string>("");
  const [title, setTitle] = useState("");
  const [goalHours, setGoalHours] = useState<number>(0);

  const fetchTpls = useServerFn(listRoomTemplates);
  const createFromTpl = useServerFn(createRoomFromTemplate);

  useEffect(() => {
    const saved = typeof localStorage !== "undefined" ? localStorage.getItem("stackd:mode") : null;
    if (saved === "gentle" || saved === "absolute") setMode(saved);
    const last = getLastSessionMinutes();
    if (last !== null) {
      setLastMinutes(last);
      setDuration(snap(last));
    }
    // First-run coaching only: someone who has already held a room knows what
    // one is, and a permanently-repeating explainer reads as nagging.
    setShowIntro(!hasCompletedSession() && !isTipDismissed("start-intro"));
    supabase.auth.getUser().then(async ({ data }) => {
      if (!data.user) return;
      const { data: p } = await supabase
        .from("profiles")
        .select("display_name")
        .eq("id", data.user.id)
        .maybeSingle();
      setProfile(p ?? { display_name: data.user.email?.split("@")[0] ?? "Anonymous" });
    });
    // Deliberately swallowed: templates are a shortcut, not a requirement.
    // Losing them degrades to the manual form, so an error banner here would
    // report a problem the user cannot act on and does not have.
    fetchTpls()
      .then(setTemplates)
      .catch(() => {});
  }, [fetchTpls]);

  useEffect(() => {
    if (typeof localStorage !== "undefined") localStorage.setItem("stackd:mode", mode);
  }, [mode]);

  const create = async () => {
    setBusy(true);
    try {
      if (tplKey) {
        const { code } = await createFromTpl({
          data: {
            templateKey: tplKey,
            title: title || undefined,
            collective_goal_seconds: goalHours > 0 ? goalHours * 3600 : null,
          },
        });
        // Written only once the room exists — a duration the user picked but
        // never actually started is not a preference, it's an abandoned draft.
        setLastSessionMinutes(duration);
        navigate({ to: "/room/$code", params: { code } });
        return;
      }

      const { data: u } = await supabase.auth.getUser();
      if (!u.user) throw new Error("Not signed in");

      let room: { id: string; code: string } | null = null;
      let lastError: { message: string; code?: string } | null = null;
      for (let i = 0; i < 5 && !room; i++) {
        const { data: inserted, error } = await supabase
          .from("rooms")
          .insert({
            code: generateRoomCode(),
            host_id: u.user.id,
            target_duration_seconds: duration * 60,
            status: "lobby",
            title: title || null,
            collective_goal_seconds: goalHours > 0 ? goalHours * 3600 : null,
          })
          .select()
          .single();
        if (!error) {
          room = inserted;
          break;
        }
        lastError = error;
        // 23505 = duplicate room code; try again with a fresh one.
        if (error.code !== "23505") throw error;
      }
      if (!room) throw new Error(lastError?.message ?? "room_code_collision");

      await supabase.from("participants").insert({
        room_id: room.id,
        user_id: u.user.id,
        display_name: profile?.display_name ?? "Host",
      });

      setLastSessionMinutes(duration);
      navigate({ to: "/room/$code", params: { code: room.code } });
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Could not create room");
      setBusy(false);
    }
  };

  return (
    <div className="min-h-dvh bg-obsidian text-silver">
      <Nav />
      <main className="app-page max-w-2xl">
        <h1 className="page-title mb-10 font-serif sm:mb-12">Plan a session</h1>

        {showIntro && (
          <div className="mb-10 flex items-start justify-between gap-4 rounded-lg border border-ember/25 bg-ember/[0.06] px-4 py-3">
            <p className="text-xs leading-relaxed text-silver-dim">
              A room is a shared timer — everyone stacks their phones face-down and holds the
              silence until it runs out.
            </p>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => {
                dismissTip("start-intro");
                setShowIntro(false);
              }}
              aria-label="Dismiss the explainer"
              className="shrink-0 font-mono text-[10px] uppercase tracking-widest text-muted-foreground hover:text-silver"
            >
              Got it
            </Button>
          </div>
        )}

        <div className="space-y-10">
          <DurationHero
            duration={duration}
            lastMinutes={lastMinutes}
            onPick={(m) => {
              // Picking a length means "my own length": a template that pinned
              // it gives way to Custom instead of greying the controls out.
              setTplKey("");
              setDuration(m);
            }}
          />

          {templates.length > 0 && (
            <div>
              <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground mb-3">
                Template
              </div>
              <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                <button
                  type="button"
                  onClick={() => setTplKey("")}
                  aria-pressed={tplKey === ""}
                  className={`text-left p-3 rounded-lg border transition-[transform,border-color,background-color] duration-200 ease-[var(--ease-ritual)] active:scale-[0.98] active:duration-100 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember focus-visible:ring-offset-2 focus-visible:ring-offset-obsidian ${tplKey === "" ? "border-ember bg-ember/5" : "border-white/10 hover:border-white/30 hover:bg-white/[0.03]"}`}
                >
                  {/* The tick is what carries "selected" for anyone who can't
                      separate the ember border from the neutral one. */}
                  <div className="font-mono text-[10px] tracking-widest uppercase mb-1">
                    Custom{tplKey === "" && <span aria-hidden="true"> ✓</span>}
                  </div>
                  <p className="text-[11px] text-muted-foreground">Configure manually.</p>
                </button>
                {templates.map((t) => (
                  <button
                    key={t.key}
                    type="button"
                    onClick={() => {
                      setTplKey(t.key);
                      setDuration(Math.round(t.target_duration_seconds / 60));
                    }}
                    aria-pressed={tplKey === t.key}
                    className={`text-left p-3 rounded-lg border transition-[transform,border-color,background-color] duration-200 ease-[var(--ease-ritual)] active:scale-[0.98] active:duration-100 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember focus-visible:ring-offset-2 focus-visible:ring-offset-obsidian ${tplKey === t.key ? "border-ember bg-ember/5" : "border-white/10 hover:border-white/30 hover:bg-white/[0.03]"}`}
                  >
                    <div className="font-mono text-[10px] tracking-widest uppercase mb-1">
                      {t.title}
                      {tplKey === t.key && <span aria-hidden="true"> ✓</span>}
                    </div>
                    <p className="text-[11px] text-muted-foreground line-clamp-2">
                      {t.description}
                    </p>
                    <p className="text-[9px] font-mono uppercase tracking-widest text-ember mt-1">
                      {Math.round(t.target_duration_seconds / 60)}m · {t.visibility}
                    </p>
                  </button>
                ))}
              </div>
            </div>
          )}

          <div>
            <label className="block font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground mb-2">
              Room Title <span className="text-muted-foreground/50">(optional)</span>
            </label>
            <Input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              maxLength={80}
              placeholder="Deep work Monday"
              className="bg-obsidian/40"
            />
          </div>

          <div>
            <label className="block font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground mb-2">
              Collective goal (hours) <span className="text-muted-foreground/50">(optional)</span>
            </label>
            <Input
              type="number"
              min={0}
              max={720}
              value={goalHours}
              onChange={(e) => setGoalHours(Number(e.target.value))}
              className="bg-obsidian/40"
            />
          </div>

          <fieldset>
            <legend className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground mb-4">
              Enforcement Profile
            </legend>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <ModeOption
                active={mode === "gentle"}
                onClick={() => setMode("gentle")}
                title="Gentle"
                desc="Desk workspace. Minor wobbles logged, not penalized. Soft vibration warnings."
              />
              <ModeOption
                active={mode === "absolute"}
                onClick={() => setMode("absolute")}
                title="Absolute"
                desc="Group settings. Any movement, tab switch, or screen wake ends the session."
              />
            </div>
          </fieldset>

          {/* On phones the one decision stays under the thumb while the
              templates and options scroll past (Android pins it the same way). */}
          <div className="sticky bottom-[calc(1rem+env(safe-area-inset-bottom))] z-10 -mx-2 rounded-xl bg-obsidian/90 p-2 backdrop-blur-md sm:static sm:mx-0 sm:bg-transparent sm:p-0 sm:backdrop-blur-none">
            <Button
              onClick={create}
              disabled={busy}
              aria-busy={busy}
              size="lg"
              className="h-14 w-full bg-silver font-mono text-xs font-bold uppercase tracking-widest text-obsidian hover:bg-silver/90"
            >
              {busy
                ? "Creating stack…"
                : tplKey
                  ? "Create Stack"
                  : duration % 60 === 0
                    ? `Create ${duration / 60}-hour Stack`
                    : `Create ${duration}-min Stack`}
            </Button>
          </div>
          {/* Busy already speaks for itself in the label; anything else that
              greys the button out must say why, or it's a dead end. */}
          {busy && (
            <p
              aria-live="polite"
              className="font-mono text-[10px] text-muted-foreground uppercase tracking-widest text-center"
            >
              Opening the room…
            </p>
          )}

          <p className="font-mono text-[10px] text-muted-foreground uppercase tracking-widest text-center">
            A 6-character key will be generated. Share it with the table.
          </p>
        </div>
      </main>
    </div>
  );
}

function ModeOption({
  active,
  onClick,
  title,
  desc,
}: {
  active: boolean;
  onClick: () => void;
  title: string;
  desc: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={`text-left p-5 rounded-xl border transition-[transform,border-color,background-color] duration-200 ease-[var(--ease-ritual)] active:scale-[0.98] active:duration-100 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember focus-visible:ring-offset-2 focus-visible:ring-offset-obsidian ${
        active
          ? "border-silver bg-white/[0.06]"
          : "border-white/10 bg-transparent hover:border-white/30 hover:bg-white/[0.03]"
      }`}
    >
      <div
        className="font-mono text-[10px] tracking-[0.3em] uppercase mb-2"
        style={{ color: active ? "#E2E2E2" : "var(--muted-foreground)" }}
      >
        {title}
        {active && <span aria-hidden="true"> ✓</span>}
      </div>
      <p className="text-xs text-muted-foreground leading-relaxed">{desc}</p>
    </button>
  );
}
