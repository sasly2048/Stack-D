import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect, useState } from "react";
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
const QUICK_DURATIONS = [15, 25, 30, 45, 60, 90];

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
      setDuration(last);
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
    <div className="min-h-screen bg-obsidian text-silver">
      <Nav />
      <main className="pt-32 pb-20 px-6 max-w-2xl mx-auto">
        <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground mb-4">
          NEW / CONFIGURE
        </div>
        <h1 className="text-5xl font-extrabold tracking-tighter mb-12">Set the protocol.</h1>

        {showIntro && (
          <div className="mb-10 flex items-start justify-between gap-4 rounded-lg border border-ember/25 bg-ember/[0.06] px-4 py-3">
            <p className="text-xs leading-relaxed text-silver-dim">
              A room is a shared timer — everyone stacks their phones face-down and holds the
              silence until it runs out.
            </p>
            <button
              type="button"
              onClick={() => {
                dismissTip("start-intro");
                setShowIntro(false);
              }}
              aria-label="Dismiss the explainer"
              className="shrink-0 cursor-pointer rounded font-mono text-[10px] uppercase tracking-widest text-muted-foreground hover:text-silver active:scale-[0.99] transition-all duration-200 ease-[var(--ease-ritual)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember"
            >
              Got it
            </button>
          </div>
        )}

        <div className="space-y-10">
          {templates.length > 0 && (
            <div>
              <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground mb-3">
                Template
              </div>
              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  onClick={() => setTplKey("")}
                  aria-pressed={tplKey === ""}
                  className={`text-left p-3 rounded-lg border transition-all duration-200 ease-[var(--ease-ritual)] active:scale-[0.99] cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember focus-visible:ring-offset-2 focus-visible:ring-offset-obsidian ${tplKey === "" ? "border-ember bg-ember/5" : "border-white/10 hover:border-white/30 hover:bg-white/[0.03]"}`}
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
                    className={`text-left p-3 rounded-lg border transition-all duration-200 ease-[var(--ease-ritual)] active:scale-[0.99] cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember focus-visible:ring-offset-2 focus-visible:ring-offset-obsidian ${tplKey === t.key ? "border-ember bg-ember/5" : "border-white/10 hover:border-white/30 hover:bg-white/[0.03]"}`}
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
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              maxLength={80}
              placeholder="Deep work Monday"
              className="w-full bg-black/40 border border-white/10 rounded px-3 py-2 text-sm"
            />
          </div>

          <div>
            <label className="block font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground mb-2">
              Collective goal (hours) <span className="text-muted-foreground/50">(optional)</span>
            </label>
            <input
              type="number"
              min={0}
              max={720}
              value={goalHours}
              onChange={(e) => setGoalHours(Number(e.target.value))}
              className="w-full bg-black/40 border border-white/10 rounded px-3 py-2 text-sm"
            />
          </div>

          <div>
            <div className="flex justify-between items-end mb-4">
              <span className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground">
                Target Duration
              </span>
              <span className="font-mono text-2xl">
                {duration}
                <span className="text-xs text-muted-foreground ml-2">MIN</span>
              </span>
            </div>
            <input
              type="range"
              min={5}
              max={240}
              step={5}
              value={duration}
              onChange={(e) => setDuration(Number(e.target.value))}
              className="w-full accent-silver"
              disabled={!!tplKey}
            />
            <div className="flex justify-between font-mono text-[10px] text-muted-foreground mt-2 uppercase tracking-widest">
              <span>5m</span>
              <span>30m</span>
              <span>1h</span>
              <span>2h</span>
              <span>4h</span>
            </div>

            {/* Quick picks alongside the slider: a slider alone can't say which
                value is the sensible default or which one you chose last, and
                dragging to an exact minute on a phone is fiddly. */}
            <div
              role="radiogroup"
              aria-label="Quick duration presets"
              className="mt-5 flex flex-wrap gap-2"
            >
              {QUICK_DURATIONS.map((m) => {
                const selected = duration === m;
                return (
                  <button
                    key={m}
                    type="button"
                    role="radio"
                    aria-checked={selected}
                    disabled={!!tplKey}
                    onClick={() => setDuration(m)}
                    className={`inline-flex items-center gap-2 rounded-lg border px-3 py-2 font-mono text-[10px] uppercase tracking-widest cursor-pointer transition-all duration-200 ease-[var(--ease-ritual)] active:scale-[0.99] disabled:opacity-50 disabled:cursor-not-allowed disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember focus-visible:ring-offset-2 focus-visible:ring-offset-obsidian ${
                      selected
                        ? "border-silver bg-white/[0.08] text-silver"
                        : "border-white/10 text-muted-foreground hover:border-white/30 hover:text-silver hover:bg-white/[0.03]"
                    }`}
                  >
                    <span>
                      {selected && <span aria-hidden="true">✓ </span>}
                      {m}m
                    </span>
                    {m === RECOMMENDED_MINUTES && (
                      <BadgeHint tone="accent" title="The length most sessions settle on">
                        Recommended
                      </BadgeHint>
                    )}
                    {lastMinutes === m && m !== RECOMMENDED_MINUTES && (
                      <BadgeHint tone="neutral" title="The length you started last time">
                        Last used
                      </BadgeHint>
                    )}
                  </button>
                );
              })}
            </div>
            {/* A remembered value that isn't one of the presets would otherwise
                be silently restored with no explanation for the odd number. */}
            {lastMinutes !== null && !QUICK_DURATIONS.includes(lastMinutes) && (
              <p className="mt-3 font-mono text-[10px] uppercase tracking-widest text-muted-foreground">
                {lastMinutes}m <BadgeHint tone="neutral">Last used</BadgeHint>
              </p>
            )}
          </div>

          <fieldset>
            <legend className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground mb-4">
              Enforcement Profile
            </legend>
            <div className="grid grid-cols-2 gap-3">
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

          <button
            onClick={create}
            disabled={busy}
            aria-busy={busy}
            className="w-full bg-silver text-obsidian py-5 rounded-lg font-mono text-xs uppercase tracking-widest font-bold hover:invert transition-all duration-200 ease-[var(--ease-ritual)] active:scale-[0.99] cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember focus-visible:ring-offset-2 focus-visible:ring-offset-obsidian"
          >
            {busy ? "Forging key..." : "Forge Room Key"}
          </button>
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
      className={`text-left p-5 rounded-xl border transition-all duration-200 ease-[var(--ease-ritual)] active:scale-[0.99] cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember focus-visible:ring-offset-2 focus-visible:ring-offset-obsidian ${
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
