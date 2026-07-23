This is a multi-phase, multi-week evolution of Stack'd. I'll ship it in focused waves rather than one monolithic change, so each wave lands stable, reviewable, and reversible. Below is the wave plan; confirm and I'll start executing Wave 1 immediately.

## Wave 1 — Foundation & Immediate Wins (highest ROI)

**1.1 Repository hygiene**
- Add `.env.example` mirroring `.env` keys (no values).
- Verify `.gitignore` covers `.env`, `.env.local`, `.lovable/`, `tests/visual/screenshots/`.
- Audit `VITE_*` exposure — confirm nothing secret is client-shipped.

**1.2 Progressive Navigation**
- Introduce `useNavTier()` hook reading real signals from `profiles` (lifetime_xp, current_focus_streak, total_sessions).
- Three tiers: Starter / Intermediate / Advanced, plus Power User Mode toggle stored in `profiles.settings` JSON.
- Refactor `src/components/nav.tsx` and Cmd+K to filter by tier; locked items show a subtle "unlocks at X" hint on hover instead of disappearing entirely.

**1.3 Emotional Copy Pass**
- Central `src/lib/copy.ts` with a `msg()` catalog: toasts, empty states, errors, session events.
- Sweep `toast.error/success` sites (rooms, sessions, friends, achievements, auth) to use the catalog.
- Rewrite `EmptyState` defaults + top ~20 highest-traffic strings first.

**1.4 Atlas — named AI companion**
- Rename `companion.functions.ts` surface to Atlas (keep server-fn names for compatibility; add persona layer in prompt).
- Create `<AtlasWhisper />` — a small dismissible presence card that surfaces on Dashboard, Insights, and post-session, using existing `proactive-ai.functions.ts` + `recommendNextSession`.
- No new page needed; Atlas rides on existing routes.

**1.5 Session Completion Signature Moment**
- New `<SessionCeremony />` overlay triggered by existing `finalize_focus_session` result.
- Sequence: timer dissolve → ambient glow expand → XP `NumberTicker` count-up → achievements/milestones slide-in → streak pulse → Atlas one-liner → recap CTA.
- Reuses existing `SessionRecapCard`, `haptic()`, achievement + challenge evaluation results.
- Respects reduced-motion and Low Power Mode.

## Wave 2 — Product Polish

**2.1 Narrative titles** — Map existing `titles`/`user_titles` rows to Explorer→Legend ladder via a `rank_from_xp()` helper. Surface current rank on profile + Atlas greeting.

**2.2 Design system tokens** — Extend `src/styles.css`:
- Motion: `--motion-spring-*`, `--ease-*`, `--dur-fast/base/slow`.
- Visual: `--blur-*`, `--elev-*`, `--glow-ember-*`.
- Audio: small `src/lib/sounds.ts` with 4 short WebAudio-synthesized cues (no asset bloat) + user mute pref.

**2.3 Ambient Atlas** — Extend `<AtlasWhisper />` to Achievements + Rooms lobby with context-aware copy.

## Wave 3 — Architecture

**3.1 Feature folder migration** — Introduce `src/features/{ai,rooms,social,analytics,replay,vault,achievements}/` and *move* (not duplicate) related components/functions. Update imports. Done incrementally, one feature per commit.

**3.2 Performance** — `bun run build` bundle report → route-level `React.lazy` for heavy pages (`/replay`, `/dna`, `/insights`, `/vault`); already lazy-load `Meteors`, `DottedMap` — extend to `Particles`, `OrbitingCircles`.

**3.3 Low Power Mode** — `useLowPower()` hook combining `matchMedia('(prefers-reduced-motion)')`, `navigator.getBattery()`, `navigator.deviceMemory`. FX components read it and no-op.

## Wave 4 — Reliability

**4.1 Tests** — Vitest units for `focus-score.ts`, XP/streak math, rank ladder; Playwright E2E for onboarding → join room → session → recap.

**4.2 Offline** — IndexedDB-backed session state via `idb-keyval`; extend existing `finalize-queue.ts` for durable persistence + resume-on-reload.

**4.3 Observability** — Sentry (`@sentry/react`) + PostHog (`posthog-js`) behind env flags; wire to existing `error-capture.ts`.

## Wave 5 — Ecosystem

Only after Waves 1–4 land: Google Calendar (OAuth via connectors), Notion export, Discord webhook preset, Chrome extension shell. Each is its own scoped project.

---

## Technical notes

- All new server functions follow existing `createServerFn + requireSupabaseAuth` pattern.
- New tables (if any) come with GRANT + RLS in same migration.
- No secrets touched; `LOVABLE_API_KEY` reused for Atlas.
- View Transitions and `haptic()` already present — reuse, don't duplicate.

---

**Recommendation:** approve and I ship Wave 1 end-to-end this turn (repo hygiene, progressive nav, copy catalog + sweep of top strings, Atlas whisper, session ceremony). Waves 2–5 follow on your say-so.