-- =========================================================
-- RATE LIMITS (persistent, sliding-window)
-- =========================================================
CREATE TABLE public.rate_limits (
  key TEXT NOT NULL PRIMARY KEY,
  hits TIMESTAMPTZ[] NOT NULL DEFAULT ARRAY[]::TIMESTAMPTZ[],
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

GRANT ALL ON public.rate_limits TO service_role;

ALTER TABLE public.rate_limits ENABLE ROW LEVEL SECURITY;
-- No policies: only service_role (which bypasses RLS) accesses this table.

-- Atomic record + check. Returns TRUE when the caller exceeded the limit.
CREATE OR REPLACE FUNCTION public.check_and_record_hit(
  _key TEXT,
  _window_seconds INT,
  _max_hits INT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  _now TIMESTAMPTZ := now();
  _cutoff TIMESTAMPTZ := _now - make_interval(secs => _window_seconds);
  _new_hits TIMESTAMPTZ[];
BEGIN
  INSERT INTO public.rate_limits(key, hits)
  VALUES (_key, ARRAY[_now])
  ON CONFLICT (key) DO UPDATE
    SET hits = (
      SELECT COALESCE(array_agg(t ORDER BY t), ARRAY[]::TIMESTAMPTZ[])
      FROM unnest(public.rate_limits.hits) AS t
      WHERE t > _cutoff
    ) || _now,
    updated_at = _now
  RETURNING hits INTO _new_hits;
  RETURN COALESCE(array_length(_new_hits, 1), 0) > _max_hits;
END;
$$;

REVOKE ALL ON FUNCTION public.check_and_record_hit(TEXT, INT, INT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.check_and_record_hit(TEXT, INT, INT) FROM anon, authenticated;

-- =========================================================
-- AUTH ATTEMPTS (audit log)
-- =========================================================
CREATE TABLE public.auth_attempts (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  provider TEXT NOT NULL,
  email TEXT,
  success BOOLEAN NOT NULL DEFAULT false,
  reason TEXT,
  ip TEXT,
  user_agent TEXT,
  at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_auth_attempts_email_at ON public.auth_attempts(email, at DESC);
CREATE INDEX idx_auth_attempts_ip_at ON public.auth_attempts(ip, at DESC);
CREATE INDEX idx_auth_attempts_at ON public.auth_attempts(at DESC);

GRANT ALL ON public.auth_attempts TO service_role;

ALTER TABLE public.auth_attempts ENABLE ROW LEVEL SECURITY;
-- No policies: only service_role writes/reads this log.

-- Helper: count recent failures for a (provider, email) pair within a window.
CREATE OR REPLACE FUNCTION public.recent_auth_failures(
  _provider TEXT,
  _email TEXT,
  _window_seconds INT
)
RETURNS INT
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT COUNT(*)::INT
  FROM public.auth_attempts
  WHERE provider = _provider
    AND email = _email
    AND success = false
    AND at > now() - make_interval(secs => _window_seconds);
$$;

REVOKE ALL ON FUNCTION public.recent_auth_failures(TEXT, TEXT, INT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.recent_auth_failures(TEXT, TEXT, INT) FROM anon, authenticated;
