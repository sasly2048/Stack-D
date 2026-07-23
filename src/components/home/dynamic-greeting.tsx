import { useEffect, useState } from "react";
import { supabase } from "@/integrations/supabase/client";

interface Ctx {
  name: string;
  yesterdaySeconds: number;
  streak: number;
  friendsOnline: number;
  challengeProgress: number; // 0..1
}

function timeGlyph(hour: number) {
  if (hour < 5) return { icon: "🌙", label: "Late Night" };
  if (hour < 12) return { icon: "☀️", label: "Good Morning" };
  if (hour < 17) return { icon: "🌤️", label: "Good Afternoon" };
  if (hour < 21) return { icon: "🌇", label: "Good Evening" };
  return { icon: "🌌", label: "Good Night" };
}

export function DynamicGreeting() {
  const [ctx, setCtx] = useState<Ctx | null>(null);
  const [now, setNow] = useState(new Date());

  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), 30_000);
    return () => window.clearInterval(id);
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const { data: userRes } = await supabase.auth.getUser();
      const uid = userRes.user?.id;
      if (!uid) return;
      const since = new Date(Date.now() - 36 * 3600_000).toISOString();
      const [{ data: profile }, { data: hist }, { data: friends }, { data: cp }] = await Promise.all([
        supabase.from("profiles").select("display_name,current_focus_streak").eq("id", uid).maybeSingle(),
        supabase.from("focus_history").select("duration_seconds,created_at").eq("profile_id", uid).gte("created_at", since),
        supabase.from("friendships").select("requester_id,addressee_id,status").eq("status", "accepted"),
        supabase.from("challenge_progress").select("progress,challenge:challenges(target)").eq("user_id", uid).order("updated_at", { ascending: false }).limit(1),
      ]);
      if (cancelled) return;
      const y = new Date(); y.setDate(y.getDate() - 1);
      const yStart = new Date(y.getFullYear(), y.getMonth(), y.getDate()).getTime();
      const yEnd = yStart + 86400_000;
      const yesterdaySeconds = (hist ?? [])
        .filter((h) => { const t = new Date(h.created_at).getTime(); return t >= yStart && t < yEnd; })
        .reduce((sum, h) => sum + (h.duration_seconds ?? 0), 0);
      const friendIds = new Set<string>();
      for (const f of (friends ?? []) as { requester_id: string; addressee_id: string }[]) {
        friendIds.add(f.requester_id === uid ? f.addressee_id : f.requester_id);
      }
      let friendsOnline = 0;
      if (friendIds.size) {
        const since5 = new Date(Date.now() - 5 * 60_000).toISOString();
        const { data: online } = await supabase.from("profiles").select("id").in("id", Array.from(friendIds)).gte("last_active_at", since5);
        friendsOnline = online?.length ?? 0;
      }
      const cpRow = (cp ?? [])[0] as { progress: number; challenge: { target: number } | null } | undefined;
      const challengeProgress = cpRow?.challenge?.target ? Math.min(1, cpRow.progress / cpRow.challenge.target) : 0;
      setCtx({
        name: profile?.display_name ?? "Focused",
        yesterdaySeconds,
        streak: profile?.current_focus_streak ?? 0,
        friendsOnline,
        challengeProgress,
      });
    })();
    return () => { cancelled = true; };
  }, []);

  const { icon, label } = timeGlyph(now.getHours());
  const time = now.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });

  return (
    <div className="relative overflow-hidden rounded-2xl border border-white/10 bg-gradient-to-br from-white/[0.04] to-transparent p-6 md:p-8">
      <div className="absolute inset-0 opacity-30 pointer-events-none" style={{ background: "radial-gradient(600px 200px at 100% 0%, rgba(240,169,104,0.15), transparent)" }} />
      <div className="relative">
        <div className="flex items-center gap-3 font-mono text-[10px] uppercase tracking-[0.3em] text-muted-foreground">
          <span>{icon} {label}</span>
          <span>·</span>
          <span>{time}</span>
        </div>
        <h1 className="mt-3 text-3xl md:text-4xl font-extrabold tracking-tight text-silver">
          {ctx ? `${label.split(" ").slice(-1)[0]}, ${ctx.name}.` : "Welcome."}
        </h1>
        {ctx && (
          <ul className="mt-6 grid gap-2 md:grid-cols-2 text-sm text-muted-foreground">
            {ctx.yesterdaySeconds > 0 && (
              <li>· You studied <span className="text-silver font-semibold">{Math.round(ctx.yesterdaySeconds / 3600 * 10) / 10}h</span> yesterday.</li>
            )}
            {ctx.friendsOnline > 0 && (
              <li>· <span className="text-silver font-semibold">{ctx.friendsOnline}</span> {ctx.friendsOnline === 1 ? "friend is" : "friends are"} already focusing.</li>
            )}
            {ctx.challengeProgress > 0 && ctx.challengeProgress < 1 && (
              <li>· Today's challenge is <span className="text-silver font-semibold">{Math.round(ctx.challengeProgress * 100)}%</span> done.</li>
            )}
            {ctx.streak > 0 && (
              <li>· Current streak: <span className="text-ember font-semibold">🔥 {ctx.streak}</span></li>
            )}
          </ul>
        )}
      </div>
    </div>
  );
}
