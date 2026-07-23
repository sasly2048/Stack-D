import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { toast } from "sonner";
import { claimDailyReward, getRewardStatus, type RewardStatus } from "@/lib/daily-rewards.functions";
import { haptic } from "@/lib/haptics";

export function DailyRewardCard() {
  const load = useServerFn(getRewardStatus);
  const claim = useServerFn(claimDailyReward);
  const [status, setStatus] = useState<RewardStatus | null>(null);
  const [claiming, setClaiming] = useState(false);

  useEffect(() => {
    load().then(setStatus).catch(() => undefined);
  }, []);

  if (!status) return null;

  const onClaim = async () => {
    setClaiming(true);
    try {
      const res = await claim();
      haptic("success");
      toast.success(`+${res.rewardXp} XP · Day ${res.dayOfStreak}`);
      const fresh = await load();
      setStatus(fresh);
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "Claim failed");
    } finally {
      setClaiming(false);
    }
  };

  return (
    <div className="border border-ember/30 rounded-md p-5 bg-ember/[0.04]">
      <div className="flex items-center justify-between">
        <div>
          <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-ember">Daily</p>
          <p className="mt-2 text-xl font-serif text-silver">
            {status.claimedToday ? "Claimed" : `+${status.nextRewardXp} XP waiting`}
          </p>
          <p className="mt-1 text-xs text-silver-dim">
            Streak · <span className="text-silver">{status.streak}</span> days · Day{" "}
            {status.nextDayOfStreak}/7 in cycle
          </p>
        </div>
        <button
          onClick={onClaim}
          disabled={status.claimedToday || claiming}
          className="px-4 py-2 rounded-full border border-ember/50 text-ember font-mono text-xs uppercase tracking-widest disabled:opacity-40 hover:bg-ember/10 transition"
        >
          {status.claimedToday ? "Come back tomorrow" : claiming ? "Claiming…" : "Claim"}
        </button>
      </div>
      <div className="mt-4 grid grid-cols-7 gap-1">
        {[10, 20, 40, 60, 80, 100, 200].map((xp, i) => {
          const active = i + 1 === status.nextDayOfStreak && !status.claimedToday;
          return (
            <div
              key={i}
              className={`h-8 rounded flex items-center justify-center text-[9px] font-mono ${
                active ? "bg-ember/30 text-ember border border-ember" : "bg-white/[0.03] text-silver-dim"
              }`}
            >
              {xp}
            </div>
          );
        })}
      </div>
    </div>
  );
}
