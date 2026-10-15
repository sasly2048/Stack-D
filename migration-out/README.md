# Migrating Stack'd to your own Supabase

`stackd_full_schema.sql` is all 50 migrations concatenated in order — the
complete database (tables, RLS, grants, RPCs, triggers, seed data) for a
fresh Supabase project you own. This replaces the Lovable-managed backend.

## Steps

1. **Create a project** — https://supabase.com → New project (free tier is fine).
   Pick a region near you; save the database password.

2. **Run the schema** — Dashboard → SQL Editor → New query → paste the whole
   `stackd_full_schema.sql` → Run. It creates everything, extensions included
   (pg_net, pg_cron, supabase_vault, pgmq — all free-tier).

3. **Get your keys** — Dashboard → Project Settings → API:
   - Project URL   → `SUPABASE_URL`
   - `anon` public key → `SUPABASE_ANON_KEY`

4. **Point the app at it** — edit `android/local.properties`:
   ```
   SUPABASE_URL=https://<your-ref>.supabase.co
   SUPABASE_ANON_KEY=<your anon key>
   ```

5. **Rebuild + install** — `./gradlew assembleDebug` then adb install.
   Sign up fresh (the old account lived in the Lovable DB; new project starts empty).

## Verify grants landed (the bug that started all this)

Dashboard → SQL Editor:
```sql
SELECT table_name, privilege_type
FROM information_schema.role_table_grants
WHERE grantee = 'authenticated' AND table_schema = 'public'
ORDER BY table_name;
```
`profiles`, `plans`, `subscriptions`, `challenges` must all show `SELECT`.
If any is missing, run the four GRANTs from the grant-audit.

## If a statement errors mid-run

The migrations were authored to run in sequence, so run the whole file at
once (not statement-by-statement). If one line fails on a fresh DB, note the
line + error — most likely an email/cron infra bit that needs a secret you
haven't set; those are safe to skip for the app to work.
