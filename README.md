# Stack'D — Internal

> **PROPRIETARY AND CONFIDENTIAL.** Copyright (c) 2026 Raghavendra G. All rights
> reserved. This repository is private. Do not redistribute, publish, or share
> any part of this source, its schema, or its documentation. See `NOTICE.txt`.

Real-time, multiplayer phone-down focus protocol. Join a shared room, stack your
phones, stay focused together. Every tilt, lift, shake, or screen wake breaks the
stack for everyone, in real time.

**Live:** [stackd.raghav.studio](https://stackd.raghav.studio/)

## How it works

1. Start or join a room with a target duration and enforcement mode (`gentle` or `absolute`).
2. Sensors arm on session start — orientation, motion, tab visibility, and Screen Wake Lock are monitored through a single adapter (`use-sensors.ts`) so the same logic runs on web and the native build.
3. Breaching a rule (tilt past threshold, pick the phone up, shake it, switch tabs, lose the wake lock) logs it with a severity: minor or severe.
4. Session ends → the client computes a provisional score locally for instant feedback, then calls a Postgres RPC (`finalize_focus_session`) that independently recomputes duration, breach count, and XP server-side and takes the _lower_ of client vs. server XP. A modified client can only shortchange itself, never inflate its score.
5. Result lands with the completion sequence — animated XP count-up, tier reveal, streak, unlocked achievements — firing the moment the session finalizes, whether instantly or synced later from the offline queue.

## Scoring model

```
S_focus = max(0, min(100, (T_focus / T_target) * 100 − Σ P_breach))
XP      = floor(S_focus * (T_focus / 60) * M_tier)
```

| Tier                 | Score range | XP multiplier |
| -------------------- | ----------- | ------------- |
| Flow State           | 95–100      | 1.5×          |
| Pristine Focus       | 85–94       | 1.0×          |
| Steady Ambient       | 70–84       | 0.5×          |
| Fragmented Attention | 40–69       | 0×            |
| Protocol Compromised | 0–39        | 0×            |

Minor breaches cost 10 points, severe breaches cost 40. Abandoning past a
15-second grace window after a severe breach adds a continuous penalty. The pure
scoring function lives in `src/lib/focus-score.ts` with no React or DB
dependencies, so it's independently testable.

## Surface area

- **Core loop** — rooms, real-time presence, live activity rail, session workspace, ambient soundscapes, QR-code room invites, floating persistent timer.
- **Progression & identity** — XP, streaks, tiers, achievements, challenges, seasons, prestige, narrative rank titles, profile cards, DNA (behavioral pattern breakdown), memory vault, session replay, time capsules.
- **Social** — friends, groups/circles, activity feed, leaderboards, mentor relationships, live session reactions, shared goal bars for group rooms.
- **Companion** — Atlas, an ambient AI coach surfacing context-aware, data-grounded recommendations (next session length, best focus hour, burnout risk) as a dismissible card on Dashboard and Insights, plus a conversational page at `/companion`. Grounded in real session history via a system-prompt guardrail against inventing statistics.
- **Trust & safety** — user reporting, moderation queue, blocking, room moderators, IP + device-fingerprint rate limiting on auth, CAPTCHA (Turnstile) on suspicious activity.
- **Progressive navigation** — routes gated behind Starter / Intermediate / Advanced tiers computed from real usage (lifetime XP, streak, session count), not a settings toggle, with a power-user override (`use-nav-tier.ts`).
- **Low Power Mode** — trims particles, meteors, and parallax FX; auto-enables on `prefers-reduced-motion` or low battery.
- **Extensibility** — webhooks with delivery logs and retry; internal TypeScript SDK for webhook signature verification; an MCP endpoint (`/mcp`) exposing focus history, groups, and profile to agent clients.

## Tech stack

| Layer          | Technology                                                                                              |
| -------------- | ------------------------------------------------------------------------------------------------------- |
| **Frontend**   | React 19, TanStack Start, TanStack Router, TypeScript, Tailwind CSS v4, GSAP, Lenis, shadcn/ui, Radix UI |
| **Backend**    | Supabase (Postgres, Auth, Realtime), Row-Level Security, SECURITY DEFINER PostgreSQL RPCs                |
| **Mobile**     | Native Android (Kotlin/Compose, in progress); Capacitor motion plugin on web                             |
| **Validation** | Zod                                                                                                     |
| **Utilities**  | jsPDF, QR Code generation                                                                                |
| **Testing**    | Vitest, Playwright visual regression, Playwright E2E smoke                                              |

## Local setup

```bash
bun install        # or npm install

cp .env.example .env
# fill in Supabase project URL + publishable key

bun run dev
```

Other scripts: `build`, `build:dev`, `preview`, `lint`, `format`, `typecheck`,
`test`, `test:e2e`, `test:visual`, `analyze`.

Database schema and RLS policies live in `supabase/migrations/`.

**Never commit the Supabase `service_role` key.** Only the publishable/anon key
belongs in `.env` or any client bundle — safety comes from RLS, not from
key secrecy. Service-role flows go through Supabase Edge Functions.

## Project structure

```
src/
├── routes/_authenticated/      # Application pages
├── components/                 # Shared UI components
├── hooks/                      # Custom hooks
├── lib/                        # Shared utilities and server functions
├── integrations/               # Supabase client and middleware
└── ...

android/                        # Native Kotlin/Compose app (branch: android-phase1)

supabase/
├── migrations/                 # Database schema and RLS history
└── functions/                  # Edge Functions (service-role only)

tests/
├── e2e/                        # Playwright smoke suite
└── visual/                     # Playwright visual regression suite
```

## Status

Actively developed. Core loop, scoring integrity, RLS coverage, and progressive
disclosure are solid. Native Android is mid-build on `android-phase1`; iOS has no
build path on the current ARM64 dev machine.
