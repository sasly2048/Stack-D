# Automated accessibility audit — axe-core, WCAG 2.0/2.1 A + AA.
#
#   python3 tests/a11y/run.py            # audit every public surface
#   BASE_URL=... python3 tests/a11y/run.py
#   A11Y_REPORT=1 python3 tests/a11y/run.py   # write JSON report
#
# Fails (exit 1) on any `critical` or `serious` violation. `moderate` and
# `minor` findings are printed as advisories so regressions stay visible
# without blocking CI on subjective rules.
import asyncio
import json
import os
import sys
from pathlib import Path

from playwright.async_api import async_playwright

BASE = os.environ.get("BASE_URL", "http://localhost:8080")
ROOT = Path(__file__).resolve().parents[2]
AXE = ROOT / "node_modules" / "axe-core" / "axe.min.js"
OUT = Path(__file__).parent / "report"

# Signed-out surfaces. Authenticated routes redirect to /auth, so auditing
# them here would just re-audit the auth page.
ROUTES = ["/", "/philosophy", "/auth", "/sdk", "/catalog"]

BLOCKING = {"critical", "serious"}

AXE_OPTIONS = {
    "runOnly": {"type": "tag", "values": ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]},
    # Motion-heavy hero canvases repaint during the scan and make colour
    # sampling unreliable; contrast is covered by scripts/check-join-contrast.mjs.
    "rules": {"color-contrast": {"enabled": True}},
}


async def audit(page, route: str) -> dict:
    await page.goto(f"{BASE}{route}", wait_until="domcontentloaded")
    await page.wait_for_timeout(1500)  # let entrance animations settle
    await page.add_script_tag(path=str(AXE))
    return await page.evaluate(
        "async (opts) => { const r = await window.axe.run(document, opts);"
        " return { violations: r.violations.map(v => ({ id: v.id, impact: v.impact,"
        " help: v.help, helpUrl: v.helpUrl,"
        " nodes: v.nodes.slice(0, 4).map(n => ({ target: n.target, html: n.html.slice(0, 160) })),"
        " count: v.nodes.length })) }; }",
        AXE_OPTIONS,
    )


async def main() -> int:
    if not AXE.exists():
        print(f"axe-core not found at {AXE}. Run `bun install` first.")
        return 1

    report: dict[str, list] = {}
    blocking_total = 0
    advisory_total = 0

    async with async_playwright() as pw:
        browser = await pw.chromium.launch(headless=True)
        context = await browser.new_context(viewport={"width": 1280, "height": 1800})
        page = await context.new_page()

        for route in ROUTES:
            result = await audit(page, route)
            violations = result["violations"]
            report[route] = violations
            blocking = [v for v in violations if v["impact"] in BLOCKING]
            advisory = [v for v in violations if v["impact"] not in BLOCKING]
            blocking_total += len(blocking)
            advisory_total += len(advisory)

            status = "FAIL" if blocking else "PASS"
            print(f"\n{status}  {route}  ({len(blocking)} blocking, {len(advisory)} advisory)")
            for v in blocking:
                print(f"  [{v['impact']}] {v['id']} — {v['help']} ({v['count']} node(s))")
                for n in v["nodes"][:2]:
                    print(f"      {n['target']}  {n['html'][:110]}")
            for v in advisory:
                print(f"  · [{v['impact']}] {v['id']} — {v['help']} ({v['count']} node(s))")

        await browser.close()

    if os.environ.get("A11Y_REPORT"):
        OUT.mkdir(parents=True, exist_ok=True)
        (OUT / "axe.json").write_text(json.dumps(report, indent=2))
        print(f"\nreport: {OUT / 'axe.json'}")

    print("\n" + "=" * 56)
    print(
        f"a11y: {len(ROUTES)} route(s) audited — "
        f"{blocking_total} blocking, {advisory_total} advisory"
    )
    return 1 if blocking_total else 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
