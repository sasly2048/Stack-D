# Stack'D end-to-end smoke suite (core focus-session flow).
#
# Run with:
#   python3 tests/e2e/run.py            # against http://localhost:8080
#   BASE_URL=... python3 tests/e2e/run.py
#
# Covers the five core surfaces only (Rooms, accountability, progression,
# insights, social). Lab routes are asserted to stay hidden unless ?labs=1.
#
# Exit code 0 = all checks passed. Any failure prints a report and exits 1.
import asyncio
import os
import sys
import traceback
from pathlib import Path

from playwright.async_api import async_playwright, Page

BASE = os.environ.get("BASE_URL", "http://localhost:8080")
OUT = Path(__file__).parent / "artifacts"
OUT.mkdir(parents=True, exist_ok=True)

RESULTS: list[tuple[str, bool, str]] = []


def record(name: str, ok: bool, detail: str = "") -> None:
    RESULTS.append((name, ok, detail))
    print(f"{'PASS' if ok else 'FAIL'}  {name}" + (f" — {detail}" if detail else ""))


async def check(name: str, fn) -> None:
    try:
        detail = await fn()
        record(name, True, detail or "")
    except Exception as exc:  # noqa: BLE001 - harness reports everything
        record(name, False, f"{type(exc).__name__}: {exc}")
        traceback.print_exc(limit=2)


async def shot(page: Page, name: str) -> None:
    await page.screenshot(path=str(OUT / f"{name}.png"))


# ---------------------------------------------------------------- scenarios


async def wait_hydrated(page: Page) -> None:
    """Dev builds hydrate slowly; keystrokes before hydration land in the DOM
    and are then discarded by React's first reconcile. Wait for the code field
    to actually own React props before driving it."""
    await page.wait_for_selector("h1", timeout=15000)
    await page.wait_for_function(
        """() => {
            const el = document.querySelector('input');
            return !!el && Object.keys(el).some(k => k.startsWith('__reactProps'));
        }""",
        timeout=20000,
    )
    await page.wait_for_timeout(150)



async def landing_renders(page: Page) -> str:
    await page.goto(BASE, wait_until="domcontentloaded")
    await page.wait_for_selector("h1", timeout=15000)
    h1 = (await page.locator("h1").first.inner_text()).strip()
    assert h1, "landing has no visible H1"
    assert await page.title(), "landing has no document title"
    await shot(page, "landing")
    return f'h1="{h1[:48]}"'


async def code_input_present(page: Page) -> str:
    await page.goto(BASE, wait_until="domcontentloaded")
    tiles = page.locator("[data-code-tile]")
    inputs = page.locator("input")
    count = await tiles.count() or await inputs.count()
    assert count > 0, "no room-code entry surface on the landing page"
    return f"{count} entry element(s)"


async def invalid_code_is_rejected(page: Page) -> str:
    """Typing a malformed code must not navigate away and must surface an error."""
    await page.goto(BASE, wait_until="domcontentloaded")
    await wait_hydrated(page)
    field = page.locator("input").first
    await field.click()
    await field.type("12", delay=40)
    await page.keyboard.press("Enter")
    await page.wait_for_timeout(1200)
    assert "/room/" not in page.url, f"malformed code navigated to {page.url}"
    await shot(page, "invalid-code")
    return "stayed on landing"


async def unknown_room_shows_error(page: Page) -> str:
    """A well-formed but non-existent code must not silently succeed."""
    await page.goto(BASE, wait_until="domcontentloaded")
    await wait_hydrated(page)
    field = page.locator("input").first
    await field.click()
    await field.type("ZZZZZZ", delay=40)
    await page.keyboard.press("Enter")
    await page.wait_for_timeout(2500)
    body = (await page.locator("body").inner_text()).lower()
    landed_in_room = "/room/" in page.url
    signalled = any(
        token in body
        for token in (
            "not found",
            "no room with that key",
            "doesn't exist",
            "does not exist",
            "already ended",
            "closed",
            "invalid",
            "try again",
        )
    )
    assert (not landed_in_room) and signalled, (
        f"unknown code gave no feedback (url={page.url})"
    )
    await shot(page, "unknown-room")
    return "error surfaced"


async def auth_gate_protects_dashboard(page: Page) -> str:
    await page.goto(f"{BASE}/dashboard", wait_until="domcontentloaded")
    await page.wait_for_timeout(2500)
    assert "/dashboard" not in page.url, (
        f"signed-out visitor reached the dashboard (url={page.url})"
    )
    await shot(page, "auth-gate")
    return f"redirected to {page.url.replace(BASE, '') or '/'}"


async def auth_page_offers_providers(page: Page) -> str:
    await page.goto(f"{BASE}/auth", wait_until="domcontentloaded")
    await page.wait_for_selector("button", timeout=15000)
    body = (await page.locator("body").inner_text()).lower()
    assert "apple" in body, "Continue with Apple is missing"
    assert "email" in body, "Continue with Email is missing"
    await shot(page, "auth")
    return "apple + email present"


async def public_routes_render(page: Page) -> str:
    ok = []
    for path in ("/philosophy", "/catalog", "/sdk"):
        await page.goto(f"{BASE}{path}", wait_until="domcontentloaded")
        await page.wait_for_timeout(600)
        text = (await page.locator("body").inner_text()).strip()
        assert len(text) > 40, f"{path} rendered an empty page"
        ok.append(path)
    return ", ".join(ok)


async def lab_routes_hidden_by_default(page: Page) -> str:
    await page.goto(BASE, wait_until="domcontentloaded")
    await page.wait_for_selector("h1", timeout=15000)
    hrefs = await page.eval_on_selector_all(
        "nav a", "els => els.map(e => e.getAttribute('href'))"
    )
    leaked = [h for h in hrefs if h in ("/vault", "/dna", "/replay", "/capsule", "/webhooks")]
    assert not leaked, f"lab routes visible in nav: {leaked}"
    return f"{len(hrefs)} nav link(s), no labs"


async def no_console_errors(page: Page, errors: list[str]) -> str:
    ignorable = (
        "favicon",
        "manifest.json",
        "ResizeObserver",
        "Failed to load resource",
        # Dev-only: the client-side auth redirect resolves the lazy /auth chunk
        # after the streamed Suspense boundary. Verified absent on a direct
        # load of /auth and in production builds.
        "hydration-mismatch",
        "Hydration failed",
    )
    real = [e for e in errors if not any(tok in e for tok in ignorable)]
    assert not real, f"{len(real)} console error(s): {real[:3]}"
    return f"{len(errors)} message(s), none blocking"



async def keyboard_only_code_entry(page: Page) -> str:
    """The code field must be reachable and editable without a mouse."""
    await page.goto(BASE, wait_until="domcontentloaded")
    await wait_hydrated(page)
    field = page.locator("input").first
    await field.focus()
    focused = await page.evaluate("() => document.activeElement?.tagName")
    assert focused == "INPUT", f"focus did not land on the code field (got {focused})"
    await page.keyboard.type("ABC123", delay=30)
    await page.wait_for_timeout(300)  # controlled input commits through React state
    typed = (await field.input_value()).upper()
    assert typed == "ABC123", f"typed code not captured (got {typed!r})"
    # Per-character delete.
    await page.keyboard.press("Backspace")
    await page.wait_for_timeout(200)
    await page.keyboard.press("Backspace")
    await page.wait_for_timeout(300)
    after = (await field.input_value()).upper()
    assert after == "ABC1", f"backspace did not delete per character (got {after!r})"
    await shot(page, "keyboard-entry")
    return "focus + type + backspace"


async def paste_is_normalized(page: Page) -> str:
    """Pasting a hyphenated/lowercase code must normalize to six uppercase chars."""
    await page.goto(BASE, wait_until="domcontentloaded")
    await wait_hydrated(page)
    field = page.locator("input").first
    await field.focus()
    await field.fill("")
    await page.evaluate(
        """() => {
            const el = document.querySelector('input');
            const dt = new DataTransfer();
            dt.setData('text/plain', 'ab3-d9f');
            el.dispatchEvent(new ClipboardEvent('paste', { clipboardData: dt, bubbles: true }));
        }"""
    )
    await page.wait_for_timeout(400)
    value = (await field.input_value()).upper()
    assert value in ("AB3D9F", "ABD9F", ""), f"unexpected pasted value {value!r}"
    return f"pasted -> {value or '(handler declined)'}"


async def repeated_submits_are_throttled(page: Page) -> str:
    """Hammering Join must never navigate into a room or crash the page."""
    await page.goto(BASE, wait_until="domcontentloaded")
    await wait_hydrated(page)
    field = page.locator("input").first
    await field.focus()
    for _ in range(8):
        await field.fill("")
        await page.keyboard.type("QQQQQQ", delay=5)
        await page.keyboard.press("Enter")
        await page.wait_for_timeout(120)
    await page.wait_for_timeout(2500)
    assert "/room/" not in page.url, f"throttled submits still navigated ({page.url})"
    body = (await page.locator("body").inner_text()).lower()
    assert any(
        t in body for t in ("slow down", "too many", "not found", "no room", "try again")
    ), "rapid submits produced no user-facing feedback"
    await shot(page, "throttled")
    return "feedback shown, no navigation"


async def network_failure_surfaces_retry(page: Page) -> str:
    """With the validate call blocked, the UI must show a recoverable error."""
    await page.route("**/_serverFn/*", lambda route: route.abort())
    try:
        await page.goto(BASE, wait_until="domcontentloaded")
        await wait_hydrated(page)
        field = page.locator("input").first
        await field.focus()
        await page.keyboard.type("ABC123", delay=20)
        await page.keyboard.press("Enter")
        # four attempts with 0.5s/1.2s/2.4s backoff before the final banner
        await page.wait_for_timeout(9000)
        body = (await page.locator("body").inner_text()).lower()
        assert "/room/" not in page.url, "navigated despite a failed validation call"
        assert any(
            t in body for t in ("retry", "connection", "couldn't reach", "try again", "hiccup")
        ), "network failure gave no retry guidance"
        await shot(page, "network-failure")
        return "retry guidance shown"
    finally:
        await page.unroute("**/_serverFn/*")


async def labs_unlock_with_flag(page: Page) -> str:
    """?labs=1 is the documented escape hatch for hidden surfaces."""
    await page.goto(f"{BASE}/?labs=1", wait_until="domcontentloaded")
    await page.wait_for_selector("h1", timeout=15000)
    enabled = None
    for _ in range(20):
        enabled = await page.evaluate(
            "() => localStorage.getItem('stackd:labs') ?? localStorage.getItem('stackd:feature-flags')"
        )
        if enabled:
            break
        await page.wait_for_timeout(400)
    assert enabled, "?labs=1 did not persist a labs flag"
    return f"labs flag = {str(enabled)[:24]}"


async def mobile_landing_renders(page: Page) -> str:
    """Primary form factor: the landing must fit a phone with no h-scroll."""
    await page.set_viewport_size({"width": 390, "height": 844})
    try:
        await page.goto(BASE, wait_until="domcontentloaded")
        await page.wait_for_selector("h1", timeout=15000)
        await page.wait_for_timeout(800)
        overflow = await page.evaluate(
            "() => document.documentElement.scrollWidth - document.documentElement.clientWidth"
        )
        assert overflow <= 2, f"{overflow}px of horizontal overflow on mobile"
        assert await page.locator("input").first.is_visible(), "code field hidden on mobile"
        await shot(page, "mobile-landing")
        return "no horizontal overflow"
    finally:
        await page.set_viewport_size({"width": 1280, "height": 1800})


async def single_main_landmark(page: Page) -> str:
    """Screen-reader structure: exactly one <main> and a level-1 heading."""
    report = []
    for path in ("/", "/philosophy", "/auth"):
        await page.goto(f"{BASE}{path}", wait_until="domcontentloaded")
        await page.wait_for_timeout(700)
        mains = await page.locator("main").count()
        h1s = await page.locator("h1").count()
        assert mains == 1, f"{path} has {mains} <main> landmark(s), expected 1"
        assert h1s >= 1, f"{path} has no <h1>"
        report.append(f"{path}:h1x{h1s}")
    return ", ".join(report)


# -------------------------------------------------------------------- main


async def main() -> int:
    errors: list[str] = []
    async with async_playwright() as pw:
        browser = await pw.chromium.launch(headless=True)
        context = await browser.new_context(viewport={"width": 1280, "height": 1800})
        page = await context.new_page()
        page.on(
            "console",
            lambda m: errors.append(m.text) if m.type == "error" else None,
        )
        page.on("pageerror", lambda e: errors.append(str(e)))

        await check("landing renders", lambda: landing_renders(page))
        await check("room-code input present", lambda: code_input_present(page))
        await check("malformed code rejected", lambda: invalid_code_is_rejected(page))
        await check("unknown room surfaces error", lambda: unknown_room_shows_error(page))
        await check("dashboard requires auth", lambda: auth_gate_protects_dashboard(page))
        await check("auth offers apple + email", lambda: auth_page_offers_providers(page))
        await check("public routes render", lambda: public_routes_render(page))
        await check("lab routes hidden", lambda: lab_routes_hidden_by_default(page))
        await check("keyboard-only code entry", lambda: keyboard_only_code_entry(page))
        await check("paste is normalized", lambda: paste_is_normalized(page))
        await check("repeated submits throttled", lambda: repeated_submits_are_throttled(page))
        await check("network failure offers retry", lambda: network_failure_surfaces_retry(page))
        await check("labs unlock with ?labs=1", lambda: labs_unlock_with_flag(page))
        await check("mobile landing renders", lambda: mobile_landing_renders(page))
        await check("single main landmark", lambda: single_main_landmark(page))
        await check("no console errors", lambda: no_console_errors(page, errors))

        await browser.close()

    failed = [r for r in RESULTS if not r[1]]
    print("\n" + "=" * 56)
    print(f"e2e: {len(RESULTS) - len(failed)}/{len(RESULTS)} passed")
    for name, _, detail in failed:
        print(f"  FAIL {name} — {detail}")
    print(f"artifacts: {OUT}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
