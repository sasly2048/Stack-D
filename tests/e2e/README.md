# End-to-end smoke suite

Playwright (Python) checks for the core focus-session flow. The suite is
deliberately small: it guards the paths a regression would actually hurt —
landing render, room-code validation, the auth gate, provider buttons,
public routes, and the core-vs-lab route split.

## Run locally

```bash
bun run dev                 # dev server on :8080
python3 tests/e2e/run.py    # or: bun run test:e2e
```

Point it elsewhere with `BASE_URL=https://stack-d.lovable.app python3 tests/e2e/run.py`.

Screenshots for every scenario land in `tests/e2e/artifacts/` (git-ignored)
and are uploaded as CI artifacts on failure.

## Adding a scenario

1. Write an `async def my_scenario(page) -> str` that raises on failure and
   returns a short detail string on success.
2. Register it in `main()` with `await check("name", lambda: my_scenario(page))`.

Prefer role/text selectors over CSS classes so styling changes don't break tests.

## Authenticated flows

Not covered here — the suite runs signed out so it can execute on CI without
credentials. Authenticated session behaviour is covered by unit tests around
the scoring engine (`tests/unit/focus-score.test.ts`) and by the server-side
RPC guards (`finalize_focus_session`).
