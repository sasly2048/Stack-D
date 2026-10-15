-- =========================================================
-- Per-user cache for passive AI surfaces (tier-capped AI)
-- =========================================================
-- Recommend, dashboard insights, proactive insights and the session recap are
-- *passive*: they render on page load, not on a user action. Metering them
-- against the monthly AI allowance (ai_meter) would drain a Pro user's 20
-- actions in ~10 dashboard visits and then lock them out of the Companion.
--
-- Instead they are tier-GATED (free → deterministic copy, paid → AI) and each
-- generated answer is cached per user here, keyed by a `scope` that changes
-- when the underlying data does (latest session timestamp, or the room id for
-- a recap). A paid user therefore costs at most one generation per new
-- session per surface, and page views are free.
--
-- Own-row RLS: a user can only read and write their own cached answers.

CREATE TABLE IF NOT EXISTS public.ai_response_cache (
  user_id    UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  kind       TEXT        NOT NULL CHECK (kind IN ('recommend', 'dashboard_insights', 'proactive', 'session_recap')),
  scope      TEXT        NOT NULL DEFAULT '',
  payload    JSONB       NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, kind, scope)
);

ALTER TABLE public.ai_response_cache ENABLE ROW LEVEL SECURITY;

GRANT SELECT, INSERT, UPDATE, DELETE ON public.ai_response_cache TO authenticated;
REVOKE ALL ON public.ai_response_cache FROM anon;

DROP POLICY IF EXISTS "Own AI cache read" ON public.ai_response_cache;
CREATE POLICY "Own AI cache read" ON public.ai_response_cache
  FOR SELECT TO authenticated USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Own AI cache write" ON public.ai_response_cache;
CREATE POLICY "Own AI cache write" ON public.ai_response_cache
  FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Own AI cache update" ON public.ai_response_cache;
CREATE POLICY "Own AI cache update" ON public.ai_response_cache
  FOR UPDATE TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Own AI cache delete" ON public.ai_response_cache;
CREATE POLICY "Own AI cache delete" ON public.ai_response_cache
  FOR DELETE TO authenticated USING (auth.uid() = user_id);
