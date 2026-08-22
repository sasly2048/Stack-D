import { createServerFn } from "@tanstack/react-start";
import { z } from "zod";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";
import {
import { fetchMyPrivateProfile } from "./private-profile.server";
  USERNAME_CHANGE_COOLDOWN_HOURS,
  USERNAME_MESSAGES,
  validateUsername,
  type UsernameCheck,
  type UsernameRejection,
} from "./username/validate";

export type UsernameResult =
  | { ok: true; username: string }
  | { ok: false; reason: UsernameRejection | "rate_limited"; message: string; retryAfterMinutes?: number };

const input = (d: unknown) => z.object({ username: z.string().max(64) }).parse(d);

/**
 * Runs the authoritative check against the database-backed ruleset and records
 * an internal decision log (category / matched term / list version). None of
 * that detail is ever returned to the caller.
 */
async function screen(
  username: string,
  userId: string,
  persist: boolean,
): Promise<UsernameCheck> {
  const { loadModerationRuleset } = await import("./username/ruleset.server");
  const ruleset = await loadModerationRuleset();
  const result = validateUsername(username, ruleset);

  if (persist) {
    try {
      const { supabaseAdmin } = await import("@/integrations/supabase/client.server");
      await supabaseAdmin.from("username_moderation_events").insert({
        user_id: userId,
        attempted: username.slice(0, 64),
        canonical: result.ok ? result.canonical : null,
        decision: result.ok ? "allowed" : "rejected",
        reason: result.ok ? null : result.reason,
        category: result.ok ? null : (result.debug?.category ?? null),
        matched_term: result.ok ? null : (result.debug?.term ?? null),
        match_mode: result.ok ? null : (result.debug?.mode ?? null),
        matched_form: result.ok ? null : (result.debug?.form ?? null),
        confidence: result.ok ? null : (result.debug?.confidence ?? null),
        list_version: result.ok ? result.listVersion : (result.debug?.listVersion ?? ruleset.version),
      });
    } catch {
      // Logging must never block a username decision.
    }
  }

  return result;
}

/** Strip internals before anything crosses the wire. */
function publicResult(check: UsernameCheck): UsernameResult {
  return check.ok
    ? { ok: true, username: check.username }
    : { ok: false, reason: check.reason, message: check.message };
}

/**
 * Availability probe for the form. Runs exactly the same rules as the mutation
 * so the UX never promises something the write will reject.
 */
export const checkUsername = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator(input)
  .handler(async ({ data, context }): Promise<UsernameResult> => {
    const check = await screen(data.username, context.userId, false);
    if (!check.ok) return publicResult(check);

    const { data: taken } = await (
      context.supabase as unknown as {
        rpc: (
          fn: string,
          args: Record<string, unknown>,
        ) => Promise<{ data: boolean | null }>;
      }
    ).rpc("username_is_taken", {
      _canonical: check.canonical,
      _exclude_user: context.userId,
    });

    // Same wording as "reserved": never reveal that another account holds it.
    if (taken) {
      return { ok: false, reason: "taken", message: USERNAME_MESSAGES.taken };
    }
    return { ok: true, username: check.username };
  });

/** Claim or change the caller's username. Server-side source of truth. */
export const setMyUsername = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator(input)
  .handler(async ({ data, context }): Promise<UsernameResult> => {
    const check = await screen(data.username, context.userId, true);
    if (!check.ok) return publicResult(check);

    const me = await fetchMyPrivateProfile(context.supabase);

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
