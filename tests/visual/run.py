# Visual regression harness: orbit, world map, typography.
#
# Run with:
#   python3 tests/visual/run.py
#
# Writes screenshots to tests/visual/screenshots/<breakpoint>/<name>.png
# plus a JSON manifest of measurements.
import asyncio
import json
from pathlib import Path
from playwright.async_api import async_playwright

ROOT = Path(__file__).parent
OUT = ROOT / "screenshots"
OUT.mkdir(parents=True, exist_ok=True)

BREAKPOINTS = [
    ("mobile",  390, 1800),
    ("tablet",  820, 1800),
    ("desktop", 1280, 1800),
]

BASE = "http://localhost:8080"

async def measure(page):
    """Read layout facts we care about — orbit fit, map size, H1 font-size."""
    return await page.evaluate("""
    () => {
      const orbit = document.querySelector('[data-visual="orbit"]')
                 || document.querySelector('.will-change-transform');
      const orbitRoot = document.querySelector('[data-orbit-root]');
      const orbitParts = [...document.querySelectorAll('[data-orbit-ring], [data-orbit-item]')];
      const map   = document.querySelector('[data-visual="map"] svg')
                 || document.querySelector('svg[viewBox]');
      const h1    = document.querySelector('h1');
      const rect  = (el) => el ? el.getBoundingClientRect().toJSON() : null;
      const cs    = (el) => el ? getComputedStyle(el) : null;
      return {
        viewport:   { w: innerWidth, h: innerHeight, dpr: devicePixelRatio },
        h1:         { rect: rect(h1),
                       fontSize: cs(h1)?.fontSize,
                       lineHeight: cs(h1)?.lineHeight },
        orbit:      {
          rect: rect(orbit),
          root: rect(orbitRoot),
          parts: orbitParts.map(rect),
        },
        map:        { rect: rect(map) },
        docHeight:  document.documentElement.scrollHeight,
      };
    }
    """)

async def shot(page, name, out_dir, selector=None):
    target = page.locator(selector) if selector else page
    path = out_dir / f"{name}.png"
    if selector:
        await target.first.screenshot(path=str(path))
    else:
        await target.screenshot(path=str(path))
    return str(path.relative_to(ROOT))

async def run_bp(browser, label, w, h):
    bp_out = OUT / label
    bp_out.mkdir(exist_ok=True)
    context = await browser.new_context(viewport={"width": w, "height": h})
    page = await context.new_page()
    await page.goto(BASE, wait_until="networkidle")
    # Freeze animations so screenshots are deterministic.
    await page.add_style_tag(content="""
      *, *::before, *::after {
        animation-duration: 0s !important;
        animation-delay: 0s !important;
        transition-duration: 0s !important;
      }
    """)
    await page.wait_for_timeout(400)

    manifest = {"breakpoint": label, "shots": {}, "layout": {}}

    # Hero + typography
    manifest["shots"]["hero"] = await shot(page, "hero", bp_out)
    manifest["layout"]["hero"] = await measure(page)

    # Scroll to orbit
    await page.evaluate("""() => {
      const el = document.querySelector('[data-visual="orbit"]');
      if (el) el.scrollIntoView({block:'center', behavior:'instant'});
    }""")
    await page.wait_for_timeout(600)
    manifest["shots"]["orbit"] = await shot(page, "orbit", bp_out)
    manifest["layout"]["orbit"] = await measure(page)

    # Scroll to map
    await page.evaluate("""() => {
      const el = document.querySelector('[data-visual="map"]');
      if (el) el.scrollIntoView({block:'center', behavior:'instant'});
    }""")
    await page.wait_for_timeout(900)  # let lazy chunk mount
    manifest["shots"]["map"] = await shot(page, "map", bp_out)
    manifest["layout"]["map"] = await measure(page)

    # Full page (long, but useful for parallax section flow)
    await page.evaluate("() => scrollTo(0,0)")
    await page.wait_for_timeout(200)
    await page.screenshot(path=str(bp_out / "full-top.png"))

    # Assertions — hard fails on regressions we care about.
    m = manifest["layout"]
    assert m["orbit"]["orbit"]["rect"]["width"] <= w + 1, \
        f"[{label}] orbit overflows viewport ({m['orbit']['orbit']['rect']['width']} > {w})"
    orbit_layout = m["orbit"]["orbit"]
    root = orbit_layout["root"]
    if root:
        epsilon = 2
        for part in orbit_layout["parts"]:
            assert part["left"] >= root["left"] - epsilon, \
                f"[{label}] orbit marker escapes left edge"
            assert part["right"] <= root["right"] + epsilon, \
                f"[{label}] orbit marker escapes right edge"
            assert part["top"] >= root["top"] - epsilon, \
                f"[{label}] orbit marker escapes top edge"
            assert part["bottom"] <= root["bottom"] + epsilon, \
                f"[{label}] orbit marker escapes bottom edge"
    if m["map"]["map"]["rect"]:
        assert m["map"]["map"]["rect"]["width"] <= w + 1, \
            f"[{label}] map overflows viewport"

    (bp_out / "manifest.json").write_text(json.dumps(manifest, indent=2))
    await context.close()
    return manifest

async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        results = []
        for label, w, h in BREAKPOINTS:
            print(f"→ {label} @ {w}x{h}")
            results.append(await run_bp(browser, label, w, h))
        await browser.close()
    (OUT / "index.json").write_text(json.dumps(results, indent=2))
    print(f"✓ wrote {len(results)} breakpoint manifests to {OUT}")

asyncio.run(main())
