import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { supabase } from "@/integrations/supabase/client";
import { Nav } from "@/components/nav";
import { generateRoomCode } from "@/lib/room";
import type { EnforcementMode } from "@/hooks/use-sensors";
import { useServerFn } from "@tanstack/react-start";
import { listRoomTemplates, createRoomFromTemplate, type RoomTemplate } from "@/lib/rooms2.functions";

export const Route = createFileRoute("/_authenticated/start")({
  head: () => ({ meta: [{ title: "New Session — Stack'd" }] }),
  component: Start,
});

function Start() {
  const navigate = useNavigate();
  const [duration, setDuration] = useState(30);
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
    supabase.auth.getUser().then(async ({ data }) => {
      if (!data.user) return;
      const { data: p } = await supabase.from("profiles").select("display_name").eq("id", data.user.id).maybeSingle();
      setProfile(p ?? { display_name: data.user.email?.split("@")[0] ?? "Anonymous" });
    });
    fetchTpls().then(setTemplates).catch(() => {});
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
        navigate({ to: "/room/$code", params: { code } });
        return;
      }

      const { data: u } = await supabase.auth.getUser();
      if (!u.user) throw new Error("Not signed in");

      let code = generateRoomCode();
      for (let i = 0; i < 5; i++) {
        const { data: exists } = await (supabase.rpc as unknown as (
          fn: string, args: Record<string, unknown>,
        ) => PromiseLike<{ data: boolean | null }>)(
          "room_code_exists", { _code: code },
        );
        if (!exists) break;
        code = generateRoomCode();
      }

      const { data: room, error } = await supabase
        .from("rooms")
        .insert({
          code,
          host_id: u.user.id,
          target_duration_seconds: duration * 60,
          status: "lobby",
          title: title || null,
          collective_goal_seconds: goalHours > 0 ? goalHours * 3600 : null,
        })
        .select().single();
      if (error) throw error;

      await supabase.from("participants").insert({
        room_id: room.id,
        user_id: u.user.id,
        display_name: profile?.display_name ?? "Host",
      });

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
                  className={`text-left p-3 rounded-lg border transition-all ${tplKey === "" ? "border-ember bg-ember/5" : "border-white/10 hover:border-white/30"}`}
                >
                  <div className="font-mono text-[10px] tracking-widest uppercase mb-1">Custom</div>
                  <p className="text-[11px] text-muted-foreground">Configure manually.</p>
                </button>
                {templates.map((t) => (
                  <button
                    key={t.key}
                    type="button"
                    onClick={() => { setTplKey(t.key); setDuration(Math.round(t.target_duration_seconds / 60)); }}
                    aria-pressed={tplKey === t.key}
                    className={`text-left p-3 rounded-lg border transition-all ${tplKey === t.key ? "border-ember bg-ember/5" : "border-white/10 hover:border-white/30"}`}
                  >
                    <div className="font-mono text-[10px] tracking-widest uppercase mb-1">{t.title}</div>
                    <p className="text-[11px] text-muted-foreground line-clamp-2">{t.description}</p>
                    <p className="text-[9px] font-mono uppercase tracking-widest text-ember mt-1">{Math.round(t.target_duration_seconds / 60)}m · {t.visibility}</p>
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
              type="number" min={0} max={720}
              value={goalHours}
              onChange={(e) => setGoalHours(Number(e.target.value))}
              className="w-full bg-black/40 border border-white/10 rounded px-3 py-2 text-sm"
            />
          </div>

          <div>
            <div className="flex justify-between items-end mb-4">
              <span className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground">Target Duration</span>
              <span className="font-mono text-2xl">{duration}<span className="text-xs text-muted-foreground ml-2">MIN</span></span>
            </div>
            <input
              type="range" min={5} max={240} step={5}
              value={duration} onChange={(e) => setDuration(Number(e.target.value))}
              className="w-full accent-silver"
              disabled={!!tplKey}
            />
            <div className="flex justify-between font-mono text-[10px] text-muted-foreground mt-2 uppercase tracking-widest">
              <span>5m</span><span>30m</span><span>1h</span><span>2h</span><span>4h</span>
            </div>
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
            onClick={create} disabled={busy}
            className="w-full bg-silver text-obsidian py-5 rounded-lg font-mono text-xs uppercase tracking-widest font-bold hover:invert transition-all disabled:opacity-50"
          >
            {busy ? "Forging key..." : "Forge Room Key"}
          </button>

          <p className="font-mono text-[10px] text-muted-foreground uppercase tracking-widest text-center">
            A 6-character key will be generated. Share it with the table.
          </p>
        </div>
      </main>
    </div>
  );
}

function ModeOption({ active, onClick, title, desc }: { active: boolean; onClick: () => void; title: string; desc: string }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={`text-left p-5 rounded-xl border transition-all ${
        active ? "border-silver bg-white/[0.06]" : "border-white/10 bg-transparent hover:border-white/30"
      }`}
    >
      <div className="font-mono text-[10px] tracking-[0.3em] uppercase mb-2" style={{ color: active ? "#E2E2E2" : "var(--muted-foreground)" }}>
        {title}
      </div>
      <p className="text-xs text-muted-foreground leading-relaxed">{desc}</p>
    </button>
  );
}
