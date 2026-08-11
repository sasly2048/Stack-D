import { createServerFn } from "@tanstack/react-start";
import { z } from "zod";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";
import {
  USERNAME_CHANGE_COOLDOWN_HOURS,
  USERNAME_MESSAGES,
  validateUsername,
  type UsernameRejection,
} from "./username/validate";

export type UsernameResult =
  | { ok: true; username: string }
  | { ok: false; reason: UsernameRejection | "rate_limited"; message: string; retryAfterMinutes?: number };

const input = (d: unknown) => z.object({ username: z.string().max(64) }).parse(d);

/**
 * Availability probe for the form. Runs the same validation as the mutation so
 * the UX never promises something the write will reject.
 */
export const checkUsername = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator(input)
  .handler(async ({ data, context }): Promise<UsernameResult> => {
    const check = validateUsername(data.username);
    if (!check.ok) return { ok: false, reason: check.reason, message: check.message };

    const { data: existing } = await context.supabase
      .from("profiles")
      .select("id")
      .eq("username_canonical", check.canonical)
      .maybeSingle();

    if (existing && existing.id !== context.userId) {
      return { ok: false, reason: "taken", message: USERNAME_MESSAGES.taken };
    }
    return { ok: true, username: check.username };
  });

/** Claim or change the caller's username. Server-side source of truth. */
export const setMyUsername = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator(input)
  .handler(async ({ data, context }): Promise<UsernameResult> => {
    const check = validateUsername(data.username);
    if (!check.ok) return { ok: false, reason: check.reason, message: check.message };

    const { data: me } = await context.supabase
      .from("profiles")
      .select("username, username_canonical, username_changed_at")
      .eq("id", context.userId)
      .maybeSingle();

    // No-op re-save shouldn't burn the cooldown.
    if (me?.username_canonical === check.canonical && me?.username === check.username) {
      return { ok: true, username: check.username };
    }

    if (me?.username_changed_at) {
      const elapsedMs = Date.now() - new Date(me.username_changed_at).getTime();
      const windowMs = USERNAME_CHANGE_COOLDOWN_HOURS * 3600_000;
      if (elapsedMs < windowMs) {
        const retryAfterMinutes = Math.ceil((windowMs - elapsedMs) / 60_000);
        return {
          ok: false,
          reason: "rate_limited",
          message: `You can change your username again in ${
            retryAfterMinutes >= 60
              ? `${Math.ceil(retryAfterMinutes / 60)}h`
              : `${retryAfterMinutes}m`
          }.`,
          retryAfterMinutes,
        };
      }
    }

    const { error } = await context.supabase
      .from("profiles")
      .update({
        username: check.username,
        username_canonical: check.canonical,
        username_changed_at: new Date().toISOString(),
      })
      .eq("id", context.userId);

    if (error) {
      // 23505 = unique violation on username_canonical: someone claimed it
      // between the check and the write. Same opaque message as "reserved".
      if (error.code === "23505") {
        return { ok: false, reason: "taken", message: USERNAME_MESSAGES.taken };
      }
      throw new Error(error.message);
    }

    return { ok: true, username: check.username };
  });
