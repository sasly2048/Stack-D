# Core Web Vitals harness — LCP, CLS, INP proxy, TTFB, long tasks.
#
#   python3 tests/perf/run.py                     # against the dev server
#   BASE_URL=https://stack-d.lovable.app python3 tests/perf/run.py
#   PERF_STRICT=1 ...                             # enforce production budgets
#
# Dev-server numbers are inflated by unminified modules and HMR, so budgets
# are only enforced with PERF_STRICT=1 (CI runs it against a preview build).
# Without it the script reports and always exits 0.
import asyncio
import json
import os
import sys
from pathlib import Path

from playwright.async_api import async_playwright

BASE = os.environ.get("BASE_URL", "http://localhost:8080")
STRICT = bool(os.environ.get("PERF_STRICT"))
OUT = Path(__file__).parent / "report"
OUT.mkdir(parents=True, exist_ok=True)

ROUTES = ["/", "/philosophy", "/auth"]

# "Good" thresholds from web.dev, with headroom for a throttled CI runner.
BUDGETS = {
    "lcp_ms": 2500,
    "cls": 0.1,
    "ttfb_ms": 800,
    "long_tasks_ms": 600,  # total main-thread blocking after load
}

COLLECT = """
() => new Promise((resolve) => {
  const out = { lcp_ms: 0, cls: 0, ttfb_ms: 0, long_tasks_ms: 0, transfer_kb: 0, requests: 0 };

  try {
    new PerformanceObserver((l) => {
      for (const e of l.getEntries()) out.lcp_ms = Math.round(e.startTime);
    }).observe({ type: 'largest-contentful-paint', buffered: true });
  } catch {}

  try {
    new PerformanceObserver((l) => {
      for (const e of l.getEntries()) if (!e.hadRecentInput) out.cls += e.value;
    }).observe({ type: 'layout-shift', buffered: true });
  } catch {}

  try {
    new PerformanceObserver((l) => {
      for (const e of l.getEntries()) out.long_tasks_ms += e.duration;
    }).observe({ type: 'longtask', buffered: true });
  } catch {}

  setTimeout(() => {
    const nav = performance.getEntriesByType('navigation')[0];
    if (nav) out.ttfb_ms = Math.round(nav.responseStart);
    const res = performance.getEntriesByType('resource');
    out.requests = res.length;
    out.transfer_kb = Math.round(res.reduce((s, r) => s + (r.transferSize || 0), 0) / 1024);
    out.cls = Number(out.cls.toFixed(4));
    out.long_tasks_ms = Math.round(out.long_tasks_ms);
    resolve(out);
  }, 4000);
})
"""


async def measure(page, route: str) -> dict:
    await page.goto(f"{BASE}{route}", wait_until="domcontentloaded")
    # Nudge the page so deferred/idle work (smooth scroll boot, lazy FX) runs.
    await page.mouse.wheel(0, 600)
    return await page.evaluate(COLLECT)


async def main() -> int:
    results: dict[str, dict] = {}
    failures: list[str] = []

    async with async_playwright() as pw:
        browser = await pw.chromium.launch(headless=True)
        context = await browser.new_context(
            viewport={"width": 1280, "height": 900},
            # Mid-tier mobile-ish CPU pressure isn't available without CDP
            # throttling; keep the device profile honest instead.
            device_scale_factor=2,
        )
        page = await context.new_page()

        for route in ROUTES:
            m = await measure(page, route)
            results[route] = m
            print(
                f"\n{route}\n"
                f"  LCP        {m['lcp_ms']:>6} ms\n"
                f"  CLS        {m['cls']:>6}\n"
                f"  TTFB       {m['ttfb_ms']:>6} ms\n"
                f"  long tasks {m['long_tasks_ms']:>6} ms\n"
                f"  transfer   {m['transfer_kb']:>6} KiB over {m['requests']} request(s)"
            )
            if STRICT:
                for key, budget in BUDGETS.items():
                    if m[key] > budget:
                        failures.append(f"{route} {key}={m[key]} > {budget}")

        await browser.close()

    (OUT / "vitals.json").write_text(json.dumps(results, indent=2))
    print("\n" + "=" * 56)
    print(f"perf: {len(ROUTES)} route(s) measured — report at {OUT / 'vitals.json'}")
    if failures:
        print("BUDGET FAILURES:")
        for f in failures:
            print(f"  {f}")
        return 1
    if not STRICT:
        print("(advisory run — set PERF_STRICT=1 to enforce budgets)")
    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
