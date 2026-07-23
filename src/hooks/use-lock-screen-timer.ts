import { useEffect } from "react";

/**
 * Media Session API integration so the OS lock screen / control center
 * shows an active "Focus Session" tile with remaining seconds while a
 * session is running. Silently no-ops on unsupported browsers.
 */
export function useLockScreenTimer(active: boolean, code: string, remainingSec: number) {
  useEffect(() => {
    if (!active || typeof navigator === "undefined" || !("mediaSession" in navigator)) return;
    try {
      const ms = navigator.mediaSession;
      const mins = Math.max(0, Math.floor(remainingSec / 60));
      const secs = Math.max(0, remainingSec % 60);
      ms.metadata = new MediaMetadata({
        title: `Focus Session · ${code}`,
        artist: `${String(mins).padStart(2, "0")}:${String(secs).padStart(2, "0")} remaining`,
        album: "Stack'd",
      });
      ms.playbackState = "playing";
    } catch {
      /* ignore */
    }
    return () => {
      try {
        navigator.mediaSession.playbackState = "none";
        navigator.mediaSession.metadata = null;
      } catch { /* ignore */ }
    };
  }, [active, code, Math.floor(remainingSec / 15)]); // reduce update noise
}
