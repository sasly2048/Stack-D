# Stack'D — Architecture

## What the product is

A shared focus-session platform: you start or join a focus room, the app watches
for device-level distractions, scores the session, and turns that into
progression you share with friends and circles.

## Core surfaces (always shipped)

| Surface             | Route                                                      | Purpose                                      |
| ------------------- | ---------------------------------------------------------- | -------------------------------------------- |
| Shared Focus Rooms  | `/start`, `/room/$code`                                    | Create/join a room, run the timer            |
| Live Accountability | `/room/$code`                                              | Realtime presence, breach signals, reactions |
| Progression         | `/leaderboard`, `/seasons`, `/achievements`, `/challenges` | XP, tiers, streaks                           |
| Session Insights    | `/dashboard`, `/insights`                                  | History, patterns, recaps                    |
| Friends & Groups    | `/friends`, `/groups`                                      | Social graph, group sprints                  |

Everything else (`/vault`, `/dna`, `/replay`, `/capsule`, `/sdk`, `/webhooks`,
`/companion`, `/feed`, `/timeline`, `/trust`, `/partners`, `/integrations`,
`/catalog`) is an **experiment**. It stays in the repo and remains reachable by
URL, but is hidden from navigation and the command palette unless labs are on.

Toggle labs with `?labs=1` (persists in `localStorage`), clear with `?labs=0`.
Source of truth: `src/lib/feature-flags.ts`.

## Stack

- **TanStack Start v1** (React 19, Vite 7) — file-based routes in `src/routes`.
- **Server logic**: `createServerFn` in `src/lib/*.functions.ts`; raw HTTP under
  `src/routes/api/`, public/webhook endpoints under `src/routes/api/public/`.
- **Data**: Supabase (Postgres + RLS + realtime). Migrations in `supabase/`.
- **State**: TanStack Query for server state; local component state elsewhere.
- **Styling**: Tailwind v4 tokens in `src/styles.css`, shadcn/ui primitives in
  `src/components/ui`, bespoke motion in `src/components/fx`.

## Data flow of a focus session

```text
/start ──► create room (server fn, RLS as user)
       ──► /room/$code
             ├─ useSensors  : visibility / motion / wake-lock → breach events
             ├─ focus-score : pure scoring engine (no React, no DB)
             └─ finalize_focus_session (RPC, atomic)
                   ├─ writes focus_history row
                   ├─ increments profile XP + streak
                   └─ emits activity for feed/leaderboard
```

Clients never INSERT into `focus_history` or UPDATE XP columns directly — RLS
blocks it; the `finalize_focus_session` RPC is the only writer. This keeps score
integrity server-side.

## Security model

- Every public table has explicit `GRANT`s plus RLS policies scoped to
  `authenticated`.
- Privilege-sensitive logic (`claim_daily_reward`, `prestige_up`,
  `dispatch_group_sprint`, `finalize_focus_session`) runs as
  `SECURITY DEFINER` with `search_path = public` and no `anon` execute rights.
- Auth attempts, rate limits, and alerts are persisted (`auth_attempts`,
  `rate_limits`, `auth_alerts`) rather than held in process memory, so limits
  survive across serverless instances.

## Quality gates

`.github/workflows/ci.yml` runs on every push and PR:

**Job `verify`**

1. `bun run lint` — ESLint
2. `bun run typecheck` — `tsgo --noEmit`
3. `bun run test` — Vitest unit suite (`tests/unit`)
4. `bun run build` — production build

**Job `e2e`** (after `verify`)

5. Boots the dev server, then `bun run test:e2e` — Playwright smoke suite
   (`tests/e2e/run.py`): landing render, room-code validation (malformed and
   unknown codes), the `_authenticated` gate, Apple/Email provider buttons,
   public routes, the core-vs-lab nav split, and console-error hygiene.
   Screenshots upload as artifacts on failure.

Visual regression scripts live in `tests/visual/` (`bun run test:visual`), run
manually against a local dev server.

## Conventions

- Colour, spacing, and shadow values are semantic tokens — never hardcoded.
- `*.functions.ts` files are thin wrappers: imports, types, and server function
  declarations only. Helpers live in `*.server.ts` or plain modules.
- Pure logic (scoring, flags, formatting) is unit-testable and free of React.
