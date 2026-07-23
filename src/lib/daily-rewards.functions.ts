import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";

export interface RewardStatus {
  streak: number;
  totalClaims: number;
  claimedToday: boolean;
  nextRewardXp: number;
  nextDayOfStreak: number;
}

const REWARDS = [10, 20, 40, 60, 80, 100, 200];

export const getRewardStatus = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<RewardStatus> => {
    const { data } = await context.supabase
      .from("login_streaks")
      .select("streak,last_claim_date,total_claims")
      .eq("user_id", context.userId)
      .maybeSingle();
    const today = new Date().toISOString().slice(0, 10);
    const streak = data?.streak ?? 0;
    const claimedToday = data?.last_claim_date === today;
    const yesterday = new Date(Date.now() - 86400_000).toISOString().slice(0, 10);
    const willChain = data?.last_claim_date === yesterday || (!data && !claimedToday);
    const nextStreak = claimedToday ? streak : willChain ? streak + 1 : 1;
    const idx = (nextStreak - 1) % 7;
    return {
      streak,
      totalClaims: data?.total_claims ?? 0,
      claimedToday,
      nextRewardXp: REWARDS[idx],
      nextDayOfStreak: idx + 1,
    };
  });

export const claimDailyReward = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<{ rewardXp: number; newStreak: number; dayOfStreak: number }> => {
    const { data, error } = await context.supabase.rpc("claim_daily_reward");
    if (error) throw new Error(error.message);
    const row = Array.isArray(data) ? data[0] : data;
    return { rewardXp: row.reward_xp, newStreak: row.new_streak, dayOfStreak: row.day_of_streak };
  });
