import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";
import { z } from "zod";

export interface AwardCard {
  id: string;
  name: string;
  description: string;
  icon: string;
  tier: string;
  xp_reward: number;
}

export interface FriendFinish {
  user_id: string;
  display_name: string | null;
  avatar_url: string | null;
  xp: number;
}

export interface SessionSummary {
  score: number;
  tier: string;
  durationSeconds: number;
  breaches: number;
  xpEarned: number;
  lifetimeXp: number;
  prestige: number;
  level: number;
  levelXpInto: number;
  levelXpSpan: number;
  streak: number;
  achievements: AwardCard[];
  milestones: AwardCard[];
  rankNow: number;
  rankBefore: number;
  personality: string | null;
  friendsFinished: FriendFinish[];
}

/** Level curve: each level costs 1000 XP more than the last (triangular). */
export function levelFromXp(xp: number): { level: number; into: number; span: number } {
  let level = 1;
  let remaining = Math.max(0, xp);
  let span = 1000;
  while (remaining >= span) {
    remaining -= span;
    level += 1;
    span += 500;
  }
  return { level, into: remaining, span };
}

export const getSessionSummary = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ historyId: z.string().uuid() }).parse(d))
  .handler(async ({ data, context }): Promise<SessionSummary> => {
    const { supabase, userId } = context;

    const { data: h } = await supabase
      .from("focus_history")
      .select("score, tier, duration_seconds, breaches_count, xp_earned, created_at")
      .eq("id", data.historyId)
      .eq("profile_id", userId)
      .maybeSingle();

    const xpEarned = (h?.xp_earned as number) ?? 0;

    const { data: prof } = await supabase
      .from("profiles")
      .select("lifetime_xp, prestige_level, current_focus_streak, productivity_dna")
      .eq("id", userId)
      .maybeSingle();

    const lifetimeXp = (prof?.lifetime_xp as number) ?? 0;
    const { level, into, span } = levelFromXp(lifetimeXp);

    // Rank = number of profiles strictly ahead + 1, before and after this session.
    const countAhead = async (xp: number) => {
      const { count } = await supabase
        .from("profiles")
        .select("id", { count: "exact", head: true })
        .gt("lifetime_xp", xp);
      return (count ?? 0) + 1;
    };
    const rankNow = await countAhead(lifetimeXp);
    const rankBefore = await countAhead(Math.max(0, lifetimeXp - xpEarned));

    // Awards unlocked in the last few minutes (this session's window).
    const since = new Date(Date.now() - 5 * 60 * 1000).toISOString();
    const { data: fresh } = await supabase
      .from("user_achievements")
      .select("achievement_id, unlocked_at")
      .eq("user_id", userId)
      .gte("unlocked_at", since);

    const ids = (fresh ?? []).map((r) => r.achievement_id as string);
    let achievements: AwardCard[] = [];
    let milestones: AwardCard[] = [];
    if (ids.length) {
      const { data: defs } = await supabase
        .from("achievements")
        .select("id, name, description, icon, tier, xp_reward")
        .in("id", ids);
      const all = (defs ?? []) as AwardCard[];
      milestones = all.filter((a) => a.tier === "milestone");
      achievements = all.filter((a) => a.tier !== "milestone");
    }

    // Friends who also completed a session today.
    const { data: links } = await supabase
      .from("friendships")
      .select("requester_id, addressee_id, status")
      .eq("status", "accepted");
    const friendIds = (links ?? [])
      .map((l) =>
        (l.requester_id as string) === userId
          ? (l.addressee_id as string)
          : (l.requester_id as string),
      )
      .filter((id) => id !== userId);

    let friendsFinished: FriendFinish[] = [];
    if (friendIds.length) {
      const dayStart = new Date();
      dayStart.setUTCHours(0, 0, 0, 0);
      const { data: acts } = await supabase
        .from("activity_events")
        .select("user_id, payload, created_at")
        .eq("kind", "session_complete")
        .in("user_id", friendIds)
        .gte("created_at", dayStart.toISOString())
        .order("created_at", { ascending: false })
        .limit(50);

      const seen = new Map<string, number>();
      for (const a of acts ?? []) {
        const uid = a.user_id as string;
        const xp = Number((a.payload as { xp?: number } | null)?.xp ?? 0);
        seen.set(uid, (seen.get(uid) ?? 0) + xp);
      }
      if (seen.size) {
        const { data: profs } = await supabase
          .from("profiles")
          .select("id, display_name, avatar_url")
          .in("id", [...seen.keys()]);
        friendsFinished = (profs ?? []).map((p) => ({
          user_id: p.id as string,
          display_name: (p.display_name as string) ?? null,
          avatar_url: (p.avatar_url as string) ?? null,
          xp: seen.get(p.id as string) ?? 0,
        }));
      }
    }

    return {
      score: (h?.score as number) ?? 0,
      tier: (h?.tier as string) ?? "steady",
      durationSeconds: (h?.duration_seconds as number) ?? 0,
      breaches: (h?.breaches_count as number) ?? 0,
      xpEarned,
      lifetimeXp,
      prestige: (prof?.prestige_level as number) ?? 0,
      level,
      levelXpInto: into,
      levelXpSpan: span,
      streak: (prof?.current_focus_streak as number) ?? 0,
      achievements,
      milestones,
      rankNow,
      rankBefore,
      personality: (prof?.productivity_dna as string) ?? null,
      friendsFinished,
    };
  });
