import { useEffect } from "react";
import { toast } from "sonner";
import { supabase } from "@/integrations/supabase/client";
import { haptic } from "@/lib/haptics";
import { copy } from "@/lib/copy";

interface ActivityEvent {
  id: string;
  user_id: string;
  kind: string;
  payload: Record<string, unknown>;
}

/**
 * Global realtime dispatcher: subscribes to the current user's activity_events
 * stream and surfaces a small toast for socially-meaningful events.
 * Mounted once at the root.
 */
export function GlobalRealtimeToasts() {
  useEffect(() => {
    let cancelled = false;
    let channel: ReturnType<typeof supabase.channel> | null = null;
    (async () => {
      const { data } = await supabase.auth.getUser();
      const uid = data.user?.id;
      if (cancelled || !uid) return;
      channel = supabase
        .channel(`activity:${uid}`)
        .on("postgres_changes", { event: "INSERT", schema: "public", table: "activity_events", filter: `user_id=eq.${uid}` }, (payload) => {
          const evt = payload.new as ActivityEvent;
          switch (evt.kind) {
            case "achievement_unlock":
              haptic("success");
              toast.success("🏅 Achievement unlocked", { description: String(evt.payload.id ?? ""), id: evt.id });
              break;
            case "challenge_complete":
              haptic("success");
              toast.success("🎯 Challenge complete", { description: `${evt.payload.name ?? ""} · +${evt.payload.xp ?? 0} XP`, id: evt.id });
              break;
            case "friend_add":
              haptic("select");
              toast(`🤝 ${copy.realtime.friendAdded}`, { id: evt.id });
              break;
            case "session_complete":
              haptic("select");
              toast(`✅ ${copy.realtime.sessionComplete(Number(evt.payload.xp ?? 0))}`, { id: evt.id });
              break;
            default:
              break;
          }
        })
        .subscribe();
    })();
    return () => {
      cancelled = true;
      if (channel) supabase.removeChannel(channel);
    };
  }, []);
  return null;
}
