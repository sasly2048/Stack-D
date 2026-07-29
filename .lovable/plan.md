# Redesign the dotted world map (Scene 05)

The current map in `src/components/fx/dotted-map.tsx` builds its land mask from ~16 hand-written continent polygons. That's why shapes read wrong (Asia is a blob, no Mediterranean, missing islands) and why the silhouette doesn't match its container's aspect ratio (`aspect-[76/34]` vs the map's own lat/lon window), which causes the letterboxing/awkward fit.

## What changes

Only the map component and the wrapper's sizing classes in Scene 05. No text, cards, markers/pulses behavior, LeaderboardPanel, animations, or spacing changes elsewhere.

### 1. Real geographic land mask

Generate the dot grid from actual Natural Earth land data instead of hand-drawn polygons:

- Offline (one-time, at implementation): fetch Natural Earth 110m land GeoJSON, rasterize it into an equirectangular land/no-land grid at a fixed resolution (~180 x 90 cells covering lon -180..180, lat -58..84).
- Encode that grid as a compact base64/bit-packed string constant embedded in `dotted-map.tsx` (a few hundred bytes — no new runtime dependency, no network fetch, SSR-safe).
- The component decodes it once in `useMemo` and emits one circle per land cell, exactly as today.

Result: recognizable Mediterranean, Indian subcontinent, Indonesian archipelago, Japan, UK, Scandinavia, Great Lakes, Red Sea, correct Africa/South America taper.

### 2. Correct proportions, edge to edge

- The SVG `viewBox` is derived from the grid dimensions, so the intrinsic aspect ratio always matches the projection — no stretching.
- The wrapper in `src/routes/index.tsx` changes from the fixed `aspect-[76/34]` to the map's true aspect so the dots fill the slot edge to edge with no dead bands. `MapSkeleton` gets the same aspect so the lazy-load swap stays silent.
- `preserveAspectRatio="xMidYMid meet"` stays; the SVG remains `w-full h-auto`.

### 3. Per-breakpoint composition

Dot density/size responds to viewport so the map reads cleanly at 394px as well as desktop:

- Mobile (<640px): coarser sampling (every other column/row) and slightly larger dot radius, so dots stay legible instead of turning into grey mush.
- Tablet (640–1024px): intermediate density.
- Desktop: full grid density.

Implemented with a small matchMedia-driven density state inside the component (defaults to the desktop grid during SSR, so no hydration mismatch), not with CSS scaling.

### 4. Markers

Keep the existing 10 city pulses and their look (ember fill, drop shadow, expanding ring, staggered `map-pulse`). Add a few for global balance: Los Angeles, Mexico City, Dubai, Nairobi, Seoul, Toronto. Marker positions use the same lon/lat → grid projection, so they land on the right coastlines.

## Visual style preserved

Same `dotColor` (`rgba(226,226,226,0.32)`), same `pulseColor` (`#F0A968`), same faint dashed equator line, same `opacity-90` blend, same props API (`className`, `dotColor`, `pulseColor`, `cities`, `step`) so `catalog.tsx` and any other usage keep working.

## Technical notes

- Files touched: `src/components/fx/dotted-map.tsx` (rewrite of the mask + projection), `src/components/fx/skeleton.tsx` (aspect match), `src/routes/index.tsx` (map slot aspect class only).
- Circle count stays in the same order of magnitude as today (~600–900 desktop, fewer on mobile), so render cost and the bundle budget are unaffected.
- Verify with Playwright screenshots at 394px, 834px, and 1440px before finishing.
