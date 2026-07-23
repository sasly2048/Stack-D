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

1. **Start or join a room** with a target duration and enforcement mode (`gentle` or `absolute`).
2. **Sensors arm** on session start — orientation, motion, tab visibility, and Screen Wake Lock are all monitored through a single adapter (`use-sensors.ts`) so the same logic runs on web and the future Capacitor build.
3. **Breach a rule** (tilt past threshold, pick the phone up, shake it, switch tabs, lose the wake lock) and it's logged with a severity — minor or severe.
4. **Session ends** → the client computes a provisional score locally for instant feedback, then calls a Postgres RPC (`finalize_focus_session`) that **independently recomputes** duration, breach count, and XP server-side and takes the *lower* of client vs. server XP. A modified client can only shortchange itself, never inflate its score.
5. **Result lands** with a full cinematic completion sequence — animated XP count-up, tier reveal, streak, unlocked achievements — that fires the moment your session finalizes, whether that's instant or synced later from an offline queue.

## Scoring model

```
S_focus = max(0, min(100, (T_focus / T_target) * 100 − Σ P_breach))
XP      = floor(S_focus * (T_focus / 60) * M_tier)
```

| Tier | Score range | XP multiplier |
|---|---|---|
| Flow State | 95–100 | 1.5× |
| Pristine Focus | 85–94 | 1.0× |
| Steady Ambient | 70–84 | 0.5× |
| Fragmented Attention | 40–69 | 0× |
| Protocol Compromised | 0–39 | 0× |

Minor breaches cost 10 points, severe breaches cost 40. Abandoning past a 15-second grace window after a severe breach adds a continuous penalty. The pure scoring function lives in `src/lib/focus-score.ts` with no React or DB dependencies, so it's independently testable.

## Features

**Core loop** — rooms, real-time presence, live activity rail, session workspace, ambient soundscapes, QR-code room invites, floating persistent timer.

**Progression & identity** — XP, streaks, tiers, achievements, challenges, seasons, prestige, narrative rank titles, profile cards, DNA (behavioral pattern breakdown), memory vault, session replay, time capsules.

**Social** — friends, groups/circles, activity feed, leaderboards, mentor relationships, live session reactions, shared goal bars for group rooms.

**Companion** — **Atlas**, an ambient AI coach that surfaces context-aware, data-grounded recommendations (next session length, best focus hour, burnout risk) as a small dismissible card on Dashboard and Insights — not a chatbot you have to seek out, though a full conversational companion page exists at `/companion` for direct Q&A. Grounded in real session history via a system-prompt guardrail against inventing statistics.

**Trust & safety** — user reporting, moderation queue, blocking, room moderators, IP + device-fingerprint rate limiting on auth, CAPTCHA (Turnstile) on suspicious activity.

**Progressive navigation** — the nav isn't flat. Routes are gated behind Starter / Intermediate / Advanced tiers computed from real usage (lifetime XP, streak, session count), not a settings toggle, with a power-user override for anyone who wants everything immediately (`use-nav-tier.ts`).

**Low Power Mode** — trims particles, meteors, and parallax FX; auto-enables on `prefers-reduced-motion` or low battery.

**Ecosystem** — an `/integrations` directory cataloging what's shipped (Webhooks, TypeScript SDK, MCP server) vs. what's planned (Calendar, Notion, Discord, Slack, Raycast), each honestly status-labeled.

**Extensibility** —
- **Webhooks** — subscribe to session/room events, with delivery logs and retry.
- **Public TypeScript SDK** (`@stackd/sdk`) — zero-dependency client for webhook signature verification.
- **MCP server** — Stack'D exposes its own [Model Context Protocol](https://modelcontextprotocol.io) endpoint (`/mcp`) so agents like Claude or Cursor can read a user's focus history, groups, and profile directly.

## Tech stack

- **Frontend** — React 19, TanStack Start + TanStack Router (file-based, SSR), TypeScript, Tailwind CSS v4, Framer-adjacent motion via GSAP + custom FX primitives, Lenis smooth scroll, shadcn/ui + Radix primitives.
- **Backend** — Supabase (Postgres, Auth, Realtime), ~40 tables with row-level security enabled on every one, SECURITY DEFINER RPCs for anything score/XP/reward-adjacent.
- **Mobile** — Capacitor core + motion plugin are integrated as dependencies; the native Android/iOS project shell is not yet scaffolded (web + PWA-ready today).
- **Other** — Zod validation throughout server functions, jsPDF for exports, QR code generation, Playwright for visual regression testing.

## Getting started

```bash
git clone https://github.com/sasly2048/Stack-D.git
cd Stack-D
bun install        # or npm install

cp .env.example .env
# fill in your Supabase project URL + publishable key

bun run dev         # vite dev
```

Other scripts: `build`, `build:dev`, `preview`, `lint`, `format`.

Database schema and RLS policies live in `supabase/migrations/` — apply them to a fresh Supabase project via the Supabase CLI or dashboard SQL editor.

## Project structure

```
src/
  routes/_authenticated/   # one file per top-level page (room, dashboard, achievements, …)
  lib/*.functions.ts       # server functions — one module per domain (auth, rooms, social, ai, …)
  components/               # UI, including fx/ (motion primitives) and rooms/ (session UI)
  hooks/                    # use-sensors, use-nav-tier, use-low-power, …
  integrations/supabase/    # typed client + auth middleware
supabase/migrations/        # full schema + RLS history, chronological
tests/visual/                # Playwright screenshot + geometry regression suite
```

## Status

Actively developed. Core loop, scoring integrity, RLS coverage, and progressive disclosure are solid. Known gaps: no native mobile shell yet (Android planned first, ARM64 dev machine has no iOS build path), and automated test coverage beyond visual regression is still thin relative to the reward-critical server logic.

## License

MIT — see [LICENSE](./LICENSE). 
