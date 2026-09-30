# Stack'd — Deep Audit (2026-09-30)

**Scope.** Android app (`worktree-android-phase1-work`, 102 Kotlin files / 21.1k lines), backend
(Supabase migrations on `main`, RLS, SECURITY DEFINER RPCs, indexes), the web AI/public routes, and a
CI-equivalent run of the web app on `main` (typecheck, lint, tests). Release APK built with R8.

**Not covered (needs you):** on-device runtime checks — no device was connected. Items marked
**[verify on device]** are static-analysis findings to confirm with the phone plugged in (cold-start
time, jank frames, memory, screen-off breach reliability, release APK smoke test).

**Resume point.** Code is pinned by git tag `snapshot/2026-09-30-pre-audit` → `29c2a5b`
(`git checkout snapshot/2026-09-30-pre-audit`). Full session context is in memory file
`stackd-state-snapshot.md`, loaded automatically in future sessions.

Severity: **P0** broken in production / data loss / security · **P1** functional bug, integrity hole,
major UX · **P2** performance, battery, polish · **P3** nit.

---

## Fix status (updated 2026-09-30, same day)

**Android** (`worktree-android-phase1-work`, PR #10):

| Finding | Status | Commit |
|---|---|---|
| Redundant plans/subscriptions migration | Reverted; branch merged with main | 726745c, ee0ee5c |
| P0 cross-user reads on `profiles` | **Fixed + verified on device** (leaderboard lists 10 members) | 2b6684d |
| Guard dies on back/swipe; breach fire-and-forget; wake lock; channel leaks; queue race; stopService; rotation-vector battery | Fixed | 4262dae |
| Companion history cap, pinned composer; RefreshFailure → signed in | Fixed | 9a1d57d |
| Lazy lists (Leaderboard, Friends, Vault, Moderation); status-bar bleed | Fixed | 876dfca, 2f5ada2 |
| AI TTL cache, POST_NOTIFICATIONS, 11sp labels, icons (2.9 MB → 234 KB), Insights lazy stats, release log strip, launchSingleTop, sign-out cache clear | Fixed | fb260db |
| **New:** Leaderboard / Premium / Seasons / ProfileDetail crashed on open (init-order NPE, pre-existing) | **Fixed + verified** | 9cea004 |

Device smoke test: all 17 menu screens open, **0 fatal crashes**. Cold start (debug) **~2.3 s → ~1.4 s**.

**Web** (`web-ai-hardening`, **PR #18**, not merged): typed JSON errors on all public AI routes (402 credits → 503 `ai_unavailable/credits`, validation → 400), and the migration allowing `ready`/`unready` room events. Tests 259/259.

**Still open:**
- **Needs a decision:** AI metering on the 5 unmetered routes. `withAiBudget` would disable them for free users (allowance 0).
- **Needs you on the phone:** a screen-off lift test (15-minute locked session, then lift) and an airplane-mode breach.
- **Deferred (low value vs risk):**
  - WAV → OGG (no ffmpeg).
  - 834 raw `.dp` → spacing tokens.
  - Shared OkHttp client.
  - Lazy lists for ≤50-row screens (Dashboard history, Feed).
  - `record_breach` `_at` param.
  - Streak copy.
  - Localization.

## Overall verdict

The foundation is **good**: a clean MVVM + manual-DI architecture, a server-authoritative scoring
model, complete RLS coverage (46/46 tables), hot paths indexed, a disciplined type scale, and an
offline finalize queue with owner guards. The web side typechecks and passes 254/254 tests. The
release APK builds with R8 at 7.9 MB.

The weak spots cluster in four places:

1. **A live regression from a Lovable migration.** It locked `profiles` to own-row reads on 09-24, and
   Android still reads `profiles` for other users.
2. **Session-integrity holes.** Breaches can be lost or bypassed through back navigation, a task swipe,
   or going offline.
3. **List rendering.** The app has zero lazy lists, which causes jank on long screens.
4. **AI cost controls.** Routes are unmetered and uncached, and that is what drained the credits.

---

## Top 10 — fix in this order

| # | Sev | Finding | Fix |
|---|-----|---------|-----|
| 1 | P0 | Cross-user data broken: `profiles` is own-row-only since 09-24; Android reads it for leaderboard, friends, feed, groups, partners, moderation, recap rank | Switch ~15 reads to `public_profiles` |
| 2 | P1 | Back press / task swipe mid-session stops the breach guard while the server session keeps running | `BackHandler` confirm while ACTIVE; service owns + records breaches; don't stop service in `onCleared` |
| 3 | P1 | Breach RPC is fire-and-forget — offline breaches never reach the server | Durable breach queue (like FinalizeQueue) |
| 4 | P1 | Companion chat dies after ~15 exchanges (server caps history at 30) | Send `history.takeLast(20)` |
| 5 | P1 | Realtime channels leak (`unsubscribe` launched on an already-cancelled scope) | `removeChannel` under `NonCancellable` / app scope |
| 6 | P1 | 5 of 7 AI routes unmetered + no per-user cache → credit drain, wallet-DoS | `withAiBudget` + rate limit + per-user TTL cache |
| 7 | P1 | Zero `LazyColumn` — every list renders eagerly | Convert list screens to lazy lists |
| 8 | P1 | Offline cold start with expired token dumps user on the Auth screen | Treat `RefreshFailure` as signed-in |
| 9 | P1 | Revert your commit `29c2a5b` — the migration duplicates main's and would re-create its policies | `git revert 29c2a5b`; merge `main` into the branch |
| 10 | P1 | main CI hasn't run for 10+ days (billing lock); lint red on main | Fix billing, merge PR #16 |

---

## 1. UI / UX

- **P1 · Session can be abandoned by accident.** There is no `BackHandler` anywhere in `feature/room/`.
  The system back gesture during an armed session silently pops the room, and with it the guard.
  Users need an "End session? / Keep focusing" confirmation while ACTIVE.
- **P1 · Offline launch = forced sign-out screen.** `MainActivity.kt:60` checks only
  `status is SessionStatus.Authenticated`. `RefreshFailure` (offline plus expired access token) takes the
  signed-out branch, even though the stored session is valid. This contradicts the offline banner and
  the queue design. **[verify on device]**
- **P1 · Companion chat.** The server rejects history over 30 messages
  (`companion.functions.ts: history.max(30)`). Android sends the whole conversation, so after ~15
  exchanges every send returns "unavailable". Also:
  - input and Send scroll away in long chats (not pinned to the bottom);
  - there's no auto-scroll to the newest reply;
  - user bubbles are forced to 80% width (`fillMaxWidth(0.8f)`). The web uses a *max* width
    (`widthIn(max = …)`).
- **P1 · AI failure is invisible.** A 402 "out of credits" surfaces as a generic fallback or a hidden
  card, with nothing logged. The route should return a typed JSON error so the app can say "AI paused".
- **P1 · Lobby "Ready" doesn't sync across devices (web + Android).** On main,
  `restrict_room_event_kinds` sets `_member_ok := ARRAY['reaction']`, so `ready`/`unready` are
  rejected. Needs a migration adding both kinds.
- **P2 · Streak copy contradicts itself.** The same `current_focus_streak` reads "N Sessions" on the
  Dashboard (matching the web) and "Nd" on Insights and Profile. The Daily card right above shows a
  *different* streak (login, "2 days"). Pick one unit and label the two streaks distinctly.
- **P2 · Notifications on Android 13+.** `POST_NOTIFICATIONS` is declared but never requested, so the
  session countdown notification is hidden by default.
- **P3 · Menu double-tap stacks duplicate screens.** `StackdNavHost.kt:93` navigates without
  `launchSingleTop`.
- **P3 · No predictive back.** `enableOnBackInvokedCallback` isn't set (target SDK 35).
- **P3 · Hardcoded strings.** 0 `stringResource` usages, so there's no localization path.

## 2. Memory management

- **P1 · Realtime channel leak (room).** `RoomViewModel.onCleared` (line 906) does
  `viewModelScope.launch { ch.unsubscribe() }`. In lifecycle 2.8, `viewModelScope` is cancelled
  *before* `onCleared` runs, so the unsubscribe never executes. Every room visited stays joined on the
  socket and keeps receiving postgres changes.
- **P1 · Realtime channel leak (global toasts).** `GlobalRealtimeToasts.kt:56` calls the suspend
  `unsubscribe()` in `finally` of a cancelled coroutine. It throws immediately and is swallowed. This
  leaks on every sign-out or user switch. Fix: `withContext(NonCancellable) { realtime.removeChannel(ch) }`.
- **P2 · Hot-path allocation in the foreground service.** The shake window rebuilds a list on every
  accelerometer sample (~16 Hz for the whole session). Use a fixed ring buffer.
- **P2 · Launcher icons.** They're ~968 KB PNGs ×3, and one is density-less
  (`drawable/ic_launcher_foreground.png`, treated as mdpi and upscaled). That costs APK size and decode
  memory. Use an adaptive vector icon.
- **P3 · Three OkHttp clients.** Supabase, `AuthRepository.http` and `AiRepository.http` each have
  their own pool and dispatcher threads. Share one client.
- **P3 · Cache clearing is inconsistent.** Sign-out via `AuthViewModel:205` doesn't clear `MemoryCache`
  (the Profile path does). Keys are user-scoped, so nothing leaks across accounts, but stale data is
  retained. Centralize the clear in `AuthRepository.signOut`.
- **OK:** ExoPlayer is released in `onDispose`, the sensor listeners unregister, and `MemoryCache` is
  bounded by screen count.

## 3. Fluidity / performance

- **P1 · No lazy lists anywhere.** 0 `LazyColumn`/`LazyRow`/`LazyGrid`. 29 screens render complete lists
  inside `Column + verticalScroll`: session history, feed, leaderboard (100), timeline, friends, vault
  (200), achievements, groups, moderation (200). Every item composes and measures on first frame, and
  memory grows with the data. This is the single biggest jank source.
- **P2 · Insights recomputes analytics on every recomposition.** `InsightsUiState` exposes computed
  getters (`totals`, `hourBuckets`, `dna`, `bestWeekday`, `forecast`, `tagDistribution`), each running
  `AnalyticsEngine` over up to 1000 rows *every time they're read*. Precompute once in the ViewModel.
- **P2 · AI calls on every screen entry.** Nav destinations recreate their ViewModels, so the Dashboard
  fires 2 LLM calls and Insights 2 more on each visit. That's slow and it's the credit drain. Add a TTL
  cache (MemoryCache plus a server-side per-user cache).
- **P2 · Battery.** The rotation-vector sensor (fused gyro, mag and accel) stays registered for the
  whole session, but it's only used to calibrate. Unregister it after calibration.
- **P2 · Screen-off reliability [verify on device].** The sensors are non-wakeup, and there is no
  partial wake lock anywhere. Once the CPU suspends with the screen off, lift events can be batched,
  delayed or dropped. Test with a 15-minute screen-off session, then a lift. Fix with a partial wake
  lock for the session, or the wake-up accelerometer.
- **P2 · No baseline profile.** Cold-start and first-scroll JIT cost. **[verify on device]** startup time.
- **OK:** the room fan-out is parallel (gated on the slowest read, not the sum); the 1 Hz ticker is
  cheap under Compose strong skipping (Kotlin 2.1); stale-while-revalidate gives instant re-entry.

## 4. Backend (schema, RLS, AI routes, web)

- **P0 · Live regression — cross-user profile reads.** Lovable migration `20260924023750` dropped
  "Authenticated can view basic profile fields" on `profiles` and replaced it with own-row only. The
  web moved to the new `public_profiles` table; Android didn't. These reads now return only you, or
  nothing:
  - `LeaderboardRepository:41` — the leaderboard shows only you.
  - `FriendsRepository:65,90`.
  - `GroupsRepository:126` (embedded `profiles(...)` join) and `:261`.
  - `PartnersRepository:56`.
  - `TrustRepository:145,200`.
  - `FeedRepository:101,137`.
  - `RecapRepository:142–157` — the rank computes as "1 of 1".
  - `RecapRepository:274,288,352`.
  - `ProfileRepository:389`.

  `public_profiles` columns: id, display_name, username, avatar_url, bio, title, prestige_level,
  banner_*, lifetime_xp, current_focus_streak, best_streak, total_focus_seconds, created_at,
  last_active_at. **[verify on device: Leaderboard]**
- **P1 · Breach recording can be lost.** The breach RPC is best-effort (`RoomViewModel.onBreach`
  `runCatching`, no retry), and breaches reach the server only through the ViewModel's collectors.
  Offline, backgrounded or swiped means the breach is never recorded while scoring proceeds. Move
  recording into the service and add a durable queue.
- **P1 · AI cost controls.** Only `getWeeklyStory` and `summarizeVaultItem` use `withAiBudget`.
  `recommend`, `dashboard-insights`, `session-recap`, `proactive` and `companion` are unmetered, with no
  per-user rate limit on public routes, so any signed-in user can script unlimited LLM spend. There's
  also no per-user caching of recommendations and insights.
- **P2 · AI error mapping.** `callAIJson` throws on any gateway non-2xx. The public routes don't catch
  it, so the client gets **500 + an HTML error page**. Return 503 JSON `{error, reason}` instead.
  Companion input validation failures also become a 500 (should be 400).
- **P1 · Branch drift.** The Android branch is **35 migrations behind main**. Merge `main` into it
  (merge only, never rebase: Lovable history).
- **P1 · Revert `29c2a5b`.** The `plans`/`subscriptions` migration it adds is redundant. Everything I
  earlier called "missing" (plans, subscriptions, access_tier, my_entitlement, lifetime_promo_status,
  ai_usage_status, open_capsule, redeem_lifetime) **already exists in main's migrations**. It's ordered
  after main's, so on merge it would drop and re-create main's policies.
  → **The schema is fully reproducible from `main`.** The SQL-editor dump is unnecessary.
- **P3** 4 SECURITY DEFINER email-queue wrappers lack `SET search_path`. They're service_role-only, so
  the risk is low.
- **P3** `room_milestones(room_id)` is unindexed.
- **Web (`main`, local CI run):** typecheck ✅ · tests ✅ 254/254 · lint ❌.
  - Lint has 648 errors: 645 are prettier formatting, and 3 are the `normalize.ts` regex (all fixed by
    open PR #16).
  - There are 37 warnings, including 8 `exhaustive-deps` in `room.$code.tsx` (stale-closure risk in the
    web session loop).
- **OK:** RLS enabled on 46/46 tables. The early permissive room/participant/break policies were dropped
  in 20260621. 47/51 definer functions pin `search_path`. Scoring is server-authoritative. Hot-path
  indexes are present.

## 5. Pixel-level

- **P2 · Spacing system unused.** `Spacing.kt` defines 4/8/12/16/20/24/32/48 but has **0 usages**. Feature
  code has **834 raw `.dp` literals**, including ~95 off-grid spacers (2, 3, 6, 10, 14, 28, 36, 40dp).
  That's why vertical rhythm differs between screens.
- **P2 · Label type too small.** `MonoLabelSmall` is **9sp** (+0.25em tracking) and `MonoLabel` is 10sp,
  below the ~11sp legibility floor. They're used on tappable actions (✦ SUMMARIZE, DELETE, chips). The
  wide tracking also risks overflow in single-line rows at large font scale.
- **P2 · Status-bar bleed.** `ResponsiveColumn` applies `safeDrawingPadding` *inside* each screen's
  `verticalScroll`, so the inset scrolls away and content slides under the clock with no scrim (seen on
  the Dashboard). Hoist the top inset out of the scroll or add a scrim.
- **P2 · Touch targets and semantics.** There are 41 `.clickable` sites, and `Role` semantics exist only
  in `Controls.kt`. Clickable text (✕ dismiss, DELETE, ✦ SUMMARIZE, opener chips) has ~20–30dp targets
  (below 48dp) and reads as plain text to TalkBack. Use `TextButton`/`IconButton` or
  `minimumInteractiveComponentSize()` + `role = Role.Button`.
- **P3** Achievement tier colors are hardcoded (gold `#FFD700` has low contrast on light; "obsidian"
  renders white).
- **P3** The Companion subtitle wraps mid-phrase.
- **OK:** the type scale is disciplined (0 ad-hoc `sp` in feature code).

## 6. Build / release / process

- **P2 · Release APK not smoke-tested.** It builds (7.9 MB, R8 obfuscation on, empty
  `proguard-rules.pro` worked because the libraries ship consumer rules). The runtime is untested:
  serialization and reflection issues only show at runtime. **[verify on device]**
- **P2 · Release logging.** Logs are unconditional in release: auth URLs and status, breach and
  calibration data. Gate them on `BuildConfig.DEBUG` or strip with R8 `-assumenosideeffects`.
- **P2 · Unused budget in the release APK.** 3× 861 KB WAV loops should be OGG/Opus (~200 KB). The
  icons are covered in §2.
- **P2 · Thin test coverage.** 12 unit-test files (pure logic). There are 0 ViewModel, repository or
  instrumented UI tests. The bugs above (channel leak, queue race, profile regression) are exactly what
  those layers would catch.
- **P2 · Finalize-queue race.** `FinalizeQueueWorker:29–37` reads, submits, then
  `replaceFor(owner, survivors)` from a stale snapshot. A result parked during the drain is lost.
  Remove only the submitted keys inside `edit {}`.
- **P2 · Service stop can crash.** `FocusSessionService.stop()` uses `startService(ACTION_STOP)`, which
  can throw from the background once the service has stopped. Use `stopService()`.
- **P1 · CI dead on main.** Every run since ~09-20 is "job not started — account locked due to a
  billing issue", so Lovable's pushes (including the profiles RLS change) are unverified. PR #16 is
  still open.
- **P3** Stale PRs #7 (superseded by #10) and #5 (ImgBot).
- **P3** `versionCode 1`, no signing config.

---

## Verify on device (plug in the phone, then I run these)

1. Leaderboard / Friends / Feed show other users (confirms or clears the P0).
2. Cold start: `adb shell am start -W` (TotalTime).
3. Jank: `dumpsys gfxinfo app.stackd.debug` while scrolling History / Feed / Leaderboard.
4. Memory: `dumpsys meminfo` after visiting 5 rooms (confirms the channel leak growth).
5. Screen-off breach: 15-minute locked session, then a lift.
6. Airplane-mode start + lift → does the breach ever reach the server?
7. Install the release APK and smoke-test sign-in → room → finalize.
