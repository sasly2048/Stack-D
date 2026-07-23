# Stack'd — Design Token Audit

Single source of truth for color, spacing, radius, and dot indicators.
All tokens live in `src/styles.css` under `@theme inline` (Tailwind v4
theme) and `:root` (raw variables). Consume by name; never hardcode.

Verified across mobile (390), tablet (820), desktop (1280) via
`tests/visual/run.py`.

## Color

| Token                 | Value      | Use                                 |
| --------------------- | ---------- | ----------------------------------- |
| `--color-obsidian`    | `#0A0A0A`  | App background                      |
| `--color-obsidian-2`  | `#111111`  | Cards, popovers                     |
| `--color-obsidian-3`  | `#181818`  | Secondary surfaces                  |
| `--color-silver`      | `#E2E2E2`  | Primary text                        |
| `--color-silver-dim`  | `#9A9A9A`  | Body copy, muted labels             |
| `--color-muted`       | `#404040`  | Dividers                            |
| `--color-muted-2`     | `#2A2A2A`  | Inset surfaces                      |
| `--color-ember`       | `#F0A968`  | Accent — "luxury" period, dots, CTA hover glow |
| `--color-ember-glow`  | `#FFC48A`  | Ember hover / text-shadow           |
| `--color-breach`      | `#FF3B30`  | Errors, invalid state               |
| `--color-pulse`       | `#34D399`  | Live status, in-session             |

Rule: never write raw hex in components. Use the Tailwind class
(`text-ember`, `bg-obsidian`) or `var(--color-*)`. Join button owns its
own token cluster (`--join-*`) — see `src/styles.css` L87-133.

## Radius

| Token           | Value                        | Use                    |
| --------------- | ---------------------------- | ---------------------- |
| `--radius`      | `0.625rem` (10px)            | Base                   |
| `--radius-sm`   | `calc(var(--radius) - 4px)`  | Inputs, code tiles     |
| `--radius-md`   | `calc(var(--radius) - 2px)`  | Buttons                |
| `--radius-lg`   | `var(--radius)`              | Cards                  |
| `--radius-xl`   | `calc(var(--radius) + 4px)`  | Modals                 |
| `--radius-2xl`  | `calc(var(--radius) + 8px)`  | Field data grid, hero  |

## Spacing (4px baseline)

| Token          | px  | Use                             |
| -------------- | --- | ------------------------------- |
| `--space-1`    | 4   | Icon gutter                     |
| `--space-2`    | 8   | Inline gap                      |
| `--space-3`    | 12  | Compact stack                   |
| `--space-4`    | 16  | Default stack                   |
| `--space-6`    | 24  | Card padding, form group        |
| `--space-8`    | 32  | Field card padding              |
| `--space-10`   | 40  | Sub-section rhythm              |
| `--space-12`   | 48  | Section internal                |
| `--space-16`   | 64  | Between blocks                  |
| `--space-20`   | 80  | Section vertical (mobile)       |
| `--space-24`   | 96  | Section vertical (desktop)      |

Prefer Tailwind spacing utilities that map to the same scale
(`p-8`, `gap-6`, `mb-16`). Custom values only through these tokens.

## Dot indicator

Every ember "." (after `luxury`, in nav status, live-node markers)
must resolve through the dot tokens — otherwise mobile/tablet/desktop
drift.

| Token           | Value                          | Use                    |
| --------------- | ------------------------------ | ---------------------- |
| `--dot-size`    | `0.5rem` (8px)                 | Default inline dot     |
| `--dot-size-sm` | `0.375rem` (6px)               | Status pip             |
| `--dot-size-lg` | `0.625rem` (10px)              | Accent / marquee dot   |
| `--dot-color`   | `var(--color-ember)`           | Fill                   |
| `--dot-glow`    | `0 0 12px rgba(240,169,104,.55)` | Optional halo        |

## Consumption rules

1. Components read tokens via Tailwind utilities that map to `@theme`.
2. Never hardcode hex, rem, or px for color/spacing/radius/dots.
3. Breakpoint scaling: change the utility (`p-6 md:p-8`) — do not
   introduce a new token per breakpoint.
4. New surface? Add a semantic token (e.g. `--color-surface-elevated`)
   rather than aliasing raw hex.
5. Run `tests/visual/run.py` after any token change to catch drift.
