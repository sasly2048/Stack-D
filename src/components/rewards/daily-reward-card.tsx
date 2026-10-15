import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { toast } from "sonner";
import {
  claimDailyReward,
  getRewardStatus,
  type RewardStatus,
} from "@/lib/daily-rewards.functions";
import { feedback } from "@/lib/feedback";
import { haptic } from "@/lib/haptics";
import { notifyXpChanged, useXpSync } from "@/lib/xp-sync";

export function DailyRewardCard() {
  const load = useServerFn(getRewardStatus);
  const claim = useServerFn(claimDailyReward);
  const [status, setStatus] = useState<RewardStatus | null>(null);
  const [claiming, setClaiming] = useState(false);

  const refresh = () =>
    load()
      .then(setStatus)
      .catch(() => undefined);

  useEffect(() => {
    refresh();
  }, []);

  useXpSync(refresh);

  if (!status) return null;

  const onClaim = async () => {
    setClaiming(true);
    try {
      const res = await claim();
      feedback("xp");
      toast.success(`+${res.rewardXp} XP · Day ${res.dayOfStreak}`);
      notifyXpChanged();
    } catch (e: unknown) {
      feedback("error");
      toast.error(e instanceof Error ? e.message : "Claim failed");
    } finally {
      setClaiming(false);
    }
  };

  // Claimed: one quiet line, never a card competing with Start.
  if (status.claimedToday) {
    return (
      <p className="text-sm text-muted-foreground">
        Today's reward claimed · Day {status.nextDayOfStreak} of 7
      </p>
    );
  }

  // Unclaimed: a slim solid-ember pill (Android parity). The glow sweeping
  // across while it claims is the XP landing.
  return (
    <button
      type="button"
      onClick={() => {
        haptic("success");
        void onClaim();
      }}
      disabled={claiming}
      className="group relative flex w-full items-center gap-3 overflow-hidden rounded-full bg-ember px-5 py-3 text-left text-obsidian transition-transform duration-200 ease-[var(--ease-ritual)] active:scale-[0.98]"
    >
      <span
        aria-hidden="true"
        className={`absolute inset-y-0 left-0 bg-[#FFC48A] transition-[width] duration-[650ms] ease-[var(--ease-ritual)] ${claiming ? "w-full" : "w-0"}`}
      />
      <span className="relative" aria-hidden="true">
        🎁
      </span>
      <span className="relative flex-1 font-semibold">
        {claiming ? "Claiming…" : `Claim today's +${status.nextRewardXp} XP`}
      </span>
      <span className="relative text-sm text-obsidian/70">Day {status.nextDayOfStreak} of 7</span>
    </button>
  );
}
