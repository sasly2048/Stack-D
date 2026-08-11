/**
 * Username normalization.
 *
 * Two different normal forms exist, and mixing them up is the classic bug:
 *
 *  - `canonicalUsername()` — the uniqueness key. Lossless-ish: case folded and
 *    separators stripped, so "Ada_Lovelace" and "adalovelace" cannot both be
 *    claimed. Stored in profiles.username_canonical.
 *  - `normalizeForModeration()` — the aggressive form used ONLY for blocklist
 *    matching: confusables folded, leetspeak undone, repeats collapsed. It is
 *    deliberately lossy and must never be used for uniqueness or display.
 */

/** Zero-width, joiners, BOM, bidi controls, soft hyphen, variation selectors. */
const INVISIBLE = /[\u00AD\u034F\u061C\u115F\u1160\u17B4\u17B5\u180B-\u180E\u200B-\u200F\u202A-\u202E\u2060-\u2064\u2066-\u206F\u3164\uFE00-\uFE0F\uFEFF\uFFA0]/g;

/** Anything that a human reads as a separator or decoration. */
const SEPARATORS = /[\s._\-+~*'"`^|/\\()[\]{}<>,;:!?@#$%&=]/g;

/** Confusables / homoglyphs → ASCII. Applied after NFKC. */
const CONFUSABLES: Record<string, string> = {
  // Cyrillic
  а: "a", в: "b", с: "c", ԁ: "d", е: "e", ѕ: "s", і: "i", ј: "j", к: "k",
  м: "m", н: "h", о: "o", р: "p", т: "t", у: "y", х: "x", г: "r", ѵ: "v",
  ц: "u", ь: "b", я: "r", б: "b", д: "d", л: "n", п: "n", ч: "y", ш: "w",
  // Greek
  α: "a", β: "b", γ: "y", ε: "e", ζ: "z", η: "n", ι: "i", κ: "k", ν: "v",
  ο: "o", ρ: "p", σ: "o", τ: "t", υ: "u", χ: "x", ω: "w", θ: "o", µ: "u",
  // Latin lookalikes / accents that NFKD alone misses
  ı: "i", ł: "l", ø: "o", đ: "d", ð: "d", þ: "p", ß: "ss", æ: "ae", œ: "oe",
  ƒ: "f", ѐ: "e", ĸ: "k", ɢ: "g", ʀ: "r", ɪ: "i", ᴀ: "a", ᴄ: "c", ᴇ: "e",
  ᴏ: "o", ᴘ: "p", ᴛ: "t", ᴜ: "u", ᴠ: "v", ʏ: "y", ʙ: "b", ᴅ: "d", ʜ: "h",
  ᴊ: "j", ᴋ: "k", ʟ: "l", ᴍ: "m", ɴ: "n", ꜱ: "s", ᴡ: "w", ᴢ: "z",
  // Armenian / Hebrew / Arabic-indic shapes commonly used as lookalikes
  օ: "o", ո: "n", ս: "u", գ: "q", ա: "a", ի: "h", ց: "g", ք: "p",
};

/** Leetspeak and symbol substitutions, undone for moderation only. */
const LEET: Record<string, string> = {
  "0": "o", "1": "i", "3": "e", "4": "a", "5": "s", "6": "g", "7": "t",
  "8": "b", "9": "g", "2": "z", "$": "s", "€": "e", "£": "l", "¥": "y",
  "@": "a", "!": "i", "|": "l", "+": "t", "(": "c", "¢": "c", "×": "x",
};

/** Strip diacritics, fold width/ligatures, drop invisible characters. */
function baseNormalize(input: string): string {
  return input
    .normalize("NFKC")
    .replace(INVISIBLE, "")
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase();
}

function foldConfusables(input: string): string {
  let out = "";
  for (const ch of input) out += CONFUSABLES[ch] ?? ch;
  return out;
}

function undoLeet(input: string): string {
  let out = "";
  for (const ch of input) out += LEET[ch] ?? ch;
  return out;
}

/** "fuuuuck" → "fuck"; caps any run at a single character. */
function collapseRepeats(input: string): string {
  return input.replace(/(.)\1+/g, "$1");
}

/**
 * Uniqueness key: lowercase, separators removed, confusables folded so a
 * homoglyph twin cannot squat next to a real account. Digits are preserved.
 */
export function canonicalUsername(input: string): string {
  return foldConfusables(baseNormalize(input))
    .replace(SEPARATORS, "")
    .replace(/[^a-z0-9]/g, "");
}

/**
 * Every form the blocklists should be tested against. More forms = fewer
 * bypasses; each is cheap, so we check them all.
 */
export function moderationForms(input: string): string[] {
  const base = baseNormalize(input);
  const folded = foldConfusables(base);
  const stripped = folded.replace(SEPARATORS, "").replace(/[^a-z0-9]/g, "");
  const deleet = undoLeet(folded).replace(SEPARATORS, "").replace(/[^a-z]/g, "");
  const forms = new Set<string>([
    input.toLowerCase(),
    base,
    folded,
    stripped,
    collapseRepeats(stripped),
    deleet,
    collapseRepeats(deleet),
    stripped.replace(/[0-9]/g, ""),
  ]);
  forms.delete("");
  return [...forms];
}

/** Primary moderation form (kept for readability in callers/tests). */
export function normalizeForModeration(input: string): string {
  return collapseRepeats(
    undoLeet(foldConfusables(baseNormalize(input)))
      .replace(SEPARATORS, "")
      .replace(/[^a-z]/g, ""),
  );
}

/**
 * Mixed-script abuse: a name that blends Latin with another script is almost
 * always a homoglyph attack rather than a genuine multilingual name, because
 * our format rule already restricts accepted characters to ASCII.
 */
export function hasMixedScript(input: string): boolean {
  const cleaned = input.replace(INVISIBLE, "");
  const scripts = new Set<string>();
  for (const ch of cleaned) {
    if (/[0-9_\-\s]/.test(ch)) continue;
    if (/[a-zA-Z]/.test(ch)) scripts.add("latin");
    else if (/[\u0400-\u04FF]/.test(ch)) scripts.add("cyrillic");
    else if (/[\u0370-\u03FF]/.test(ch)) scripts.add("greek");
    else if (/[\u0530-\u058F]/.test(ch)) scripts.add("armenian");
    else if (/[\u0590-\u05FF]/.test(ch)) scripts.add("hebrew");
    else if (/[\u0600-\u06FF]/.test(ch)) scripts.add("arabic");
    else if (/[\u0900-\u097F]/.test(ch)) scripts.add("devanagari");
    else if (/[\u4E00-\u9FFF]/.test(ch)) scripts.add("han");
    else scripts.add("other");
  }
  return scripts.size > 1;
}
