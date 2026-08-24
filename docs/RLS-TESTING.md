# RLS / IDOR testing

Two layers guard our Row-Level Security:

1. **Text tests** (`tests/unit/*.test.ts`) — regex over the migration SQL.
   They catch policy-*shape* regressions (a dropped `REVOKE`, a missing
   `WITH CHECK`, a trigger that stops freezing a column). They run everywhere,
   need no database, and gate every commit. But they only prove the SQL *says*
   the right thing — not that Postgres *enforces* it.

2. **Real RLS/IDOR tests** (`tests/rls/*.test.ts`) — run the actual policies
   against a local `supabase start` stack with real GoTrue JWTs. User B tries
   to read/mutate User A's rows and must be denied by the database. This is the
   layer that catches a policy that parses fine but doesn't actually block the
   cross-user access (a genuine IDOR).

## Running the real tests

Requires Docker.

```bash
supabase start            # boots Postgres + GoTrue + PostgREST locally,
                          # applies every migration in supabase/migrations/
npm run test:rls          # runs tests/rls/ against 127.0.0.1:54321
supabase stop             # tear down
```

The suite auto-**skips** (never fails) when the stack isn't reachable, so
`npm test` stays green in CI/dev without Docker. The default anon/service keys
in `tests/rls/helpers.ts` are the Supabase CLI's fixed local demo keys — public,
local-only, not secrets. Override via `SUPABASE_TEST_URL` /
`SUPABASE_TEST_ANON_KEY` / `SUPABASE_TEST_SERVICE_KEY` if your ports differ.

### Caveat: email confirmation

The local `config.toml` sets `enable_confirmations = false` so tests get a
session on sign-up. **The hosted project keeps confirmation ON**
(`mailer_autoconfirm: false`) — do not infer production auth behavior from the
local stack. This only affects the throwaway test users.

## What's covered

`tests/rls/idor.test.ts` asserts, with real JWTs:

- anon cannot read `profiles`
- User B cannot UPDATE User A's `participants` scoring (integrity/breached)
- even the owner cannot forge their own `participants.integrity` (column-freeze
  trigger)
- a non-host cannot change a `rooms` lifecycle
- a blocked user cannot create a `friendships` row with the blocker

## Write-policy audit (P2-22)

Snapshot of every non-SELECT RLS policy, resolved latest-wins across all
migrations (2026-08-24). Every write is gated by an authority predicate — there
is **no `WITH CHECK (true)` on any INSERT/UPDATE**, and every DELETE `USING`
clause is owner/host-scoped. The only `USING (true)` policies are SELECT reads
(leaderboard/room-lookup/roster visibility); `profiles` reads are further
restricted by column-level `GRANT SELECT` (see 20260822051951).

| Table | Cmd | Authority |
|---|---|---|
| breaks | INSERT | `auth.uid() = user_id` |
| email_* / suppressed_emails | INSERT/UPDATE/ALL | `auth.role() = 'service_role'` |
| focus_groups | INSERT/UPDATE/DELETE | `auth.uid() = created_by` |
| friendships | INSERT | `auth.uid() = requester_id AND status='pending'` |
| friendships | DELETE | `auth.uid() IN (requester_id, addressee_id)` |
| group_members | INSERT | member of the group's `focus_groups` row |
| group_members | DELETE | self-leave or group creator |
| memory_vault_items | ALL | `auth.uid() = user_id AND has_tier('elite')` |
| mentor_relationships | ALL/DELETE | `auth.uid() IN (mentor_id, mentee_id)` |
| participants | INSERT | `auth.uid() = user_id` |
| profiles | INSERT/UPDATE | `auth.uid() = id` |
| room_join_requests | INSERT | `auth.uid() = user_id AND status='pending'` |
| room_join_requests | UPDATE | requester or room moderator |
| room_moderators | INSERT/DELETE | `is_room_host(room_id, auth.uid())` |
| room_scheduled_events | ALL | room moderator AND `created_by = auth.uid()` |
| rooms | INSERT/UPDATE/DELETE | `auth.uid() = host_id` |
| session_reactions | INSERT | `user_id = auth.uid()` AND session exists |
| session_reactions | DELETE | `user_id = auth.uid()` |
| session_workspace_items | ALL | `user_id = auth.uid()` |
| time_capsules | ALL | `auth.uid() = user_id AND has_tier('elite')` |
| user_blocks | INSERT/DELETE | `auth.uid() = blocker_id` |
| user_reports | INSERT | `auth.uid() = reporter_id` |
| user_reports | UPDATE | room owner of the reported room |
| user_titles | DELETE | `auth.uid() = user_id` |
| webhooks | ALL | `auth.uid() = user_id` |

Note: column-freeze triggers (participants integrity, rooms lifecycle, profile
scoring) enforce *which columns* a legitimate owner may change — the RLS above
governs *which rows*. Both layers are exercised by the tests in `tests/rls/`.

## Manual IDOR checklist (pre-release, against staging)

For tables/flows not yet in the automated suite, spot-check as a second,
non-owner user:

- [ ] `memory_vault_items` / `time_capsules` — non-owner (and non-elite) gets 0 rows
- [ ] `room_join_requests` — a requester cannot self-approve; a non-moderator cannot approve
- [ ] `season_participants` — direct SELECT returns only your own row (standings go through the RPC)
- [ ] `webhooks` / `webhook_deliveries` — scoped to `user_id`; no cross-user reads
- [ ] `user_reports` — reporter sees only their own reports
- [ ] `friendships` — only the addressee can accept; reciprocal-pair uniqueness holds
- [ ] every new SECURITY DEFINER RPC — `EXECUTE` revoked from `anon`
