import { useEffect, useRef } from "react";

type Status = "idle" | "ready" | "verified" | "error" | "expired";

declare global {
  interface Window {
    turnstile?: {
      render: (
        el: HTMLElement,
        opts: {
          sitekey: string;
          theme?: "light" | "dark" | "auto";
          callback?: (token: string) => void;
          "error-callback"?: () => void;
          "expired-callback"?: () => void;
          action?: string;
        },
      ) => string;
      reset: (id?: string) => void;
      remove: (id?: string) => void;
    };
  }
}

const SCRIPT_SRC = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";

function loadScript(): Promise<void> {
  if (typeof window === "undefined") return Promise.resolve();
  if (window.turnstile) return Promise.resolve();
  const existing = document.querySelector<HTMLScriptElement>(`script[src="${SCRIPT_SRC}"]`);
  if (existing) {
    return new Promise((res) => existing.addEventListener("load", () => res(), { once: true }));
  }
  return new Promise((res, rej) => {
    const s = document.createElement("script");
    s.src = SCRIPT_SRC;
    s.async = true;
    s.defer = true;
    s.onload = () => res();
    s.onerror = () => rej(new Error("turnstile_script_failed"));
    document.head.appendChild(s);
  });
}

export function Turnstile({
  action,
  onToken,
  onStatus,
}: {
  action: string;
  onToken: (token: string | null) => void;
  onStatus?: (s: Status) => void;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const idRef = useRef<string | null>(null);
  const sitekey = import.meta.env.VITE_TURNSTILE_SITE_KEY as string | undefined;

  useEffect(() => {
    if (!sitekey || !ref.current) return;
    let cancelled = false;
    onStatus?.("idle");
    loadScript()
      .then(() => {
        if (cancelled || !window.turnstile || !ref.current) return;
        onStatus?.("ready");
        idRef.current = window.turnstile.render(ref.current, {
          sitekey,
          theme: "dark",
          action,
          callback: (token) => { onToken(token); onStatus?.("verified"); },
          "error-callback": () => { onToken(null); onStatus?.("error"); },
          "expired-callback": () => { onToken(null); onStatus?.("expired"); },
        });
      })
      .catch(() => onStatus?.("error"));
    return () => {
      cancelled = true;
      try {
        if (idRef.current && window.turnstile) window.turnstile.remove(idRef.current);
      } catch { /* noop */ }
      idRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sitekey, action]);

  if (!sitekey) {
    return (
      <div className="font-mono text-[10px] uppercase tracking-widest text-muted-foreground/70">
        CAPTCHA not configured
      </div>
    );
  }
  return <div ref={ref} className="flex justify-center" />;
}

/** Cheap, non-tracking device fingerprint — UA + screen + tz + language. */
export function getDeviceFingerprint(): string {
  if (typeof window === "undefined") return "ssr";
  const parts = [
    navigator.userAgent || "",
    String(window.screen?.width || 0),
    String(window.screen?.height || 0),
    String(window.devicePixelRatio || 1),
    Intl.DateTimeFormat().resolvedOptions().timeZone || "",
    navigator.language || "",
    String((navigator as Navigator & { hardwareConcurrency?: number }).hardwareConcurrency || 0),
  ].join("|");
  // FNV-1a 32-bit
  let h = 0x811c9dc5;
  for (let i = 0; i < parts.length; i++) {
    h ^= parts.charCodeAt(i);
    h = (h + ((h << 1) + (h << 4) + (h << 7) + (h << 8) + (h << 24))) >>> 0;
  }
  return h.toString(16).padStart(8, "0");
}
