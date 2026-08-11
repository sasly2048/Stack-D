/**
 * Versioned moderation lists for usernames.
 *
 * Bump LIST_VERSION whenever any list changes. The version is stored alongside
 * nothing user-facing, but it lets us re-audit existing usernames after an
 * update ("everyone approved under v1 should be rechecked under v2").
 *
 * All entries MUST already be in canonical form: lowercase a-z0-9 only, with
 * no separators. `normalizeForModeration()` reduces user input to that same
 * alphabet before matching, so "F_u.c\u2013k" and "fuck" collide.
 */
export const LIST_VERSION = 1;

/** Names only the platform may hold, plus impersonation bait. Exact match. */
export const RESERVED_NAMES: readonly string[] = [
  // platform / brand
  "stackd", "stack", "stackdapp", "stackdteam", "stackdstaff", "stackdsupport",
  "official", "officialstackd", "team", "teamstackd", "staff", "support",
  "helpdesk", "help", "admin", "admins", "administrator", "root", "superuser",
  "sysadmin", "moderator", "moderators", "mod", "mods", "owner", "founder",
  "ceo", "system", "systems", "operator", "security", "securityteam", "abuse",
  "trust", "trustandsafety", "billing", "payments", "payment", "refund",
  "verify", "verified", "verification", "authentication", "auth", "login",
  "signin", "signup", "register", "password", "account", "accounts",
  // reserved routes / infra
  "api", "app", "www", "mail", "email", "smtp", "ftp", "cdn", "static",
  "assets", "docs", "doc", "blog", "status", "dev", "test", "staging",
  "preview", "null", "undefined", "none", "anonymous", "anon", "guest", "user",
  "users", "me", "self", "everyone", "here", "all", "bot", "bots",
  "dashboard", "profile", "profiles", "settings", "leaderboard", "seasons",
  "rooms", "room", "friends", "groups", "circles", "achievements", "vault",
  "privacy", "terms", "legal", "contact", "about", "philosophy", "catalog",
  "sitemap", "robots", "wellknown", "mcp", "sdk", "webhook", "webhooks",
];

/**
 * Substring-matched prohibited terms: profanity, slurs, sexual/NSFW,
 * hateful/violent terms. Matched against the fully normalized form, so
 * leetspeak, repeats and separators are already collapsed by the time we look.
 *
 * Short/ambiguous stems live in EXACT_BLOCKED instead, to avoid the Scunthorpe
 * problem (e.g. "ass" inside "cassandra").
 */
export const BLOCKED_SUBSTRINGS: readonly string[] = [
  // profanity
  "fuck", "fuk", "fck", "fuc", "motherfucker", "shit", "bullshit", "bitch",
  "bastard", "asshole", "arsehole", "dumbass", "jackass", "dickhead",
  "douchebag", "wanker", "bollocks", "bugger", "prick", "twat", "slut",
  "whore", "hoe", "skank", "cunt", "kunt", "knt", "damnit", "goddamn",
  "crap", "piss", "pissoff", "shite", "fanny", "minge", "arsewipe",
  "chutiya", "chutiye", "madarchod", "behenchod", "bhenchod", "bhosdike",
  "gandu", "gaand", "randi", "lund", "lauda", "harami", "kutta", "kamina",
  "puta", "puto", "pendejo", "cabron", "mierda", "coño", "cono", "carajo",
  "merde", "scheisse", "arschloch", "cazzo", "stronzo",
  // sexual / NSFW / solicitation
  "porn", "pron", "pornhub", "xvideos", "xhamster", "onlyfans", "nsfw",
  "hentai", "sex", "sexy", "sexcam", "camgirl", "camboy", "escort", "hooker",
  "prostitute", "brothel", "nude", "nudes", "naked", "boobs", "boob", "tits",
  "titties", "titty", "nipple", "vagina", "pussy", "clit", "penis", "dick",
  "cock", "cocks", "balls", "scrotum", "testicle", "anus", "butthole",
  "rectum", "semen", "cum", "cumshot", "jizz", "sperm", "orgasm", "climax",
  "masturbat", "jerkoff", "handjob", "blowjob", "rimjob", "deepthroat",
  "creampie", "gangbang", "threesome", "bukkake", "fetish", "bdsm", "bondage",
  "milf", "dilf", "incest", "bestiality", "zoophilia", "necrophilia",
  "pedophile", "pedo", "paedo", "lolicon", "shotacon", "childporn", "cp",
  "rape", "rapist", "molest", "grooming", "upskirt", "voyeur", "erotic",
  "hardcore", "softcore", "stripper", "striptease", "orgy", "dildo",
  "buttplug", "vibrator", "fleshlight", "sextape", "sexting", "horny",
  "cumming", "squirt", "twerk", "thot",
  // slurs targeting protected groups
  "nigger", "nigga", "niger", "nigg", "negro", "coon", "chink", "gook",
  "jap", "spic", "wetback", "beaner", "kike", "yid", "heeb", "raghead",
  "towelhead", "sandnigger", "camel", "paki", "curry", "abo", "boong",
  "gypsy", "gyppo", "pikey", "cracker", "honky", "whitetrash", "redskin",
  "injun", "squaw", "faggot", "fagot", "fag", "dyke", "tranny", "shemale",
  "ladyboy", "homo", "queerbait", "sodomite", "retard", "retarded", "spastic",
  "spaz", "mongoloid", "cripple", "midget", "goyim", "zhid",
  // hate / violence / extremism
  "hitler", "adolfhitler", "nazi", "neonazi", "heilhitler", "sieg", "seigheil",
  "kkk", "kluxklan", "whitepower", "whitepride", "genocide", "holocaust",
  "gaschamber", "lynch", "lynching", "ethniccleansing", "supremacist",
  "isis", "alqaeda", "taliban", "jihadi", "terrorist", "bomber", "suicidebomb",
  "schoolshoot", "massshoot", "killyourself", "kysnow", "killall", "gasthe",
  "deathto", "hangthe", "rapeyou", "killyou", "beatyou", "diebitch",
  // scam / impersonation helpers
  "freerobux", "freevbucks", "giveaway", "airdrop", "cryptogift",
];

/**
 * Terms blocked only as a whole username (too short/common to substring-match).
 */
export const EXACT_BLOCKED: readonly string[] = [
  "ass", "arse", "tit", "wank", "bum", "fap", "sex", "cum", "cok", "dik",
  "pussi", "phuk", "phuck", "wtf", "stfu", "gtfo", "milf", "hoe", "hoes",
  "kys", "die", "rape", "nazi", "pedo", "cp", "nig", "fag", "dyk",
];

/** Substrings that make a name read as staff/impersonation when combined. */
export const IMPERSONATION_MARKERS: readonly string[] = [
  "official", "support", "helpdesk", "admin", "moderator", "staffteam",
  "stackdteam", "stackdstaff", "stackdsupport", "stackdadmin", "stackdmod",
  "stackdofficial", "systemalert", "securityalert", "verifyaccount",
];
