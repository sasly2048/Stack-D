/**
 * Versioned moderation term config.
 *
 * This file is the *fallback / seed* source of truth. At runtime the server
 * loads the same data from the database (`moderation_terms`,
 * `moderation_allowlist`), so lists can be updated without a redeploy and
 * without touching the matching logic. Keep the two in sync by re-seeding from
 * here whenever LIST_VERSION is bumped.
 *
 * Match modes — chosen per term to keep false positives low:
 *  - "substring": term may appear anywhere. Only for long, unambiguous terms.
 *  - "word":      term must align with a token boundary (whole token, or the
 *                 token's prefix/suffix). Used for medium-length terms that
 *                 also occur inside innocent words.
 *  - "exact":     the whole username (normalized) must be the term. Used for
 *                 short/ambiguous stems where anything looser misfires.
 *
 * Rule of thumb: length < 4 → "exact"; 4-5 and embeddable in real words →
 * "word"; otherwise "substring".
 */

export const LIST_VERSION = 2;

export type ModerationCategory =
  | "profanity"
  | "nsfw"
  | "slurs"
  | "reserved"
  | "impersonation";

export type MatchMode = "exact" | "word" | "substring";

export interface ModerationTerm {
  category: ModerationCategory;
  /** Canonical (lowercase a-z0-9) form of the term. */
  term: string;
  mode: MatchMode;
}

function build(
  category: ModerationCategory,
  mode: MatchMode,
  terms: readonly string[],
): ModerationTerm[] {
  return terms.map((term) => ({ category, term, mode }));
}

/* ------------------------------------------------------------------ */
/* profanity                                                           */
/* ------------------------------------------------------------------ */

const PROFANITY_SUBSTRING = [
  "fuck", "fucker", "fucking", "motherfucker", "fuk", "phuck", "shit", "shite",
  "bullshit", "bitch", "bastard", "asshole", "arsehole", "dumbass", "jackass",
  "dickhead", "douchebag", "wanker", "bollocks", "prick", "cunt", "kunt",
  "goddamn", "damnit", "pissoff", "arsewipe", "chutiya", "chutiye",
  "madarchod", "behenchod", "bhenchod", "bhosdike", "bhosda", "gandu",
  "harami", "kamina", "pendejo", "cabron", "mierda", "arschloch", "scheisse",
  "stronzo", "connard", "salope",
];

const PROFANITY_WORD = [
  "fck", "twat", "slut", "whore", "skank", "piss", "minge", "kutta", "puta",
  "puto", "carajo", "merde", "cazzo", "gaand", "prick",
];

const PROFANITY_EXACT = [
  "ass", "arse", "bum", "wank", "fap", "wtf", "stfu", "gtfo", "phuk", "fuc",
  "knt", "hoe", "hoes", "cono", "damn", "crap", "bugger", "randi", "lund",
  "lauda", "fanny",
];

/* ------------------------------------------------------------------ */
/* nsfw                                                                */
/* ------------------------------------------------------------------ */

const NSFW_SUBSTRING = [
  "porn", "pornhub", "xvideos", "xhamster", "onlyfans", "hentai", "sexcam",
  "camgirl", "camboy", "escort", "hooker", "prostitute", "brothel", "nudes",
  "boobs", "titties", "nipple", "vagina", "pussy", "penis", "scrotum",
  "testicle", "butthole", "rectum", "cumshot", "orgasm", "masturbat",
  "jerkoff", "handjob", "blowjob", "rimjob", "deepthroat", "creampie",
  "gangbang", "threesome", "bukkake", "fetish", "bondage", "incest",
  "bestiality", "zoophilia", "necrophilia", "pedophile", "childporn",
  "molest", "upskirt", "voyeur", "striptease", "buttplug", "vibrator",
  "fleshlight", "sextape", "sexting", "sexchat", "sexslave", "sexvideo", "sexdoll", "hotsex", "freesex", "cumming", "squirt", "sexy", "nsfw",
];

const NSFW_WORD = [
  "nude", "naked", "boob", "tits", "titty", "clit", "dick", "cock", "anus",
  "semen", "sperm", "climax", "bdsm", "milf", "dilf", "rapist", "erotic",
  "stripper", "orgy", "dildo", "horny", "thot", "grooming", "lolicon",
  "shotacon", "hardcore", "softcore", "twerk",
];

const NSFW_EXACT = [
  "cum", "tit", "cok", "dik", "pussi", "balls", "pron", "pedo", "paedo",
  "cp", "jizz", "sex", "rape",
];

/* ------------------------------------------------------------------ */
/* slurs / hate                                                        */
/* ------------------------------------------------------------------ */

const SLURS_SUBSTRING = [
  "nigger", "nigga", "niggr", "sandnigger", "chink", "gook", "wetback",
  "beaner", "kike", "heeb", "raghead", "towelhead", "faggot", "fagot",
  "tranny", "shemale", "ladyboy", "sodomite", "retard", "retarded",
  "mongoloid", "whitetrash", "whitepower", "whitepride", "redskin",
  "gyppo", "pikey", "supremacist", "ethniccleansing", "gaschamber",
  "neonazi", "heilhitler", "siegheil", "kluxklan", "holocaustjoke",
  "killyourself", "killallthe", "deathtoall",
];

const SLURS_WORD = [
  "gypsy", "queerbait", "spastic", "cripple", "midget", "goyim", "lynching",
  "genocide", "hitler", "nazi", "terrorist", "jihadi", "taliban", "alqaeda",
  "boong", "honky",
];

const SLURS_EXACT = [
  "nig", "fag", "abo", "jap", "yid", "kys", "kkk", "spaz", "curry", "camel",
  "negro", "coon", "spic", "dyke", "homo", "paki", "squaw", "injun",
  "cracker", "zhid", "isis",
];

/* ------------------------------------------------------------------ */
/* reserved (platform / infra / routes)                                */
/* ------------------------------------------------------------------ */

const RESERVED_EXACT = [
  "stackd", "stack", "stackdapp", "official", "team", "staff", "support",
  "helpdesk", "help", "admin", "admins", "administrator", "root", "superuser",
  "sysadmin", "moderator", "moderators", "mod", "mods", "owner", "founder",
  "ceo", "system", "systems", "operator", "security", "abuse", "trust",
  "billing", "payments", "payment", "refund", "verify", "verified",
  "verification", "authentication", "auth", "login", "signin", "signup",
  "register", "password", "account", "accounts", "api", "app", "www", "mail",
  "email", "smtp", "ftp", "cdn", "static", "assets", "docs", "doc", "blog",
  "status", "dev", "test", "staging", "preview", "null", "undefined", "none",
  "anonymous", "anon", "guest", "user", "users", "me", "self", "everyone",
  "here", "all", "bot", "bots", "dashboard", "profile", "profiles",
  "settings", "leaderboard", "seasons", "rooms", "room", "friends", "groups",
  "circles", "achievements", "vault", "privacy", "terms", "legal", "contact",
  "about", "philosophy", "catalog", "sitemap", "robots", "wellknown", "mcp",
  "sdk", "webhook", "webhooks",
];

/* ------------------------------------------------------------------ */
/* impersonation (staff/system identities, brands)                     */
/* ------------------------------------------------------------------ */

const IMPERSONATION_SUBSTRING = [
  "stackdteam", "stackdstaff", "stackdsupport", "stackdadmin", "stackdmod",
  "stackdofficial", "officialstackd", "teamstackd", "systemalert",
  "securityalert", "securityteam", "verifyaccount", "trustandsafety",
  "officialsupport", "supportteam", "adminteam", "helpdeskteam",
  "moderatorteam", "freerobux", "freevbucks", "cryptogiveaway", "cryptoairdrop",
];

const IMPERSONATION_WORD = [
  "stackdhq", "lovabledev", "openai", "chatgpt", "supabase",
];

export const MODERATION_TERMS: readonly ModerationTerm[] = [
  ...build("profanity", "substring", PROFANITY_SUBSTRING),
  ...build("profanity", "word", PROFANITY_WORD),
  ...build("profanity", "exact", PROFANITY_EXACT),
  ...build("nsfw", "substring", NSFW_SUBSTRING),
  ...build("nsfw", "word", NSFW_WORD),
  ...build("nsfw", "exact", NSFW_EXACT),
  ...build("slurs", "substring", SLURS_SUBSTRING),
  ...build("slurs", "word", SLURS_WORD),
  ...build("slurs", "exact", SLURS_EXACT),
  ...build("reserved", "exact", RESERVED_EXACT),
  ...build("impersonation", "substring", IMPERSONATION_SUBSTRING),
  ...build("impersonation", "word", IMPERSONATION_WORD),
];

/**
 * Reviewed legitimate names that the matcher would otherwise flag. Entries are
 * canonical usernames (lowercase, separators removed) and bypass moderation
 * entirely — format rules and uniqueness still apply.
 */
export const ALLOWLIST: readonly string[] = [
  "cassandra", "scunthorpe", "penistone", "lightwater", "shitake",
  "matsushita", "assange", "classic", "grasshopper", "bassist", "compass",
  "cocktail", "cockburn", "hancock", "peacock", "woodcock", "titan",
  "titanium", "constitution", "analysis", "analyst", "canal", "arsenal",
  "sussex", "middlesex", "essex", "therapist", "specialist", "assassin",
  "bumblebee", "dickens", "dickinson", "curryhouse", "camelot", "cameldev",
  "abolition", "japan", "japanese", "abode", "cocoa", "documentation",
];
