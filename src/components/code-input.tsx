import { useEffect, useRef, useState } from "react";
import { cn } from "@/lib/utils";

type Props = {
  value: string;
  onChange: (v: string) => void;
  length?: number;
  invalid?: boolean;
  ariaLabel?: string;
};

/**
 * 6-slot room code input.
 * - Hidden <input> owns the real cursor + selection, so the OS handles
 *   keyboard, IME, paste, autofill, undo, and text selection natively.
 * - Visible tiles mirror the value with per-character entrance animation,
 *   show the caret on the active slot, and visualise selection ranges.
 * - Backspace deletes one char at a time at the current caret position.
 */
export function CodeInput({
  value,
  onChange,
  length = 6,
  invalid = false,
  ariaLabel = "Room code",
}: Props) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [focused, setFocused] = useState(false);
  const [sel, setSel] = useState<{ start: number; end: number }>({
    start: 0,
    end: 0,
  });

  const sanitize = (raw: string) =>
    raw.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, length);

  const syncSelection = () => {
    const el = inputRef.current;
    if (!el) return;
    setSel({
      start: el.selectionStart ?? value.length,
      end: el.selectionEnd ?? value.length,
    });
  };

  // Keep selection state fresh while focused (keyboard arrows, mouse drag, etc.)
  useEffect(() => {
    if (!focused) return;
    const id = window.setInterval(syncSelection, 60);
    return () => window.clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [focused]);

  const focus = () => {
    const el = inputRef.current;
    if (!el) return;
    el.focus();
    // Place caret at the end of the entered value by default
    const pos = value.length;
    el.setSelectionRange(pos, pos);
    syncSelection();
  };

  return (
    <div
      className="relative w-full"
      onClick={focus}
      role="group"
      aria-label={ariaLabel}
      aria-describedby="code-input-hint"
    >
      <span id="code-input-hint" className="sr-only">
        Enter a 6-character room code. You can paste a code or type one character at a time.
        Use backspace to delete; arrow keys to move between characters.
      </span>
      <input
        ref={inputRef}
        type="text"
        inputMode="text"
        autoComplete="one-time-code"
        spellCheck={false}
        autoCapitalize="characters"
        aria-label={ariaLabel}
        aria-invalid={invalid || undefined}
        aria-required="true"
        maxLength={length}
        value={value}
        onChange={(e) => {
          onChange(sanitize(e.target.value));
          requestAnimationFrame(syncSelection);
        }}
        onPaste={(e) => {
          // Honour paste explicitly so sanitization happens before commit.
          const txt = e.clipboardData?.getData("text") ?? "";
          if (!txt) return;
          e.preventDefault();
          onChange(sanitize(txt));
          requestAnimationFrame(() => {
            const el = inputRef.current;
            if (!el) return;
            const pos = sanitize(txt).length;
            el.setSelectionRange(pos, pos);
            syncSelection();
          });
        }}
        onSelect={syncSelection}
        onKeyUp={syncSelection}
        onKeyDown={(e) => {
          // Submit on Enter handled by the parent form; arrows/backspace are native.
          if (e.key === "Home") {
            e.preventDefault();
            inputRef.current?.setSelectionRange(0, 0);
            syncSelection();
          } else if (e.key === "End") {
            e.preventDefault();
            const pos = value.length;
            inputRef.current?.setSelectionRange(pos, pos);
            syncSelection();
          }
        }}
        onClick={(e) => {
          e.stopPropagation();
          syncSelection();
        }}
        onFocus={() => {
          setFocused(true);
          syncSelection();
        }}
        onBlur={() => setFocused(false)}
        className="absolute inset-0 w-full h-full opacity-0 cursor-text"
      />


      <div
        aria-hidden="true"
        className={cn(
          "flex gap-2 sm:gap-3 select-none transition-[transform,filter] duration-300",
          invalid && "animate-shake",
        )}
      >
        {Array.from({ length }).map((_, i) => {
          const char = value[i];
          const filled = i < value.length;
          const caretHere =
            focused && sel.start === sel.end && sel.start === i;
          const caretAtEnd =
            focused &&
            sel.start === sel.end &&
            sel.start === length &&
            i === length - 1;
          const selected =
            focused && sel.end > sel.start && i >= sel.start && i < sel.end;

          return (
            <div
              key={i}
              className={cn(
                "relative flex-1 aspect-[3/4] max-h-16 rounded-lg border bg-white/[0.04] flex items-center justify-center font-mono text-lg font-bold uppercase",
                "transition-[border-color,background,box-shadow,transform] duration-300 ease-[cubic-bezier(0.32,0.72,0,1)]",
                filled && "border-white/25 bg-white/[0.08] text-silver",
                !filled && "border-white/10 text-muted-foreground",
                focused && !invalid && i === value.length && "border-ember/70 shadow-[0_0_0_3px_rgba(201,135,74,0.12)]",
                selected && "bg-ember/20 border-ember/60",
                invalid && filled && "border-breach/70 text-breach",
                invalid && !filled && "border-breach/30",
              )}
            >
              {char && (
                <span
                  key={`${i}-${char}`}
                  className="animate-char-in inline-block"
                >
                  {char}
                </span>
              )}
              {caretHere && (
                <span
                  className="absolute h-6 w-px bg-ember animate-caret-blink"
                  aria-hidden="true"
                />
              )}
              {caretAtEnd && (
                <span
                  className="absolute right-2 h-6 w-px bg-ember animate-caret-blink"
                  aria-hidden="true"
                />
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
