## Goal

Ship six features that make Stack'D feel social, personal, and shareable — building on what already exists rather than adding parallel systems.

Verified current state: `seasons` / `season_participants` tables exist but the table is empty and nothing awards season XP; `LiveActivityRail` already streams room events in realtime; session completion dispatches a minimal ceremony (duration/xp/score/tier only); a "Productivity DNA" archetype exists in `src/lib/dna.functions.ts` but is hidden behind the labs flag; there is no Wrapped surface and no milestone system.

---

## 1. Stack Wrapped

New core route `/wrapped` (rolling 12 months if the calendar year is young).

Stats computed server-side from `focus_history`, `profiles`, `user_achievements`, `participants`:
- total hours held, total XP, sessions count
- longest single session, longest streak
- most productive weekday + peak hour
- top collaborator (most co-attended rooms)
- global percentile by lifetime XP

Presentation: scene-by-scene scroll reusing the landing `Scene` language, `NumberTicker`, oversized numerals, ember accents.

Shareable image: rendered client-side onto a 1080×1350 canvas from the same data, with Download + Web Share API (download fallback on desktop). No new dependency.

## 2. Live Presence

Upgrade the room lobby/session header rather than adding a panel.

Per-user states derived from `participants` (`last_heartbeat`, `breached`, `left_at`) plus the realtime channel:

- **Idle** — present, not ready
- **Ready** — signaled ready
- **Stacking** — session running, phone held
- **Broke stack** — breached
- **Disconnected** — no heartbeat for >45s while the session is live, so a dropped connection reads differently from an intentional leave

Ready flow: a Ready toggle in the lobby writes a `ready` room event. When every present participant is ready, the roster resolves to a full-width **Everyone Ready → Starting…** beat, then a synchronized 3-2-1 countdown driven off `started_at` so all clients agree. Haptics on each tick.

Activity rail gets animated entry for new events plus `disconnected` / `reconnected` / `ready` kinds.

## 3. Rich End-of-Session Summary

A new server function called right after `finalize_focus_session` returns the full result set; `SessionCeremony` becomes a multi-beat overlay in this exact order:

1. **Focus Score** — tier ring reveal
2. **XP** — counter animates up from 0 with easing (never snaps to the final number), tier multiplier shown
3. **Level / Prestige progress** — bar fills toward the next threshold
4. **Achievements** — newly unlocked badges (already returned by `evaluate_achievements`)
5. **Rank change** — global leaderboard delta, ▲/▼ with the position moved
6. **Friends finished** — friends who completed a session today
7. **Continue** — explicit dismiss button (tap-anywhere and Escape still work)

Reduced-motion collapses beats into one static summary. `ResultsCard` on the room page shows the same expanded set.

## 4. Focus Personality

Personality is **composite and dynamic**, not a single fixed label. Each user gets 2–3 stacked traits rendered as `Deep Worker • Night Owl`, recomputed from a rolling 60-day window so it shifts as habits shift.

Trait pool derived from real signals: Deep Worker, Sprint Specialist, Marathoner, Night Owl, Early Bird, Consistency Master, Weekend Warrior, Streak Keeper, Flow Chaser, Comeback Kid.

- Stored on `profiles.productivity_dna` (column exists) and refreshed on session finalize.
- Surfaced on profile header, user hover cards, leaderboard rows, and Wrapped.
- A "your personality shifted" toast when the leading trait changes.
- `/dna` moves out of labs into core as the deep view.

## 5. Weekly Seasons

- Weekly cadence: Monday 00:00 UTC → Sunday 23:59 UTC.
- Seasons are **named, not numbered** — each week draws a themed name from a curated rotating list in the Obsidian-ritualist voice (e.g. "Season of Quiet Hands", "The Long Dark", "Ember Week", "Still Water"), with the ordinal shown as small secondary text.
- A pg_cron job (Monday 00:00 UTC) closes the current season and opens the next; a helper self-heals by creating the current week's season on demand if the job missed.
- `finalize_focus_session` upserts earned XP into `season_participants` for the active season — `profiles.lifetime_xp` stays permanent, season ranking resets weekly.
- `/seasons` gets a live top-10 board, your rank, a countdown to reset, and the previous season's winner. Top finisher receives the season's `reward_title_id`.

## 6. Lifetime Milestones

Permanent, cumulative markers that never reset.

- Thresholds on total focus hours: 100, 200, 300, 500, 1000, 2000 hours (plus lifetime session-count and streak markers).
- Awarded automatically inside the session finalize path, alongside achievements.
- Each unlock produces a **Milestone Card**: an engraved-plate visual with the number, the date reached, and a short line of copy.
- Cards live permanently on the profile in a milestone shelf, appear in the session ceremony when freshly earned, and are included in Wrapped.
- Implemented on the existing `achievements` / `user_achievements` tables with a `milestone` tier, so no new social plumbing is needed.

---

## Technical notes

- New server functions in `src/lib/wrapped.functions.ts`, `src/lib/session-summary.functions.ts`, `src/lib/milestones.functions.ts`; extensions to `seasons.functions.ts` and `dna.functions.ts`. All `requireSupabaseAuth`, called from components or `_authenticated` loaders only.
- Migrations: milestone rows + evaluation function; season auto-rollover function with the named-season list and pg_cron schedule; `finalize_focus_session` updated to write season XP, evaluate milestones, refresh personality, and return the richer summary; new `ready` / `disconnected` / `reconnected` values in the room-event and activity-event kind allowlists. RLS + GRANTs on anything new.
- Presence disconnect detection is client-derived from `last_heartbeat` — no new table, no polling beyond the existing heartbeat.
- Wrapped share image is pure canvas; no new dependency.
- `/wrapped` and `/dna` join `CORE_ROUTES`; nav and command palette updated.
- Visual language unchanged: obsidian surfaces, silver type, ember (#F0A968) accents, existing FX primitives.
