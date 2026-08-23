-- =========================================================
-- P0 #8: memory_vault_items + time_capsules — enforce the Elite entitlement
--        in RLS, not only in the server functions
-- =========================================================
-- Both features are Elite-only (premium-catalog.ts: requiredTier "elite",
-- serverGate true). The server functions (memory-vault.functions.ts,
-- capsules.functions.ts) call requireFeature("vault"/"time_capsules"), but that
-- gate lives in the TanStack server layer only. A user holding just their own
-- JWT + the public anon key can hit PostgREST directly
-- (e.g. POST /rest/v1/memory_vault_items) and completely bypass it — the
-- original policies checked only `auth.uid() = user_id`, so any authenticated
-- user (free / pro) could read and write their own vault + capsules.
--
-- Fix: make the row's owner AND the Elite entitlement both required, at the RLS
-- level. has_tier('elite') is SECURITY DEFINER + granted to authenticated, so
-- it evaluates safely inside a policy (admin/lifetime satisfy 'elite' per its
-- own definition). Existing Elite owners keep full access; everyone else is
-- blocked at the database, matching the server gate.
--
-- Idempotent: policies are dropped-if-exists then recreated.

-- ---- memory_vault_items ----
DROP POLICY IF EXISTS "vault owner only" ON public.memory_vault_items;
CREATE POLICY "vault owner + elite" ON public.memory_vault_items
  FOR ALL TO authenticated
  USING (auth.uid() = user_id AND public.has_tier('elite'))
  WITH CHECK (auth.uid() = user_id AND public.has_tier('elite'));

-- ---- time_capsules ----
-- Split policies (read / insert / update / delete) collapse into one FOR ALL:
-- the tier + owner predicate is identical for every verb, and a single policy
-- is easier to audit than four that must stay in sync.
DROP POLICY IF EXISTS "own capsules read"   ON public.time_capsules;
DROP POLICY IF EXISTS "own capsules write"  ON public.time_capsules;
DROP POLICY IF EXISTS "own capsules update" ON public.time_capsules;
DROP POLICY IF EXISTS "own capsules delete" ON public.time_capsules;
CREATE POLICY "capsule owner + elite" ON public.time_capsules
  FOR ALL TO authenticated
  USING (auth.uid() = user_id AND public.has_tier('elite'))
  WITH CHECK (auth.uid() = user_id AND public.has_tier('elite'));
