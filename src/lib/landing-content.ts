// Centralized landing page content
// Ensures copy, stats, testimonials, and demo data never drift from UI expectations

export const LANDING_CONTENT = {
  meta: {
    title: "Stack'd — Presence is the new luxury",
    description:
      "Stack'd isn't another focus timer — it's a shared commitment to being present. Stack your phones with friends and hold the silence together.",
    ogDescription:
      "A real-time, multiplayer phone-stacking room. Every tilt, lift and screen wake is shared instantly.",
  },

  hero: {
    eyebrow: "Presence is the new luxury",
    headline: "Stack your phones. Hold the silence together.",
    description:
      "Stack'd isn't another focus timer — it's a shared commitment to being present with the people in front of you.",
    primaryCta: "Join a Session",
    secondaryCta: "Create a Room",
  },

  stats: [
    {
      value: 4.2,
      suffix: "h",
      label: "Avg. daily screen time, 18–34",
      decimals: 1,
    },
    {
      value: 144,
      suffix: "×",
      label: "Phone unlocks per person, per day",
      decimals: 0,
    },
    {
      value: 23,
      suffix: "s",
      label: "Median focus after a notification",
      decimals: 0,
    },
    {
      value: 0,
      suffix: "",
      label: "Notifications during a focus block",
      decimals: 0,
    },
  ],

  signals: [
    {
      key: "Tilt",
      description: "Multi-axis accelerometer drift, reported the instant it happens.",
    },
    {
      key: "Lift",
      description: "The phone leaves the stack. The room knows before you sit back down.",
    },
    {
      key: "Wake",
      description: "Screen-on events break the hold — no quiet exits, no private cheats.",
    },
  ],

  testimonials: [
    {
      quote:
        "We stacked at dinner. Nobody touched their phone for ninety minutes. I forgot what that felt like.",
      author: "Léa",
      location: "Paris",
    },
    {
      quote: "The shared timer is the unlock. It stops being willpower and starts being a game.",
      author: "Devon",
      location: "Brooklyn",
    },
    {
      quote: "My team uses it before every review. The room is sharper. The arguments are better.",
      author: "Priya",
      location: "Bangalore",
    },
    {
      quote: "First Sunday brunch in a year where I remember what my sister actually said.",
      author: "Mateo",
      location: "Mexico City",
    },
    {
      quote: "Chai on the terrace, four friends, phones stacked. Felt like college again.",
      author: "Aarav",
      location: "Mumbai",
    },
    {
      quote: "Our design crit finally had silence in it. The critiques got braver.",
      author: "Meera",
      location: "Delhi",
    },
    {
      quote: "Coded for two hours straight without a single Slack peek. Shipped the migration.",
      author: "Rohan",
      location: "Hyderabad",
    },
    {
      quote: "Sunday lunch with amma and appa. Nobody reached for a phone once.",
      author: "Divya",
      location: "Chennai",
    },
    {
      quote: "Board meeting ran an hour shorter. Nobody scrolled. Nobody drifted.",
      author: "Ingrid",
      location: "Stockholm",
    },
    {
      quote: "I stopped calling it a detox. It's just how we hang out now.",
      author: "Rowan",
      location: "Melbourne",
    },
  ],

  features: [
    {
      title: "Real-time Presence",
      description: "Every motion is shared instantly. No lag, no pretense.",
    },
    {
      title: "Honest Signals",
      description: "Tilt, Lift, Wake. Hardware tells the truth your willpower can't.",
    },
    {
      title: "Shared Commitment",
      description: "When others are in the room, quitting feels different. Softer.",
    },
    {
      title: "No Gamification",
      description: "No points, no streaks, no leaderboards. Just the weight of presence.",
    },
  ],

  faq: [
    {
      question: "How does Stack'd track my phone?",
      answer:
        "Stack'd accesses your device's accelerometer and screen sensors. The data stays on your phone. We only transmit motion vectors and wake events to the shared room.",
    },
    {
      question: "What if someone lifts their phone?",
      answer: "Everyone in the room sees it instantly. No hiding, no sneaking. That's the point.",
    },
    {
      question: "Does Stack'd work offline?",
      answer:
        "No. Stack'd is real-time multiplayer. You and everyone in the room need an active connection.",
    },
    {
      question: "Can I use Stack'd alone?",
      answer:
        "You can create a room, but a room without others is just a timer. Stack'd is designed for presence together.",
    },
  ],

  permissions: {
    accelerometer:
      "Detects when your phone tilts or moves. Used to track whether the stack is stable.",
    wakelock: "Keeps your screen on during a focus session. Prevents the phone from sleeping.",
    notifications: "Lets Stack'd notify the room when your screen turns on or you lift your phone.",
  },

  rewards: {
    title: "Rewards",
    description: "Streaks, XP, achievements. Earned, not given.",
    metrics: [
      { key: "Streak", value: "Days focused together", color: "text-orange-500" },
      {
        key: "XP",
        value: "Minutes of presence",
        color: "text-purple-500",
      },
      {
        key: "Achievements",
        value: "Milestones unlocked",
        color: "text-blue-500",
      },
    ],
  },

  cta: {
    primary: "Start a Room",
    secondary: "Join with Code",
    tagline: "No signup. No email. No friction.",
  },
};

export type LandingContent = typeof LANDING_CONTENT;
