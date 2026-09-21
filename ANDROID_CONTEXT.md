# Stack'd Android — Context Extract

Hand this to a fresh session to resume the Android build. Everything here was
read off branch `android-phase1` at commit `3014578`, not from memory.

**Nothing about the web app is in scope.** The web repo is used only as a
feature/design/backend spec; do not audit, modify, or deploy it.

---

## 1. What this app is

Native Kotlin/Compose rewrite of Stack'd — a real-time multiplayer
"phone-stacking" focus app. You start a session, put your phone down, and the
app watches its sensors: tilt it or pick it up and you *breach*, which costs
you the session.

Target: 1:1 replica of the web app in features, UI, and theme.

## 2. Where the code is

| | |
|---|---|
| Repo | `sasly2048/Stack-D` |
| Branch | `android-phase1` |
| Head | `3014578` |
| PR | [#7](https://github.com/sasly2048/Stack-D/pull/7) — **open, not draft, targets `main`, unmerged** |
| Base drift | Branch is **behind `main`** (main has since taken web-only commits). Rebase before continuing. |
| Local worktree | `.claude/worktrees/stack-d-audit-fixes` — has an untracked `android/` with **build output only**, no source. Real source is on the branch. |

52 tracked files under `android/` + the `auth-guard` Edge Function under
`supabase/functions/`.

### Commits on the branch (oldest first)
```
5eef526 feat(android): re-land Phase 0 scaffolding and port the breach detector
e251e2e feat(android): session timer, offline result queue, and drift-free clock
9f5de18 feat(android): auth repository, auth-guard function, and real fonts
3014578 fix(auth-guard): stop trusting client-reported sign-in outcomes
```

## 3. Backend

Android talks **directly to the same live Supabase project as the web app** —
shared accounts, rooms, and leaderboards. There is no server tier; RLS and
`SECURITY DEFINER` RPCs are the entire trust boundary.

- Project ref: `wmqyswkqdnfnpdcpdhan`
- URL: `https://wmqyswkqdnfnpdcpdhan.supabase.co`
- Anon/publishable key → `android/local.properties` (gitignored) → `BuildConfig`

> **Security constraint, standing:** the `service_role` key must **never** go in
> `local.properties` or anywhere else in the APK — it would be trivially
> extractable. Service-role flows go through Edge Functions only.

Email confirmation is **required** (`mailer_autoconfirm: false`, verified via
`/auth/v1/settings`). Sign-up therefore yields **no session** — it must show
"Check your email to confirm your account" and must **not** route to the
dashboard.

## 4. Architecture

Single Gradle module (`app`), package-by-feature, MVVM, one ViewModel per
screen with `StateFlow<UiState>`. Manual DI via an `AppContainer` — no Hilt.
Repositories are concrete classes mirroring the web's `*.functions.ts` files
1:1 so porting stays mechanical.

```
android/app/src/main/java/app/stackd/
  MainActivity.kt  StackdApplication.kt  StackdNavHost.kt  Destinations.kt
  core/
    supabase/SupabaseModule.kt
    settings/SettingsStore.kt
    theme/{Color,Type,Shape,Motion,Theme}.kt
    ui/{GlassCard,PulseDot,Placeholder}.kt
    workmanager/{FinalizeQueue,FinalizeQueueWorker}.kt
  data/auth/AuthRepository.kt
  feature/room/session/{BreachRules,BreachDetector,FocusSessionService,SessionClock}.kt
```

Realtime is **ViewModel-scoped**, not a shared connection manager — subscribe in
`init`, `unsubscribe()` in `onCleared()`. Matches how the web scopes channels to
screen lifetime.

## 5. What is DONE

### Phase 0 — scaffolding ✅
Gradle + version catalog, Supabase SDK wired, Compose theme ported from the web's
`styles.css` `@theme inline` block, nav graph with **30 destinations** declared as
placeholders, manifest, real Inter (400/500/600/800) + JetBrains Mono (400/500)
`.ttf` in `res/font/`, app icon + adaptive mipmaps.

### Phase 1 — session mechanics ✅ (backend-independent half)

- **`BreachRules.kt`** — pure decision logic, no Android types, so it is
  JVM-testable without a mocking framework. Constants ported verbatim from the
  web's `use-sensors.ts`:
  - tilt threshold 60° gentle / 30° absolute
  - shake threshold 22 gentle / 16 absolute
  - `TILT_HOLD_MS = 3000`, `MINOR_THROTTLE_MS = 3000`, `LIFT_ANGLE = 90`
  - haptics 200ms severe / 60ms minor
  - severe if: absolute mode, OR held > 3000ms, OR delta > 90° (reason becomes `LIFT`)
- **`BreachDetector.kt`** — drives the rules off `TYPE_ROTATION_VECTOR` +
  `TYPE_ACCELEROMETER`. Purely local/callback-driven; it does **not** call
  Supabase itself — the ViewModel's callback does. Same separation as web.
- **`FocusSessionService.kt`** — foreground service using
  `setWhen` + `setUsesChronometer` + `setChronometerCountDown`, so the OS renders
  the countdown from an absolute wall-clock base. Drift-immune by construction,
  no per-second updates.
- **`SessionClock.kt`** — every value derived from server `started_at`, never a
  local tick.
- **`FinalizeQueue.kt` / `FinalizeQueueWorker.kt`** — DataStore JSON queue,
  dedupe by `(owner, roomId)`, WorkManager flush on `NetworkType.CONNECTED`.
  Relies on `finalize_focus_session`'s existing one-row-per-(profile,room)
  idempotency.
- **`SettingsStore.kt`** — DataStore. Holds `devToolsEnabled` (off by default)
  and the device fingerprint: a **random UUID**, deliberately not `ANDROID_ID`
  (which is stable across uninstalls and shared across same-key apps — far more
  identifying than a 60-second throttle needs).

**Tests: 30/30 green** (`BreachRulesTest`, `SessionClockTest`). `assembleDebug`
succeeds, APK ~24.6 MB.

### Auth ✅ — including a security fix the original plan didn't anticipate

`AuthRepository.kt` + `supabase/functions/auth-guard/index.ts`.

The important part, from commit `3014578`: **password sign-in runs entirely
inside the Edge Function.** The original design had the client call Supabase
Auth directly and then report the outcome back for logging. That was wrong —
the lockout throttle is driven by the failure count in `auth_attempts`, so
whoever writes that row decides who gets locked out. A client that reports its
own outcome can lock out **any address it knows**. Now the credential check
happens server-side, the client never asserts an outcome, and it receives either
session tokens (installed via `importSession`) or a refusal.

Two failure modes, deliberately opposite:
- `POST /auth-guard` (advisory throttle) fails **open** — it is a throttle, not
  the authorization boundary; RLS and Supabase Auth stand behind it, so an
  outage must not lock everyone out. Matches web's `catch → null`.
- `POST /auth-guard/signin` (the actual sign-in) fails **closed** — an
  unreachable function means no session, not a free pass.

Rate-limit constants, verbatim from web:
`SIGNIN_WINDOW_SEC=60`/`MAX_HITS=10`, `FP_WINDOW_SEC=60`/`MAX_HITS=15`,
`EMAIL_FAILURE_WINDOW_SEC=600`/`LOCKOUT_THRESHOLD=5`. Same message strings so
Android and web read identically.

Client IP comes from `Deno.ServeHandlerInfo.remoteAddr`, **never**
`X-Forwarded-For` — XFF is caller-supplied, so keying a rate limit on it means
an attacker rotates one header and the throttle protects only honest clients.

#### Deliberate divergences from web (all settled with the user)
| Area | Web | Android | Why |
|---|---|---|---|
| Google sign-in | Lovable OAuth broker → `setSession` | Credential Manager → `signInWith(IDToken)` | Broker is browser-only. Same Supabase user, different transport. |
| Apple sign-in | Supported | **Not supported** | Would need a web redirect. Revisit only if users ask. |
| CAPTCHA | Turnstile on sign-up + after failed sign-in | **None** | No native widget; a signed APK is a weaker bot target than an open form. Every other guard check preserved. |
| `tab-hidden` / `wake-lost` | Two distinct browser signals | Both collapse to `onPause`/lifecycle STOP | One detection point on Android. Both enum values kept for schema parity. |

Google is gated on a Google Cloud OAuth client ID (Web + Android types, Android
one bound to the signing SHA-1) being added to the Supabase Google provider.
Until it exists the button is hidden and email/password works standalone — the
ID is read from `local.properties`, so **no code change** when it lands.

## 6. What is NOT done

### Phase 1 remainder — needs the live backend
Auth screen, dashboard, start, room, results screens and the repositories behind
them. Credentials are in place, so this is unblocked.

Still to port for the room screen:
- Realtime subscriptions: `rooms` (all events), `participants` (all events),
  `breaks` (INSERT), each filtered by room id — mirrors web's `room:{roomId}` channel.
- **Clock-drift reconciliation** on `ON_RESUME`: re-fetch the room row and
  recompute from server `started_at` rather than trusting the local tick.
- **Confirm-identity step** — web interposes an "Is this you?" screen after any
  successful auth, with a 4-char challenge derived from the user id when no
  email is present, max 3 attempts then auto sign-out. This is user-visible
  product behavior, not web plumbing. Port it.

> **Phase 1 sign-off requires a real device**, not the emulator: full lifecycle
> (lobby → countdown → active → breach → results) with **screen-off mid-session**
> and **airplane-mode mid-session**. Those are exactly what the FGS, the offline
> queue, and the drift correction exist for, and the emulator happy-path doesn't
> exercise any of them.

### Phase 2 — repeat-pattern screens
profile, profile/$id, leaderboard, friends, achievements, challenges. Same
repository + list + realtime shape Phase 1 establishes; batch them.

### Phase 3 — remaining product screens
circles, groups, feed, timeline, insights, trust, trust/moderation, vault,
seasons, integrations, partners, replay, capsule, dna, wrapped.

**AI features are deferred**: companion chat, dashboard recommendations, insights
cards, wrapped narrative, brand-prose copy. Build the screens and nav entries so
the IA is complete, but stub the AI-calling logic with "Coming soon" / static
fallback. Backend strategy for AI is an explicit future decision.

### Phase 4 — dev tooling
webhooks management, SDK docs, MCP tool list — gated behind the Settings
`devToolsEnabled` toggle. Plus a `webhook-test` Edge Function: `{webhookId}` →
`{success, statusCode?, responseSnippet?, deliveryId}`, loading the webhook row
server-side (do **not** trust a client-supplied URL/secret), HMAC-signing and
POSTing a test payload, logging `webhook_deliveries`.

### Phase 5 — polish
reduce-motion audit, empty/error states, CSV export (`FileProvider` + share
intent), wrapped shareable image (Compose `Canvas` → `Bitmap` → share intent),
per-character text reveal.

## 7. Design tokens

Source of truth is the web's **`src/styles.css` `@theme inline` block**.

> Do **not** use `src/lib/design-tokens.ts` — it is legacy/unused and holds an
> old blue-HSL palette from an earlier landing page, not the shipped
> ember/obsidian theme.

| Token | Hex |
|---|---|
| obsidian (bg) | `#0A0A0A` |
| obsidian-2 (cards) | `#111111` |
| obsidian-3 | `#181818` |
| silver (text) | `#E2E2E2` |
| silver-dim | `#9A9A9A` |
| muted | `#404040` |
| muted-2 | `#2A2A2A` |
| ember (accent/CTA) | `#F0A968` |
| ember-glow | `#FFC48A` |
| breach | `#FF3B30` |
| pulse (live) | `#34D399` |
| accent | `#C9874A` |
| border | `rgba(255,255,255,0.08)` |
| ring | `rgba(201,135,74,0.5)` |

Radius scale 4/8/10/14/18px. Signature easing `cubic-bezier(0.32, 0.72, 0, 1)`
→ `CubicBezierEasing(0.32f, 0.72f, 0f, 1f)`, exact, used in every `tween`.

Palette lives in a custom `StackdColors` data class via `CompositionLocal` —
it does not map cleanly onto Material3's semantic slots.

`GlassCard` uses `RenderEffect` blur on API 31+, falling back to a translucent
scrim below that.

**Reduce-motion**: read `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` once,
expose via `LocalReduceMotion`, short-circuit every `InfiniteTransition`.
Mirrors the web's `prefers-reduced-motion`.

## 8. Dependencies (`gradle/libs.versions.toml`)

AGP 8.7.3 · Kotlin 2.1.0 · Compose BOM 2024.12.01 · Supabase KT 3.0.3
(`postgrest-kt`, `realtime-kt`, `auth-kt`, `functions-kt`) · Ktor 3.0.3 (OkHttp
engine) · navigation-compose 2.8.5 · datastore-preferences 1.1.1 ·
work-runtime 2.10.0 · kotlinx serialization 1.7.3 / datetime 0.6.1 /
coroutines 1.9.0 · zxing-core 3.5.3 · JUnit 4.13.2.

Deliberate omissions:
- **No charting library.** The analytics/DNA-radar/heatmap screens are custom SVG
  on web — a bespoke visual language, not off-the-shelf chart shapes. Port as
  Compose `Canvas`/`Path` draws rather than reskinning Vico/MPAndroidChart.
- **zxing for QR generation only.** The app generates invite codes and never
  scans — no CameraX, no camera permission.
- **`storage-kt` not added.** Add only if a Phase 3 screen (vault/capsule) turns
  out to need file upload. Confirm during that phase.

## 9. Known risks — budget real time

- **Sensor calibration on physical devices.** `TYPE_ROTATION_VECTOR` behavior
  varies across OEM implementations. Emulator-simulated sensors prove nothing.
- **Foreground service type declaration.** Play policy shifts here — verify the
  current requirement at implementation time; do not rely on training data.
- **Offline queue's airplane-mode → reconnect path.** Test manually.
- **Drift reconciliation vs the FGS Chronometer.** Background 10+ minutes and
  resume, not just quick app-switches.
- **FGS must not become a breach loophole.** Screen-off during an active session
  still breaches, exactly as web's `visibilitychange` does. The notification's
  job is showing remaining time in legitimate cases (lobby/pre-start), not
  suppressing the `onPause` → breach rule.
- **RLS parity.** With no server tier, every repository method is subject to the
  policy directly. Example: reading `season_participants` directly returns 0 rows
  under the tightened RLS — it must go through the `season_standings` /
  `my_season_rank` RPCs instead. Verify each method against its actual policy.

## 10. Web files to read as spec (read-only)

| File | For |
|---|---|
| `src/hooks/use-sensors.ts` | breach state machine — port verbatim |
| `src/lib/finalize-queue.ts` | offline queue dedupe/retry contract |
| `src/hooks/use-lock-screen-timer.ts` | web's thin timer; the Android FGS is a superset, not a direct port |
| `src/routes/_authenticated/room.$code.tsx` | realtime wiring + full room state machine |
| `src/lib/room.functions.ts`, `src/lib/rooms2.functions.ts` | RPC/table call patterns every repository mirrors |
| `src/lib/auth.functions.ts` | `guardSignIn` / `logAuthAttempt` constants |
| `src/styles.css` (`@theme inline`) | design tokens |

## 11. First moves in the next session

1. `git fetch && git checkout android-phase1 && git rebase origin/main` — the
   branch is behind.
2. Recreate `android/local.properties` if absent (Supabase URL + **anon** key).
3. `cd android && ./gradlew test assembleDebug` to confirm the 30 tests still
   pass after the rebase.
4. Resume Phase 1: auth screen → dashboard → start → room → results.
