# Production UI/UX Polish Audit

## Goal
Bring every user-facing Stack'd screen to a consistent, production-grade finish while preserving the current Obsidian Ritualist direction and all existing behavior.

## Scope
Audit and polish all visual routes at three independent viewports:

- Mobile: 390 × 844, with a 320 px overflow safety check
- Tablet: 820 × 1180
- Desktop: 1280 × 1800

The matrix includes the public pages, authentication and verification, all authenticated product pages, dynamic room and profile pages, the component catalog, 404/error screens, and shared overlays. API, webhook, sitemap, MCP transport, and redirect-only endpoints are nonvisual and excluded.

## Implementation

### 1. Establish one precise visual foundation
- Preserve the obsidian, silver, ember, pulse, and breach palette and current typography character.
- Consolidate page gutters, navigation clearance, content widths, section rhythm, heading scales, label tracking, panel radii, borders, shadows, focus rings, pressed states, and disabled states into shared tokens and reusable patterns.
- Bring shared buttons, inputs, cards, dialogs, sheets, badges, skeletons, empty states, and query errors into that system without flattening intentionally distinctive branded treatments.
- Remove or quarantine the unused parallel landing stylesheet so it cannot reintroduce conflicting styles.

### 2. Audit every public and system screen
Independently inspect and correct `/`, `/auth`, `/philosophy`, `/privacy`, `/sdk`, `/catalog`, and the global 404/error screens at mobile, tablet, and desktop sizes.

Cover default plus relevant interaction states: room-code empty/partial/ready/invalid/loading/rate-limited/network-error, sign-in/sign-up, provider loading/errors, field validation, CAPTCHA placement, password visibility, verification, catalog tabs/dialogs, long code blocks, navigation active states, and keyboard focus.

### 3. Audit every authenticated screen
Inspect and correct the full signed-in route set at all three viewports: Analytics, Start Session, Room, Feed, Friends, Circles/Groups, Ranks, Insights, Timeline, Challenges, Seasons, Achievements, Wrapped, DNA, Replay, Vault, Capsule, Partners, Atlas, Integrations, Profile, public profile detail, Trust, Moderation, and Webhooks.

For each screen:
- Normalize fixed-nav clearance and horizontal gutters.
- Correct heading scaling, line length, wrapping, grid transitions, card alignment, control sizing, and whitespace rhythm.
- Remove clipping, unintended overflow, awkward empty space, crowding, and breakpoint-specific imbalance.
- Keep data-dense screens scannable and action-heavy screens comfortably tappable.

### 4. Exercise real states, not only happy paths
- Restore a safe authenticated preview session and use existing data; do not change product data or business logic for visual convenience.
- Check loading, populated, empty, error, disabled, selected, active, hover, focus-visible, and modal/sheet states wherever the UI exposes them.
- Validate dynamic routes with real accessible room/profile records, including lobby/active/completed room presentations where available.
- Inspect premium upgrade/manage-subscription dialogs, command palette, mobile navigation, toasts, offline banner, floating timer, queue badge, ceremonies, and confirmation overlays for viewport fit, safe-area handling, focus behavior, and stacking collisions.
- Add a deliberate visible fallback for any page state that currently collapses into blank or toast-only content, without changing its underlying behavior.

### 5. Fix by shared cause, then page-specific detail
- First repair shared primitives and page-shell patterns so improvements propagate consistently.
- Then make targeted corrections where a screen intentionally needs different composition or density.
- Treat mobile, tablet, and desktop as separate compositions: no breakpoint is considered fixed merely because another one passes.
- Preserve route behavior, data flow, copy intent, accessibility semantics, reduced-motion support, and the existing brand atmosphere.

### 6. Build a repeatable visual regression matrix
- Extend the Playwright visual audit from a landing-only sample into a route/state matrix with stable screenshots and measurements.
- Assert no document-level horizontal overflow, no visible element escaping intended containers, no text/control overlap, one coherent main landmark, usable dialog dimensions, and stable fixed-overlay placement.
- Keep screenshots viewport-sized rather than full-page; capture individual long sections and overlays separately for useful review.
- Retain focused checks for the landing orbit/map and add representative checks for dense grids, forms, tables/lists, dynamic rooms, and dialogs.

### 7. Re-audit and regression pass
- Run the complete route × viewport matrix after the first polish pass.
- Review screenshots visually, fix remaining regressions, and rerun failed or changed screens.
- Run the relevant accessibility, visual, and end-to-end suites plus type validation.
- Finish only when all reachable visual routes pass mobile, tablet, and desktop checks with no unexplained overflow, clipping, overlap, console failure, or inconsistent shared-state treatment.

## Technical guardrails
- Frontend/presentation changes only unless a missing visible state requires wiring existing errors into the UI.
- No redesign, new feature, navigation rewrite, or payment/auth/business-rule change.
- Use semantic theme tokens and existing component primitives; avoid new hardcoded visual values in page code.
- Keep touch targets at least 44 px where appropriate and preserve keyboard access, contrast, safe areas, and reduced motion.
