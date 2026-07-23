import { useEffect, useState, type ReactNode } from "react";
import { HoverCard, HoverCardTrigger, HoverCardContent } from "@/components/ui/hover-card";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Link } from "@tanstack/react-router";
import { useServerFn } from "@tanstack/react-start";
import { getProfileCard, type ProfileCardData } from "@/lib/profile-card.functions";
import { sendFriendRequest, respondFriendRequest } from "@/lib/friends.functions";
import { toast } from "sonner";

function fmtMinutes(sec: number): string {
  const m = Math.floor(sec / 60);
  if (m < 60) return `${m}m`;
  const h = Math.floor(m / 60);
  return `${h}h ${m % 60}m`;
}

export function UserHoverCard({
  profileId,
  children,
  side = "top",
}: {
  profileId: string;
  children: ReactNode;
  side?: "top" | "right" | "bottom" | "left";
}) {
  const [open, setOpen] = useState(false);
  const [data, setData] = useState<ProfileCardData | null>(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const fetchCard = useServerFn(getProfileCard);
  const addFriend = useServerFn(sendFriendRequest);
  const respondFriend = useServerFn(respondFriendRequest);

  useEffect(() => {
    if (!open || data || loading) return;
    setLoading(true);
    fetchCard({ data: { profileId } })
      .then(setData)
      .catch(() => toast.error("Couldn't load profile"))
      .finally(() => setLoading(false));
  }, [open, data, loading, profileId, fetchCard]);

  async function handleAdd() {
    setBusy(true);
    try {
      await addFriend({ data: { addresseeId: profileId } });
      toast.success("Friend request sent");
      setData((prev) => prev && { ...prev, friendship: { id: "pending", status: "pending", direction: "outgoing" } });
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Failed");
    } finally {
      setBusy(false);
    }
  }
  async function handleAccept() {
    if (!data?.friendship) return;
    setBusy(true);
    try {
      await respondFriend({ data: { id: data.friendship.id, accept: true } });
      toast.success("Now friends");
      setData({ ...data, friendship: { ...data.friendship, status: "accepted", direction: "friend" } });
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <HoverCard openDelay={200} closeDelay={80} onOpenChange={setOpen}>
      <HoverCardTrigger asChild>{children}</HoverCardTrigger>
      <HoverCardContent side={side} className="w-80 bg-background/95 backdrop-blur border-white/10">
        {loading || !data ? (
          <div className="animate-pulse space-y-3">
            <div className="flex gap-3">
              <div className="size-12 rounded-full bg-white/10" />
              <div className="flex-1 space-y-2">
                <div className="h-3 bg-white/10 rounded w-2/3" />
                <div className="h-2 bg-white/5 rounded w-1/2" />
              </div>
            </div>
            <div className="h-16 bg-white/5 rounded" />
          </div>
        ) : (
          <div className="space-y-3">
            <div className="flex items-start gap-3">
              <Avatar className="size-12 border border-white/10">
                <AvatarImage src={data.avatar_url ?? undefined} />
                <AvatarFallback className="bg-white/5 text-silver">
                  {(data.display_name ?? "A").slice(0, 2).toUpperCase()}
                </AvatarFallback>
              </Avatar>
              <div className="flex-1 min-w-0">
                <Link
                  to="/profile/$id"
                  params={{ id: data.id }}
                  className="text-sm font-semibold text-silver hover:text-ember truncate block"
                >
                  {data.display_name ?? "Anonymous"}
                </Link>
                {data.bio && <p className="text-[11px] text-muted-foreground line-clamp-2 mt-0.5">{data.bio}</p>}
                {!data.is_self && data.mutual_friend_count > 0 && (
                  <p className="text-[10px] text-ember mt-1 font-mono uppercase tracking-wider">
                    {data.mutual_friend_count} mutual
                  </p>
                )}
              </div>
            </div>

            <div className="grid grid-cols-3 gap-2 text-center">
              <Stat label="XP" value={data.lifetime_xp.toLocaleString()} />
              <Stat label="STREAK" value={`${data.current_focus_streak}d`} />
              <Stat label="TODAY" value={fmtMinutes(data.today_focus_seconds)} />
            </div>

            {data.recent_achievements.length > 0 && (
              <div>
                <div className="text-[9px] font-mono tracking-[0.3em] uppercase text-muted-foreground mb-1">
                  RECENT
                </div>
                <div className="flex flex-wrap gap-1">
                  {data.recent_achievements.map((a) => (
                    <span
                      key={a.id}
                      className="text-[10px] px-1.5 py-0.5 rounded bg-white/5 border border-white/10 text-silver-dim"
                      title={a.name}
                    >
                      {a.name}
                    </span>
                  ))}
                </div>
              </div>
            )}

            {!data.is_self && (
              <div className="pt-1">
                {data.friendship?.status === "accepted" && (
                  <div className="text-[10px] font-mono uppercase tracking-wider text-ember">◆ Friends</div>
                )}
                {data.friendship?.status === "pending" && data.friendship.direction === "outgoing" && (
                  <div className="text-[10px] font-mono uppercase tracking-wider text-muted-foreground">Requested</div>
                )}
                {data.friendship?.status === "pending" && data.friendship.direction === "incoming" && (
                  <Button size="sm" onClick={handleAccept} disabled={busy} className="w-full h-7 text-xs">
                    Accept request
                  </Button>
                )}
                {!data.friendship && (
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={handleAdd}
                    disabled={busy}
                    className="w-full h-7 text-xs border-white/10 hover:border-ember hover:text-ember"
                  >
                    Add friend
                  </Button>
                )}
              </div>
            )}
          </div>
        )}
      </HoverCardContent>
    </HoverCard>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="border border-white/5 rounded py-1.5">
      <div className="text-[9px] font-mono tracking-[0.2em] text-muted-foreground">{label}</div>
      <div className="text-xs text-silver font-mono mt-0.5">{value}</div>
    </div>
  );
}
