import { useCallback, useEffect, useRef, useState } from "react";

type Theme = "dark" | "light";

const STORAGE_KEY = "stackd:theme";
/**
 * If a focus protocol has stamped its own override (e.g. the room runtime
 * forces OLED-black), we skip mutating document classes from here. The room
 * sets `dataset.themeLock = "1"` when active.
 */
function isThemeLocked(): boolean {
  if (typeof document === "undefined") return false;
  return document.documentElement.dataset.themeLock === "1";
}

function readStoredTheme(): Theme {
  if (typeof window === "undefined") return "dark";
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    if (stored === "light" || stored === "dark") return stored;
  } catch { /* private mode */ }
  return window.matchMedia?.("(prefers-color-scheme: light)").matches ? "light" : "dark";
}

function applyTheme(theme: Theme) {
  if (isThemeLocked()) return;
  const root = document.documentElement;
  root.dataset.theme = theme;
  root.classList.toggle("light", theme === "light");
  root.classList.toggle("dark", theme === "dark");
}

/**
 * Dark/light toggle. Brief §1 (theme):
 *   - `mounted` gate prevents SSR mismatch — we render an inert placeholder
 *     until after hydration, then sync to localStorage / prefers-color-scheme.
 *   - `touch-action: manipulation` + a single-fire ref kills the ghost-click
 *     double-trigger that mobile browsers emit (touchend + synthetic click).
 *   - Respects `data-theme-lock` so the focus runtime can pin OLED-black.
 */
export function ThemeToggle({ className = "" }: { className?: string }) {
  const [mounted, setMounted] = useState(false);
  const [theme, setTheme] = useState<Theme>("dark");
  const inFlightRef = useRef(false);

  useEffect(() => {
    const initial = readStoredTheme();
    setTheme(initial);
    applyTheme(initial);
    setMounted(true);
  }, []);

  const toggle = useCallback((e?: React.SyntheticEvent) => {
    // Stop the synthetic click that fires after a touchend on mobile.
    e?.preventDefault();
    if (inFlightRef.current) return;
    inFlightRef.current = true;
    // 350ms guard fully spans the touchend → emulated-click window that
    // mobile browsers emit (≈300ms). rAF was too short on slow devices.
    window.setTimeout(() => { inFlightRef.current = false; }, 350);

    setTheme((prev) => {
      const next: Theme = prev === "dark" ? "light" : "dark";
      applyTheme(next);
      try { window.localStorage.setItem(STORAGE_KEY, next); } catch { /* ignore */ }
      return next;
    });
  }, []);

  if (!mounted) {
    return (
      <span
        aria-hidden
        className={`inline-block w-[4.5rem] opacity-0 ${className}`}
      >
        ☾ Dark
      </span>
    );
  }

  return (
    <button
      type="button"
      onClick={toggle}
      onTouchEnd={toggle}
      aria-label={`Switch to ${theme === "dark" ? "light" : "dark"} theme`}
      aria-pressed={theme === "light"}
      style={{ touchAction: "manipulation" }}
      className={`hover:text-silver transition-colors select-none ${className}`}
    >
      {theme === "dark" ? "☾ Dark" : "☀ Light"}
    </button>
  );
}
