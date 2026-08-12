/**
 * Boundary-aware moderation matcher.
 *
 * Design goals, in priority order:
 *  1. Do not reject legitimate usernames. "Sasly204800" must pass even though
 *     leetspeak-undoing it produces the letter run "…abo…".
 *  2. Still catch deliberate obfuscation: separators, homoglyphs, leetspeak,
 *     repeated characters, invisible characters.
 *
 * The trick is that different *forms* of the username are trusted differently.
 * Forms built from real letters (base / separator-stripped) are checked with
 * the full mode set. Lossy forms (leetspeak-undone, repeat-collapsed) can
 * manufacture accidental letter runs, so they only accept whole-string matches
 * or long unambiguous substrings — never loose token-boundary matches.
 */
import type { MatchMode, ModerationCategory, ModerationTerm } from "./terms";
import {
  canonicalUsername,
  collapseRepeats,
  deleetForm,
  foldedBase,
  strippedForm,
} from "./normalize";

export interface ModerationRuleset {
  version: number;
  terms: readonly ModerationTerm[];
  /** Canonical usernames reviewed as legitimate. */
  allowlist: ReadonlySet<string>;
}

export interface ModerationHit {
  category: ModerationCategory;
  term: string;
  mode: MatchMode;
  /** Which normalized form triggered it — internal debugging only. */
  form: string;
  confidence: number;
}

/** Block threshold. Weak-form hits land exactly on it. */
export const CONFIDENCE_THRESHOLD = 0.7;
/** Lossy forms only consider substrings at least this long. */
const WEAK_MIN_LENGTH = 4;

const CONFIDENCE: Record<MatchMode, number> = {
  exact: 1,
  substring: 0.95,
  word: 0.85,
};

/**
 * Tokens are letter runs: we split on separators *and* on digit runs, so
 * "big_dick" and "bigdick69" both expose "dick" at a boundary while
 * "cassandra" never exposes "ass".
 */
function tokenize(form: string): string[] {
  return form.split(/[^a-z]+/).filter(Boolean);
}

function tokenMatches(token: string, term: string): boolean {
  return token === term || token.startsWith(term) || token.endsWith(term);
}

function digitsTrimmed(value: string): string {
  return value.replace(/[0-9]+$/, "");
}

/**
 * Blanks out whole tokens that are on the reviewed allowlist. Token-level (not
 * whole-name) so a legitimate stem stays legitimate when combined with other
 * words or digits.
 */
function neutralizeAllowlisted(raw: string, allowlist: ReadonlySet<string>): string {
  const folded = foldedBase(raw);
  const kept = folded
    .split(/([^a-z0-9]+)/)
    .map((part) => {
      const letters = part.replace(/[^a-z0-9]/g, "");
      if (!letters) return part;
      if (allowlist.has(letters) || allowlist.has(digitsTrimmed(letters))) {
        return " ";
      }
      return part;
    })
    .join("");
  return kept;
}

export function buildRuleset(
  terms: readonly ModerationTerm[],
  allowlist: readonly string[],
  version: number,
): ModerationRuleset {
  const seen = new Set<string>();
  const deduped: ModerationTerm[] = [];
  for (const t of terms) {
    const key = `${t.category}:${t.term}`;
    if (seen.has(key)) continue;
    seen.add(key);
    deduped.push(t);
  }
  return { version, terms: deduped, allowlist: new Set(allowlist) };
}

/**
 * Returns the strongest hit at or above the confidence threshold, or null when
 * the name is clean.
 */
export function screenUsername(
  raw: string,
  ruleset: ModerationRuleset,
): ModerationHit | null {
  const canonical = canonicalUsername(raw);
  if (
    ruleset.allowlist.has(canonical) ||
    ruleset.allowlist.has(digitsTrimmed(canonical))
  ) {
    return null;
  }

  // Allowlisted tokens are removed before matching, so "peacock_dev" and
  // "cocktail99" clear even though their stems are blocked elsewhere.
  const neutralized = neutralizeAllowlisted(raw, ruleset.allowlist);

  const base = foldedBase(neutralized);
  const stripped = strippedForm(neutralized);
  const lettersOnly = stripped.replace(/[0-9]/g, "");

  /** Full-trust forms: every mode applies. */
  const strong = [
    { name: "base", value: base },
    { name: "stripped", value: stripped },
    { name: "letters", value: lettersOnly },
  ].filter((f) => f.value);

  const deleet = deleetForm(neutralized);
  /** Lossy forms: exact match, or long substrings only. */
  const weak = [
    { name: "deleet", value: deleet },
    { name: "collapsed", value: collapseRepeats(lettersOnly) },
    { name: "collapsed_deleet", value: collapseRepeats(deleet) },
  ].filter((f) => f.value && !strong.some((s) => s.value === f.value));

  const strongTokens = strong.map((f) => ({ name: f.name, tokens: tokenize(f.value) }));

  let best: ModerationHit | null = null;
  const consider = (hit: ModerationHit) => {
    if (hit.confidence < CONFIDENCE_THRESHOLD) return;
    if (!best || hit.confidence > best.confidence) best = hit;
  };

  for (const t of ruleset.terms) {
    // --- strong forms -------------------------------------------------
    for (const form of strong) {
      if (t.mode === "exact") {
        if (form.value === t.term) {
          consider({ ...t, form: form.name, confidence: CONFIDENCE.exact });
        }
      } else if (t.mode === "substring") {
        if (form.value.includes(t.term)) {
          consider({ ...t, form: form.name, confidence: CONFIDENCE.substring });
        }
      }
    }
    if (t.mode === "word") {
      for (const form of strongTokens) {
        if (form.tokens.some((tok) => tokenMatches(tok, t.term))) {
          consider({ ...t, form: form.name, confidence: CONFIDENCE.word });
        }
      }
    }

    // --- lossy forms --------------------------------------------------
    for (const form of weak) {
      if (form.value === t.term) {
        consider({ ...t, form: form.name, confidence: CONFIDENCE.exact });
      } else if (
        t.mode === "substring" &&
        t.term.length >= WEAK_MIN_LENGTH &&
        form.value.includes(t.term)
      ) {
        consider({ ...t, form: form.name, confidence: CONFIDENCE_THRESHOLD });
      }
    }
  }

  return best;
}
