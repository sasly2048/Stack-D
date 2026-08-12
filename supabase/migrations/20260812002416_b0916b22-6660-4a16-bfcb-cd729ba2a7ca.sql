CREATE TABLE public.moderation_list_versions (
  category TEXT PRIMARY KEY,
  version INTEGER NOT NULL DEFAULT 1,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
GRANT ALL ON public.moderation_list_versions TO service_role;
ALTER TABLE public.moderation_list_versions ENABLE ROW LEVEL SECURITY;

CREATE TABLE public.moderation_terms (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  category TEXT NOT NULL CHECK (category IN ('profanity','nsfw','slurs','reserved','impersonation')),
  term TEXT NOT NULL CHECK (term ~ '^[a-z0-9]{2,40}$'),
  match_mode TEXT NOT NULL DEFAULT 'word' CHECK (match_mode IN ('exact','word','substring')),
  list_version INTEGER NOT NULL DEFAULT 1,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (category, term)
);
CREATE INDEX moderation_terms_active_idx ON public.moderation_terms (active, category);
GRANT ALL ON public.moderation_terms TO service_role;
ALTER TABLE public.moderation_terms ENABLE ROW LEVEL SECURITY;
CREATE TRIGGER moderation_terms_updated_at BEFORE UPDATE ON public.moderation_terms
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TABLE public.moderation_allowlist (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  canonical TEXT NOT NULL UNIQUE CHECK (canonical ~ '^[a-z0-9]{2,40}$'),
  reason TEXT,
  list_version INTEGER NOT NULL DEFAULT 1,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
GRANT ALL ON public.moderation_allowlist TO service_role;
ALTER TABLE public.moderation_allowlist ENABLE ROW LEVEL SECURITY;
CREATE TRIGGER moderation_allowlist_updated_at BEFORE UPDATE ON public.moderation_allowlist
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TABLE public.username_moderation_events (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id UUID,
  attempted TEXT NOT NULL,
  canonical TEXT,
  decision TEXT NOT NULL CHECK (decision IN ('allowed','rejected','error')),
  reason TEXT,
  category TEXT,
  matched_term TEXT,
  match_mode TEXT,
  matched_form TEXT,
  confidence NUMERIC,
  list_version INTEGER,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX username_moderation_events_created_idx ON public.username_moderation_events (created_at DESC);
GRANT ALL ON public.username_moderation_events TO service_role;
ALTER TABLE public.username_moderation_events ENABLE ROW LEVEL SECURITY;