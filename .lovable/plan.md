## Stack'D — Polish Pass (Depth 3)

Refinement-only. No new features, no rewrites of auth, Supabase, Atlas, focus sessions, progression, or public APIs. Ember/silver/obsidian branding preserved.

---

### 1. Design system consolidation

- Audit `src/styles.css` tokens; document the canonical scale (radius, spacing rhythm, ember/silver ramps) in a comment block at the top and remove any orphan tokens no `rg` reference points at.
- Sweep components for hardcoded colors (`text-white`, `#FFFFFF`, `bg-black`, arbitrary hex) and replace with existing tokens (`text-silver`, `bg-obsidian`, `text-ember`). Report scope before touching — likely <15 files.
- Normalize button states across `.btn-ember`, `.btn-silver-sweep`, and shadcn `<Button>`: same focus ring token, same disabled opacity, same hover timing curve.
- Unify skeleton loaders — keep `src/components/fx/skeleton.tsx`, delete `src/components/skeleton.tsx` if unused (verified via `rg`).

### 2. Navigation + empty/loading states

- Verify `useNavTier` progressive nav still matches shipped routes; remove references to deleted routes if any.
- Every `_authenticated/*` route: confirm it has a real empty state (not a blank div) and a skeleton on first paint. Fix only the ones that are missing.
- Command palette (`command-palette.tsx`): dedupe entries, ensure every action has an icon + shortcut hint, and sort by section.

### 3. Performance

- `rg` for `useEffect` fetches that should be `useQuery` with a loader — flag only, don't rewrite unless it's a one-line swap.
- Lazy-load heavy FX (`DottedMap`, `Meteors`, `OrbitingCircles`, `LightRays`) if any are still statically imported on non-hero routes.
- Audit `package.json` for deps with zero `rg` hits and remove them.
- Add `React.memo` only to demonstrably re-rendering list items (session timeline rows, activity rail rows).

### 4. Code health

- Remove dead files verified by `rg` returning zero importers.
- Consolidate duplicated helpers: check for parallel `haptic`/`observability`/copy utilities and pick one canonical.
- Fix any `any` types that trivially resolve to a known shape (no deep type surgery).
- Standardize server-fn file naming (`*.functions.ts`) — flag any drift.

### 5. Accessibility quick wins

- Icon-only buttons: add `aria-label` where missing (Nav, palette triggers, close buttons).
- Ensure `focus-visible` ring is visible on ember/silver buttons against obsidian bg.
- Add `aria-live="polite"` to toast region if missing.

---

### Out of scope (explicit)

Auth flow, Supabase schema/RLS, Atlas prompt logic, focus scoring, room realtime, webhooks/SDK, branding colors, route paths, PWA/service worker.

### Execution

Sequential batches, one theme per turn, each ending with a build check. I will report findings before making bulk edits so you can veto per-batch. Estimated 4–6 turns end-to-end.

### First turn deliverable

Audit report: list of hardcoded colors, dead files, duplicated utilities, missing aria-labels, and heavy static imports — with file paths and counts. No code edits until you greenlight the batches.
