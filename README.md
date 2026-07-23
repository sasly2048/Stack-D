<h1 align="center"> Stack'D </h1>

<p align="center">
  <img src="src\assets\logo.png"
       alt="Stack'D Logo"
       width="300">
</p>
<p align="center">
  <strong>Presence is the new luxury.</strong>
</p>

<p align="center">
  <strong>Stack'D</strong> is a real-time, multiplayer phone-down focus protocol.
</p>

<p align="center">
  Join a shared room, stack your phones, and stay focused together.
  Every tilt, lift, shake, or screen wake breaks the stack for everyone—in real time.
</p>

<div align="center">
   
![Last Commit](https://img.shields.io/github/last-commit/sasly2048/Stack-D)
![License](https://img.shields.io/badge/License-MIT-informational.svg)
![Made with Love](https://img.shields.io/badge/Made%20with-%E2%9D%A4-red)
![Platform](https://img.shields.io/badge/Platform-Windows%2011-0078D4?logo=windows11)
</div>


🔗 **Live:** [stack-d.lovable.app](https://stack-d.lovable.app)

---

## How it works

1. **Start or join a session** — a host spins up a room and gets a 6-character code (ambiguity-free alphabet, no `0/O` or `1/I`); others join with that code.
2. **Stack your phones** — once everyone's in, the room enters a synced focus block with a shared countdown.
3. **Sensors enforce presence** — device orientation and motion are watched continuously:
   - **Tilt** — sustained device angle change beyond a threshold
   - **Lift** — sharp orientation delta (phone picked up)
   - **Shake** — accelerometer magnitude spike
   - **Tab hidden** — app backgrounded / switched away from
   - **Wake lock lost** — screen allowed to sleep
4. **Breaches are scored, not just flagged** — minor and severe breaches carry different penalties, and a grace window absorbs brief slips before an "abandonment" penalty kicks in.
5. **Focus becomes a score, then XP** — a deterministic scoring engine converts time-on-task and breach history into a 0–100 focus score, a tier (*Flow State* → *Protocol Compromised*), and XP for the room leaderboard.

## Focus scoring model

```
S_focus = max(0, min(100, (T_focus / T_target) × 100 − Σ P_breach))
XP      = floor(S_focus × (T_focus / 60) × M_tier)
```

| Tier | Score range | XP multiplier |
|---|---|---|
| Flow State | 95–100 | 1.5× |
| Pristine Focus | 85–94 | 1.0× |
| Steady Ambient | 70–84 | 0.5× |
| Fragmented Attention | 40–69 | 0.0× |
| Protocol Compromised | 0–39 | 0.0× |

Scoring logic lives in [`src/lib/focus-score.ts`](src/lib/focus-score.ts) as pure, dependency-free functions — durations are carried at millisecond precision through the calculation and only floored at the database boundary.

## Features

- 🔑 **6-character room codes** with format validation, rate limiting, and clear retry states on the join flow
- 📡 **Realtime room sync** via Supabase — no host machine required
- 📱 **Multi-signal sensor detection** (orientation, motion, visibility, wake lock) behind a platform-agnostic `SensorAdapter`, so the same hook drives both the web build and the Capacitor mobile shell
- 🏆 **Groups & leaderboard** for tracking accumulated focus time and XP across sessions
- 🌓 **Light/dark theming**, accessible focus states, `aria-live` status regions on interactive flows
- 🔐 **Auth-gated rooms** with Supabase auth middleware and session handling

## Tech stack

| Layer | Technology |
|---|---|
| Framework | [TanStack Start](https://tanstack.com/start) (React 19) + [TanStack Router](https://tanstack.com/router) |
| Styling | Tailwind CSS v4, shadcn/ui (Radix primitives) |
| Backend / Realtime / Auth | Supabase |
| Mobile shell | Capacitor (`@capacitor/core`, `@capacitor/motion`) |
| Forms & validation | React Hook Form + Zod |
| Tooling | Vite, ESLint, Prettier, TypeScript |
| Package manager | Bun |

## Project structure

```
src/
├── components/          # UI components (nav, code input, theme toggle, ui/ = shadcn primitives)
├── hooks/
│   ├── use-auth.ts      # Auth state
│   ├── use-mobile.tsx   # Responsive breakpoint hook
│   └── use-sensors.ts   # Multi-signal breach detection (orientation/motion/visibility/wake-lock)
├── integrations/
│   ├── lovable/         # Lovable platform integration
│   └── supabase/        # Supabase client, auth middleware/attacher, generated types
├── lib/
│   ├── focus-score.ts   # Scoring engine (pure functions)
│   ├── room.ts           # Room code generation, duration formatting
│   ├── room.functions.ts # Server functions for room validation/lifecycle
│   ├── sensor-adapter.ts # Web/Capacitor sensor abstraction
│   ├── invite-channel.ts # Room invite/broadcast logic
│   └── finalize-queue.ts # Session finalization
└── routes/
    ├── index.tsx                     # Landing page
    ├── auth.tsx                      # Auth flow
    └── _authenticated/
        ├── start.tsx                 # Create a session
        ├── room.$code.tsx            # Active focus room
        ├── groups.tsx                # Groups management
        ├── dashboard.tsx              # User dashboard
        └── leaderboard.tsx            # XP / focus-time leaderboard
```

## Getting started

**Prerequisites:** [Bun](https://bun.sh)

```bash
# Install dependencies
bun install

# Start the dev server
bun run dev

# Type-check + lint
bun run lint

# Production build
bun run build
```

You'll need a Supabase project configured via environment variables (see `.env`) for auth and realtime room sync to work locally.

## License

MIT — see [LICENSE](LICENSE).
