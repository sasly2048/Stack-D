/**
 * Emotional copy catalog + narrative progression titles. Prefer these strings
 * over ad-hoc toast text so the companion voice stays consistent.
 *
 * Voice: warm, calm, second-person. Celebrate effort over perfection.
 * Never punish. Never blame the user for a system error.
 */
export const copy = {
  session: {
    startedTitle: "Session begun.",
    startedBody: "The room is quiet. You're here.",
    completedTitle: "Session held.",
    completedBody: (mins: number) => `${mins} minutes of yours, kept.`,
    breach: "That's okay. Come back when you're ready.",
    breachMinor: (reason: string) => `A small ripple · ${reason.toUpperCase()}`,
    breachSevere: (reason: string) => `The stack broke · ${reason.toUpperCase()}`,
    finalizeRetry: "Saving quietly in the background — we'll catch up when the network does.",
    queuedRetry: "Held safely — we'll sync your result the moment the network returns.",
    queuedOffline: "You're offline. Your session is saved and will land when you're back.",
  },
  realtime: {
    friendAdded: "A new companion joined your circle.",
    sessionComplete: (xp: number) => `Session held · +${xp} XP kept.`,
    groupSprint: (name: string, group: string) => `${name} opened a ${group} sprint — join if you're ready.`,
  },
  room: {
    createFail: "The room didn't open. Try once more — we're right behind you.",
    joinFail: "That room isn't reachable right now.",
    joinNotFound: "No room with that code. Check the letters and try again.",
    joinClosed: "That room has already closed for the night.",
    joinRateLimited: (secs: number) => `Take a breath — try again in ${secs}s.`,
  },
  auth: {
    signInFail: "That didn't go through. Nothing lost — try again.",
    signOutOk: "See you soon.",
  },
  friends: {
    requestSent: "Request sent. They'll see it next time they're around.",
    accepted: "You've got a companion.",
    removed: "The tie is loosened. No hard feelings.",
  },
  achievements: {
    unlocked: (name: string) => `${name} — earned.`,
  },
  challenges: {
    completed: (name: string) => `${name} · complete.`,
  },
  generic: {
    saveOk: "Saved.",
    saveFail: "That didn't save. Your work is still here — try once more.",
    networkOffline: "You're offline. We'll hold your progress.",
    networkBack: "Reconnected. Catching you up.",
    comingSoon: "Almost ready — this one's coming soon.",
  },
  empty: {
    sessions: {
      title: "No sessions yet — and that's a fine place to begin.",
      body: "Your first focus block will land here. Start when you're ready.",
    },
    friends: {
      title: "Quiet in here.",
      body: "Add someone you trust to keep pace with.",
    },
    achievements: {
      title: "The wall is bare.",
      body: "Every session writes on it. Yours are coming.",
    },
    feed: {
      title: "Nothing to catch up on.",
      body: "When your circle moves, it'll show up here.",
    },
    vault: {
      title: "An empty shelf.",
      body: "Save a note, a link, a thought — anything you'd want back later.",
    },
  },
} as const;

/**
 * Narrative progression — every user passes through the same six chapters.
 * Chapters are derived from lifetime XP so they map cleanly onto the same
 * signal the Nav tier system already reads.
 */
export interface NarrativeChapter {
  key: "arrival" | "kindling" | "cadence" | "keeper" | "steward" | "elder";
  title: string;
  subtitle: string;
  minXp: number;
}

export const NARRATIVE_CHAPTERS: NarrativeChapter[] = [
  { key: "arrival",  title: "The Arrival",  subtitle: "You showed up. That was the hard part.",          minXp: 0 },
  { key: "kindling", title: "The Kindling", subtitle: "A first small fire — held long enough to warm.", minXp: 150 },
  { key: "cadence",  title: "The Cadence",  subtitle: "You've found a rhythm. It's yours now.",         minXp: 600 },
  { key: "keeper",   title: "The Keeper",   subtitle: "You keep the silence. Others feel it.",          minXp: 1500 },
  { key: "steward",  title: "The Steward",  subtitle: "You hold space for the room, not just yourself.",minXp: 3500 },
  { key: "elder",    title: "The Elder",    subtitle: "You are what a full attention looks like.",      minXp: 7500 },
];

export function chapterForXp(xp: number): NarrativeChapter {
  let current = NARRATIVE_CHAPTERS[0];
  for (const c of NARRATIVE_CHAPTERS) if (xp >= c.minXp) current = c;
  return current;
}

export function nextChapter(xp: number): NarrativeChapter | null {
  return NARRATIVE_CHAPTERS.find((c) => c.minXp > xp) ?? null;
}
