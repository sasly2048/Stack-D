# Complete UI/UX Consistency Audit

## Goal
Bring every user-facing Stack'd screen to one production-grade visual rhythm while preserving the existing Obsidian Ritualist direction and all current behavior.

## Phase 1 — Shared visual foundation
- Normalize the spacing scale, page gutters, fixed-navigation clearance, content widths, section spacing, readable text measures, heading line-heights, labels, and control heights.
- Align shared buttons, inputs, cards, dialogs, sheets, menus, loading states, empty states, errors, and fixed overlays to those rules.
- Replace repeated one-off page styling with shared patterns where the intended result is equivalent.

## Phase 2 — Public and system screens
- Audit `/`, `/auth`, `/philosophy`, `/privacy`, `/sdk`, `/catalog`, verification, 404, and error screens.
- Check default, loading, invalid, disabled, focused, and retry states.
- Correct spacing, alignment, text measure, wrapping, section transitions, and overflow independently at mobile, tablet, and desktop sizes.

## Phase 3 — Signed-in product screens
- Audit every signed-in page, including Analytics, Start Session, Room, Feed, Friends, Circles, Ranks, Insights, Timeline, Challenges, Seasons, Achievements, Wrapped, DNA, Replay, Vault, Capsule, Partners, Atlas, Integrations, Profile, Trust, Moderation, and Webhooks.
- Standardize page openings, heading hierarchy, panel spacing, grid transitions, form rhythm, action rows, tables/lists, and empty/error states.
- Validate dynamic room and profile layouts with accessible real data without changing product data or business logic.

## Phase 4 — Overlays and transient states
- Audit dialogs, sheets, command palette, mobile navigation, toasts, offline banner, timer, queue badge, upgrade/manage-subscription flows, confirmations, and ceremonies.
- Correct viewport fit, safe-area spacing, stacking, focus presentation, action spacing, and text wrapping.

## Phase 5 — Responsive regression pass
- Extend the visual audit into a repeatable route-by-viewport matrix for 390×844, 820×1180, and 1280×1800, plus a 320px overflow safety check.
- Assert no horizontal overflow, collisions, clipped text, escaped controls, incoherent blank space, or unstable fixed elements.
- Review viewport screenshots, fix remaining inconsistencies, and run focused accessibility and interaction checks.

## Guardrails
- Preserve the current visual direction, interaction behavior, copy intent, navigation, data flow, and business rules.
- Use semantic design tokens and existing controls rather than page-level hardcoded styling.
- Treat mobile, tablet, and desktop as separate compositions, not scaled copies.
- Keep touch targets, keyboard focus, contrast, reduced motion, and safe areas intact.
