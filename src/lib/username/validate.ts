/**
 * Shared username validation. The SERVER is the source of truth — this module
 * is imported by both the server function and the form so the messages match,
 * but the client copy is UX only and is always re-run server-side.
 */
import {
  BLOCKED_SUBSTRINGS,
  EXACT_BLOCKED,
  IMPERSONATION_MARKERS,
  LIST_VERSION,
  RESERVED_NAMES,
} from "./lists";
import { canonicalUsername, hasMixedScript, moderationForms } from "./normalize";

export const USERNAME_MIN = 3;
export const USERNAME_MAX = 20;
export const USERNAME_PATTERN = /^[A-Za-z][A-Za-z0-9_-]{2,19}$/;
/** Minimum gap between changes, enforced server-side. */
export const USERNAME_CHANGE_COOLDOWN_HOURS = 24;

export type UsernameRejection =
  | "required"
  | "too_short"
  | "too_long"
  | "must_start_with_letter"
  | "invalid_characters"
  | "invisible_characters"
  | "mixed_script"
  | "reserved"
  | "prohibited"
  | "taken";

export const USERNAME_MESSAGES: Record<UsernameRejection, string> = {
  required: "Choose a username.",
  too_short: `Usernames need at least ${USERNAME_MIN} characters.`,
  too_long: `Usernames can be at most ${USERNAME_MAX} characters.`,
  must_start_with_letter: "Usernames must start with a letter.",
  invalid_characters: "Use letters, numbers, underscores and hyphens only.",
  invisible_characters: "That username contains hidden or unsupported characters.",
  mixed_script: "That username mixes character sets that look alike. Use plain letters.",
  // Deliberately identical wording for reserved and taken names: revealing that
  // a name is *reserved* tells an attacker which handles the platform holds.
  reserved: "That username isn't available.",
  taken: "That username isn't available.",
  prohibited: "That username breaks our naming rules. Try something else.",
};

export type UsernameCheck =
  | { ok: true; username: string; canonical: string; listVersion: number }
  | { ok: false; reason: UsernameRejection; message: string };

function fail(reason: UsernameRejection): UsernameCheck {
  return { ok: false, reason, message: USERNAME_MESSAGES[reason] };
}

function containsAny(form: string, terms: readonly string[]): boolean {
  return terms.some((t) => form.includes(t));
}

/**
 * Full format + content validation. Does NOT check uniqueness (that needs the
 * database); the caller adds "taken".
 */
export function validateUsername(raw: unknown): UsernameCheck {
  if (typeof raw !== "string") return fail("required");
  const username = raw.trim();
  if (!username) return fail("required");

  // Reject invisible characters explicitly rather than silently stripping them,
  // so nobody ends up with a display name they didn't type.
  if (/[\u200B-\u200F\u2060-\u206F\uFEFF\u00AD\u180E\u3164]/.test(username)) {
    return fail("invisible_characters");
  }

  if (username.length < USERNAME_MIN) return fail("too_short");
  if (username.length > USERNAME_MAX) return fail("too_long");
  if (!/^[A-Za-z]/.test(username)) return fail("must_start_with_letter");
  // Mixed-script check runs after the ASCII pattern so that emoji and other
  // stray characters get the plainer "invalid characters" explanation.
  if (!USERNAME_PATTERN.test(username)) {
    return fail(hasMixedScript(username) ? "mixed_script" : "invalid_characters");
  }


  const canonical = canonicalUsername(username);
  if (canonical.length < USERNAME_MIN) return fail("too_short");

  const forms = moderationForms(username);

  if (forms.some((f) => RESERVED_NAMES.includes(f))) return fail("reserved");
  if (forms.some((f) => containsAny(f, IMPERSONATION_MARKERS))) return fail("reserved");
  if (forms.some((f) => EXACT_BLOCKED.includes(f))) return fail("prohibited");
  if (forms.some((f) => containsAny(f, BLOCKED_SUBSTRINGS))) return fail("prohibited");

  return { ok: true, username, canonical, listVersion: LIST_VERSION };
}

export { canonicalUsername, LIST_VERSION };
