ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS username TEXT,
  ADD COLUMN IF NOT EXISTS username_canonical TEXT,
  ADD COLUMN IF NOT EXISTS username_changed_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS profiles_username_canonical_key
  ON public.profiles (username_canonical);

ALTER TABLE public.profiles
  DROP CONSTRAINT IF EXISTS profiles_username_format_chk;
ALTER TABLE public.profiles
  ADD CONSTRAINT profiles_username_format_chk
  CHECK (username IS NULL OR username ~ '^[A-Za-z][A-Za-z0-9_-]{2,19}$');

ALTER TABLE public.profiles
  DROP CONSTRAINT IF EXISTS profiles_username_canonical_chk;
ALTER TABLE public.profiles
  ADD CONSTRAINT profiles_username_canonical_chk
  CHECK (username_canonical IS NULL OR username_canonical ~ '^[a-z0-9]{3,20}$');