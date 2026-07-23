# Visual Regression Tests

Playwright-driven snapshots that verify the landing page across three
breakpoints (mobile 390, tablet 820, desktop 1280). Captures the hero,
orbit, and world map plus a JSON manifest of computed geometry so
regressions surface as diffable data, not just image drift.

## Run

```
python3 tests/visual/run.py
```

Screenshots + `manifest.json` land in `tests/visual/screenshots/<bp>/`.
The script asserts that:

- the orbit block never exceeds the viewport width
- the dotted-map SVG never exceeds the viewport width

Add breakpoints by editing `BREAKPOINTS` in `run.py`.

## Baselines

Commit the `screenshots/` folder as the baseline. After an intentional
visual change, re-run and review the diff before committing new frames.
