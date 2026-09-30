# Backend AI surfaces → Android — implementation plan

**Goal:** bring the 7 LLM-backed web features to Android. They can't run on-device
(the `LOVABLE_API_KEY` must never ship in the APK), so Android calls
public web routes that hold the key server-side — the **exact `auth-guard`
pattern already in use** (`{WEB_BASE_URL}/api/public/auth-guard`).

## The constraint (why routes, not on-device)
- Web AI = `src/lib/ai.server.ts::callAIJson()` → POSTs the Lovable AI Gateway
  (`https://ai.gateway.lovable.dev/v1/chat/completions`) with header
  `Lovable-API-Key: process.env.LOVABLE_API_KEY` (server-only secret).
- Web callers are `createServerFn(...).middleware([requireSupabaseAuth]).handler`
  — framework RPC endpoints Android can't hit directly.
- `requireSupabaseAuth` (`src/integrations/supabase/auth-middleware.ts`) reads
  `Authorization: Bearer <token>`, builds a token-scoped Supabase client, exposes
  `{ supabase, userId }`.

## The 7 surfaces (web fn → Android UI)
| # | web function | file | Android UI target |
|---|---|---|---|
| 1 | `recommendNextSession` | ai.functions.ts:110 | Atlas card (replace local heuristic in DashboardScreen) |
| 2 | `generateDashboardInsights` | ai.functions.ts:224 | new dashboard insights card |
| 3 | `generateSessionRecap` | ai.functions.ts:371 | recap card on RoomScreen.Ended |
| 4 | `getWeeklyStory` + `discoverPatterns` | ai-narrative.functions.ts:30,58 | Insights weekly-narrative |
| 5 | `getProactiveInsights` | proactive-ai.functions.ts:26 | Insights proactive panel |
| 6 | `askCompanion` | companion.functions.ts:12 | AtlasWhisper companion |
| 7 | `summarizeVaultItem` | memory-vault.functions.ts:85 | Vault item "AI ✦" summary |

## Web side (goes on MAIN, via its own branch/PR — never the android branch)
Branch already created: `web-ai-routes` off origin/main.

**DRY approach (preferred):** for each fn, extract its handler body into an
exported plain helper `xCore(supabase, userId, input) => result`, and have BOTH
the existing `createServerFn` handler AND the new public route call it — zero
prompt/fallback drift. (Started this for `recommendNextSession` then reverted at
the 76%-context boundary; redo cleanly.)

**Route shape** (mirror `src/routes/api/public/health.ts` + auth-middleware):
```
src/routes/api/public/ai/recommend.ts        (POST)
src/routes/api/public/ai/dashboard-insights.ts
src/routes/api/public/ai/session-recap.ts    (POST {historyId})
src/routes/api/public/ai/weekly-narrative.ts
src/routes/api/public/ai/proactive.ts
src/routes/api/public/ai/companion.ts         (POST {question})
src/routes/api/public/ai/vault-summarize.ts   (POST {itemId})
```
Each handler: read `Authorization: Bearer`, build token-scoped supabase client
(copy from auth-middleware), resolve userId via `supabase.auth.getUser()`, call
`xCore(...)`, `Response.json(result)`. 401 on missing/invalid token.

**CI note:** web CI (`verify`) currently blocked by a GitHub Actions BILLING
lock on the account — not code. Local `bun run lint/typecheck/test/build` all
pass. Fix billing or merge web PRs with `--admin`.

## Android side (goes on `worktree-android-phase1-work`)
- New `data/ai/AiRepository.kt`: Ktor/supabase-kt HTTP calls to
  `{BuildConfig.WEB_BASE_URL}/api/public/ai/*` with
  `Authorization: Bearer ${auth.accessToken}`. Mirror `AuthRepository`'s webBase
  usage (AuthRepository.kt:269-295) + `@Serializable` response models matching
  each web return type.
- Wire results into existing UI, each behind graceful fallback (keep the current
  local Atlas heuristic as the offline/failed default):
  - #1 Atlas card: swap `recommendNextSession(history)` local calc → repo call,
    fall back to local on failure.
  - #2 dashboard insights: new card in DashboardScreen (VM fetches async).
  - #3 recap: add AI card to RoomScreen.Ended (VM already has historyId).
  - #4/#5 Insights screen: weekly-narrative + proactive panels.
  - #6 companion: AtlasWhisper surface (dashboard/insights).
  - #7 vault: "AI ✦" per-item summary button (Elite-gated — needs Elite acct to test).

## Deploy-gated — nothing testable until:
1. Web PR merged to main + web app redeployed (routes live).
2. `LOVABLE_API_KEY` present in the web deploy env.
3. Android `WEB_BASE_URL` points at that deploy.
Until then: build to green (kotlin compile + web lint/type/test), verify on
device once live. Each Android surface must degrade gracefully so a 401/timeout
never breaks the screen.

## PROGRESS (resume here)
### ✅ WEB SIDE COMPLETE — all 7 routes done (branch `web-ai-routes`, commit c384332)
build/typecheck/254 tests green, new files prettier-clean. Routes:
recommend, dashboard-insights, session-recap, weekly-story, discover-patterns,
proactive (GET), companion, vault-summarize. Each = shared `xCore` +
`ai-public-auth.ts`. **Next: open PR web-ai-routes → main; deploy needs
LOVABLE_API_KEY in env.**

### ▶ ANDROID SIDE — not started
Build `data/ai/AiRepository.kt` (Ktor/supabase-kt POST/GET to
`{BuildConfig.WEB_BASE_URL}/api/public/ai/*` with `Authorization: Bearer
${auth.accessToken}`) + `@Serializable` response models per route, then wire each
surface with graceful fallback (see the per-surface table above). Elite-gate the
vault one. All on `worktree-android-phase1-work`.

---
### (original notes below)

Branch `web-ai-routes`, commit `be6a4bd`. **3 of 7 web routes DONE + verified**
(build/typecheck/254 tests green, new files prettier-clean):
- ✅ recommend → recommendNextSessionCore
- ✅ dashboard-insights → generateDashboardInsightsCore
- ✅ session-recap → generateSessionRecapCore (+ validateSessionRecapInput, SessionRecapInput)
Shared: `src/lib/ai-public-auth.ts` (authenticate/unauthorized). `AiSupabase` type at top of ai.functions.ts.

**REMAINING 4 — identical mechanical pattern** (extract handler body → exported
`xCore(supabase, userId, input?)`, RPC becomes thin wrapper, add route file that
authenticate()s then calls core):
- getWeeklyStory (src/lib/ai-narrative.functions.ts) → routes/api/public/ai/weekly-story.ts (POST)
- discoverPatterns (src/lib/ai-narrative.functions.ts) → .../discover-patterns.ts (POST)
- getProactiveInsights (src/lib/proactive-ai.functions.ts, GET) → .../proactive.ts (GET)
- askCompanion (src/lib/companion.functions.ts, POST body {question}) → .../companion.ts (POST, await request.json())
- summarizeVaultItem (src/lib/memory-vault.functions.ts, POST body {itemId}) → .../vault-summarize.ts (POST)
For files without `AiSupabase`, add `import type { SupabaseClient } from "@supabase/supabase-js"; import type { Database } from "@/integrations/supabase/types"; type AiSupabase = SupabaseClient<Database>;`.
ALWAYS `bun run build` before typecheck (regenerates routeTree for new routes).
Prettier-fix ONLY your new files (`bunx eslint --fix <files>`) — do NOT mass-fix main's pre-existing format debt (that's PR #16).

## Suggested order
1. Web: extract cores + 7 routes + local verify (lint/type/test) → PR to main.
2. Android: AiRepository + models → compile.
3. Android UI wiring surface-by-surface, each with fallback.
4. Verify on device once web is deployed.
