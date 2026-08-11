import { useEffect, useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useServerFn } from "@tanstack/react-start";
import { toast } from "sonner";
import { INTERACTIVE } from "@/components/ui/interactive";
import { checkUsername, setMyUsername, type UsernameResult } from "@/lib/username.functions";
import {
  USERNAME_CHANGE_COOLDOWN_HOURS,
  USERNAME_MAX,
  validateUsername,
} from "@/lib/username/validate";

/**
 * Username claim/change form.
 *
 * Client validation here is purely UX: every keystroke check and the final
 * submit both go through the same rules the server re-runs authoritatively.
 */
export function UsernameForm({ current }: { current: string | null }) {
  const queryClient = useQueryClient();
  const probe = useServerFn(checkUsername);
  const save = useServerFn(setMyUsername);

  const [value, setValue] = useState(current ?? "");
  const [touched, setTouched] = useState(false);
  const [remote, setRemote] = useState<UsernameResult | null>(null);
  const [checking, setChecking] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const trimmed = value.trim();
  const local = trimmed ? validateUsername(trimmed) : null;
  const unchanged = trimmed.toLowerCase() === (current ?? "").toLowerCase();

  // Debounced availability probe. Only fires once the shape is already legal,
  // so we never round-trip obviously-invalid input.
  useEffect(() => {
    setRemote(null);
    if (!local?.ok || unchanged) return;
    let cancelled = false;
    setChecking(true);
    const t = setTimeout(async () => {
      try {
        const res = (await probe({ data: { username: trimmed } })) as UsernameResult;
        if (!cancelled) setRemote(res);
      } catch {
        if (!cancelled) setRemote(null);
      } finally {
        if (!cancelled) setChecking(false);
      }
    }, 400);
    return () => {
      cancelled = true;
      clearTimeout(t);
      setChecking(false);
    };
  }, [trimmed, local?.ok, unchanged, probe]);

  const mutation = useMutation({
    mutationFn: () => save({ data: { username: trimmed } }) as Promise<UsernameResult>,
    onSuccess: (res) => {
      if (res.ok) {
        toast.success(`Username set to @${res.username}`);
        setRemote(null);
        void queryClient.invalidateQueries({ queryKey: ["my-profile"] });
      } else {
        setRemote(res);
        inputRef.current?.focus();
      }
    },
    onError: () => toast.error("Could not set username"),
  });

  const error =
    touched && trimmed && local && !local.ok
      ? local.message
      : remote && !remote.ok
        ? remote.message
        : null;
  const available = remote?.ok === true && !checking;
  const canSubmit = !!local?.ok && !unchanged && !checking && !mutation.isPending && !error;

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        setTouched(true);
        if (canSubmit) mutation.mutate();
      }}
      className="space-y-3"
    >
      <h2 className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">Username</h2>
      <label className="block space-y-2">
        <span className="sr-only">Username</span>
        <div className="flex items-center gap-2 border border-white/10 focus-within:border-ember/60 rounded-md px-4 py-3 transition-colors">
          <span aria-hidden="true" className="font-mono text-silver-dim">
            @
          </span>
          <input
            ref={inputRef}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            onBlur={() => setTouched(true)}
            maxLength={USERNAME_MAX}
            autoComplete="username"
            spellCheck={false}
            aria-invalid={!!error || undefined}
            aria-describedby="username-hint"
            placeholder="yourname"
            className="w-full bg-transparent outline-none font-mono"
          />
        </div>
      </label>

      <p
        id="username-hint"
        role={error ? "alert" : undefined}
        aria-live="polite"
        className={`font-mono text-[10px] tracking-wide ${
          error ? "text-breach" : available ? "text-ember" : "text-silver-dim"
        }`}
      >
        {error
          ? error
          : checking
            ? "Checking availability…"
            : available
              ? "Available"
              : `3–20 characters, starts with a letter, letters/numbers/_/- only. You can change it once every ${USERNAME_CHANGE_COOLDOWN_HOURS}h.`}
      </p>

      <button
        type="submit"
        disabled={!canSubmit}
        aria-busy={mutation.isPending}
        className={`font-mono text-[10px] tracking-[0.3em] uppercase px-5 py-2.5 border border-ember/40 text-ember hover:bg-ember/10 rounded-full transition-colors disabled:opacity-50 ${INTERACTIVE}`}
      >
        {mutation.isPending ? "Claiming…" : current ? "Change username" : "Claim username"}
      </button>
    </form>
  );
}
