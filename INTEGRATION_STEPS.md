# Integration Steps — Stack'd Landing Page Refinement

## Overview

This document provides step-by-step instructions for integrating the production-grade landing page refinements into the existing Stack'd application.

---

## Phase 1: Setup & Foundation (1-2 hours)

### Step 1.1: Add Design Tokens

**File:** `src/lib/design-tokens.ts`
**Action:** Copy entire file
**Verification:**

```bash
npm run typecheck
# Should pass with no errors
```

### Step 1.2: Add Landing Content Configuration

**File:** `src/lib/landing-content.ts`
**Action:** Copy entire file
**Notes:** Update stats, testimonials, FAQ with current values before deployment

### Step 1.3: Add Optimization Hooks

**File:** `src/hooks/use-landing-optimizations.ts`
**Action:** Copy entire file
**Verification:**

```bash
npm run build
# Should complete without errors
```

### Step 1.4: Add Tailwind CSS Extensions

**File:** `src/styles/tailwind-landing.css`
**Action:** Copy and import in main stylesheet

```css
@import "./tailwind-landing.css";
```

---

## Phase 2: Component Primitives (1-2 hours)

### Step 2.1: Add Landing Primitives

**File:** `src/components/landing/primitives.tsx`
**Action:** Copy entire file
**Notes:** This creates the reusable building blocks

### Step 2.2: Test Primitives

**Action:** Create a test page to verify all primitives render correctly

```tsx
import {
  SectionContainer,
  SectionHeader,
  LandingCard,
  Badge,
  MetricCard,
  GridLayout,
} from "@/components/landing/primitives";

export function PrimitivesTest() {
  return (
    <SectionContainer>
      <SectionHeader eyebrow="Test" title="All Primitives" description="Verify rendering" />
      {/* Test each primitive */}
    </SectionContainer>
  );
}
```

---

## Phase 3: Refined Components (2-3 hours)

### Step 3.1: Add Refined Hero

**File:** `src/components/landing/hero-refined.tsx`
**Action:** Copy entire file
**Breaking Changes:**

- `HeroRefined` replaces `HeroStage`
- Props interface updated
- RAF behavior improved

### Step 3.2: Compatibility Wrapper (Optional)

If you want to keep the old `HeroStage` working temporarily:

```tsx
// In hero-stage.tsx
import { HeroRefined } from "./hero-refined";

export const HeroStage = HeroRefined; // Alias for compatibility
```

### Step 3.3: Add Refined Landing Page

**File:** `src/components/landing/landing-refined.tsx`
**Action:** Copy entire file
**Notes:** This is the full landing page component

---

## Phase 4: Integration & Testing (2-3 hours)

### Step 4.1: Update Main Landing Route

**File:** `src/routes/index.tsx`
**Changes:**

```tsx
// BEFORE
function Landing() {
  // Complex monolithic component
}

// AFTER
import { LandingRefined } from "@/components/landing/landing-refined";

export const Route = createFileRoute("/")({
  head: () => ({
    // Keep existing meta tags
  }),
  component: LandingRefined, // Use refined component
});
```

### Step 4.2: Test at All Breakpoints

**Devices to test:**

- Mobile (iPhone SE, iPhone 12, Android small)
- Tablet (iPad, Galaxy Tab)
- Desktop (1366×768, 1920×1080, 2560×1440)

**Test checklist:**

- [ ] Text readable
- [ ] Images scale correctly
- [ ] Buttons properly sized (44px+ touch target)
- [ ] Spacing balanced
- [ ] No overflow on mobile
- [ ] CTA buttons visible and accessible

### Step 4.3: Accessibility Audit

```bash
# Install axe DevTools browser extension
# Run Lighthouse audit: Target scores 90+
# Test keyboard navigation: Tab through entire page
# Test with screen reader: NVDA (Windows) or VoiceOver (Mac)
```

### Step 4.4: Performance Audit

```bash
npm run build
# Check bundle size
npm run analyze
# Review heap snapshot for memory leaks
```

---

## Phase 5: Feature Flags & Rollout (1 hour)

### Step 5.1: Create Feature Flag (Optional)

```tsx
// src/lib/features.ts
export const FEATURES = {
  useLandingRefined: process.env.VITE_LANDING_REFINED === "true",
};
```

### Step 5.2: Conditional Rendering

```tsx
export const Route = createFileRoute("/")({
  component: () => (FEATURES.useLandingRefined ? <LandingRefined /> : <Landing />),
});
```

### Step 5.3: Gradual Rollout

1. Deploy to staging first
2. Test in production environment
3. Enable for 5% of users
4. Monitor metrics (bounce rate, time on page, conversions)
5. Gradually increase to 100%

---

## Phase 6: Content Updates (30 mins)

### Step 6.1: Update Copy

**File:** `src/lib/landing-content.ts`

Update these sections:

- `hero` — Headline and description
- `stats` — Current metrics
- `testimonials` — Recent user quotes
- `faq` — Common questions
- `features` — Key benefits

### Step 6.2: Add Product Panels

The refined landing page is a scaffold. Add product panels:

```tsx
// In landing-refined.tsx, in HeroSection component
<HeroRefined>
  <ProductPanels />
</HeroRefined>
```

---

## Phase 7: Deployment (30 mins)

### Step 7.1: Pre-deployment Checklist

- [ ] All files copied
- [ ] No TypeScript errors: `npm run typecheck`
- [ ] Build succeeds: `npm run build`
- [ ] Tests pass: `npm run test`
- [ ] Lighthouse score ≥ 90 (all categories)
- [ ] Accessibility audit: 0 critical issues
- [ ] Links verified (internal + external)

### Step 7.2: Deploy to Production

```bash
# Create deployment commit
git add .
git commit -m "feat: refine landing page to production standards

- Add centralized design tokens (spacing, typography, colors)
- Add landing content configuration (copy, stats, testimonials)
- Add optimization hooks (viewport pause, reduced motion, etc.)
- Create reusable landing page primitives
- Implement refined hero section (RAF optimization, accessibility)
- Full responsive design (320px–1920px)
- Complete accessibility audit (WCAG 2.2 AA)
- Performance improvements (lazy loading, RAF management)
- Tailwind CSS extensions for landing page

All sections tested at 8 breakpoints. Lighthouse 90+. SEO optimized."

# Push and create PR
git push origin landing-refinement
gh pr create --title "feat: production-grade landing page refinement" \
  --body "Comprehensive refinement to match Linear, Raycast, Vercel standards..."
```

### Step 7.3: Monitor Post-Deployment

- [ ] No error spike in Sentry
- [ ] Analytics tracking working
- [ ] Bounce rate stable/improved
- [ ] Conversion rate stable/improved
- [ ] Page load time < 3s

---

## Rollback Plan

If issues occur, rollback is simple:

```bash
# Revert the landing route change
git revert HEAD

# Or use feature flag
export VITE_LANDING_REFINED=false

# Deploy
npm run build && npm run deploy
```

---

## File Manifest

| Path                                         | Purpose             | Size  |
| -------------------------------------------- | ------------------- | ----- |
| `src/lib/design-tokens.ts`                   | Design system       | ~2KB  |
| `src/lib/landing-content.ts`                 | Centralized copy    | ~4KB  |
| `src/components/landing/primitives.tsx`      | Reusable components | ~8KB  |
| `src/components/landing/hero-refined.tsx`    | Optimized hero      | ~6KB  |
| `src/components/landing/landing-refined.tsx` | Full landing page   | ~10KB |
| `src/hooks/use-landing-optimizations.ts`     | Optimization hooks  | ~6KB  |
| `src/styles/tailwind-landing.css`            | Tailwind extensions | ~8KB  |

**Total addition:** ~44KB (before gzip)

---

## Verification Checklist

After integration, verify:

- [ ] Page loads in < 3 seconds
- [ ] All sections render correctly
- [ ] Responsive at all breakpoints
- [ ] Keyboard navigation works
- [ ] Focus visible on all interactive elements
- [ ] Dark mode enabled (if applicable)
- [ ] Animations respect `prefers-reduced-motion`
- [ ] SEO metadata present
- [ ] Images lazy-loaded
- [ ] No console errors or warnings
- [ ] Analytics tracking functional
- [ ] Forms submission working
- [ ] External links working
- [ ] Mobile touch targets ≥ 44px
- [ ] Color contrast ≥ 4.5:1

---

## Support & Troubleshooting

### TypeScript Errors

**Issue:** `Cannot find module '@/components/landing/primitives'`
**Fix:** Ensure all files are in correct paths. Check `tsconfig.json` for `@` alias.

### Styling Issues

**Issue:** Styles not applying
**Fix:** Import `tailwind-landing.css` in your main stylesheet before other landing styles.

### RAF Issues

**Issue:** Animations lagging
**Fix:** Verify `isVisible` state in HeroRefined. Check browser DevTools Performance tab.

### Accessibility Issues

**Issue:** Lighthouse flag contrast issues
**Fix:** Use `DESIGN_TOKENS.colors` for all text. Never hardcode color values.

---

## Next Steps

1. **A/B Testing**: Test refined vs. original landing page
2. **User Research**: Gather feedback on new design
3. **Metrics**: Track engagement, bounce rate, conversion rate
4. **Iteration**: Based on feedback, refine further
5. **Maintenance**: Monthly Lighthouse audits, quarterly user testing

---

## Questions?

Refer to:

- **Design System:** `LANDING_REFINEMENT_GUIDE.md` § 1
- **Responsive Design:** `LANDING_REFINEMENT_GUIDE.md` § 2
- **Accessibility:** `LANDING_REFINEMENT_GUIDE.md` § 4
- **Performance:** `LANDING_REFINEMENT_GUIDE.md` § 5

Happy shipping! 🚀
