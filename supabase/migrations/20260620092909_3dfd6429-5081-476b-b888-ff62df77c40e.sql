
-- =========================================================
-- PROFILES
-- =========================================================
CREATE TABLE public.profiles (
  id UUID NOT NULL PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  display_name TEXT NOT NULL DEFAULT 'Anonymous',
  avatar_url TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

GRANT SELECT ON public.profiles TO anon;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.profiles TO authenticated;
GRANT ALL ON public.profiles TO service_role;

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Profiles are viewable by everyone"
  ON public.profiles FOR SELECT
  USING (true);

CREATE POLICY "Users can insert their own profile"
  ON public.profiles FOR INSERT
  TO authenticated
  WITH CHECK (auth.uid() = id);

CREATE POLICY "Users can update their own profile"
  ON public.profiles FOR UPDATE
  TO authenticated
  USING (auth.uid() = id)
  WITH CHECK (auth.uid() = id);

-- =========================================================
-- ROOMS
-- =========================================================
CREATE TYPE public.room_status AS ENUM ('lobby', 'active', 'complete', 'aborted');

CREATE TABLE public.rooms (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  code TEXT NOT NULL UNIQUE,
  host_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  target_duration_seconds INTEGER NOT NULL DEFAULT 1800 CHECK (target_duration_seconds BETWEEN 60 AND 28800),
  status public.room_status NOT NULL DEFAULT 'lobby',
  started_at TIMESTAMPTZ,
  ended_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_rooms_code ON public.rooms(code);
CREATE INDEX idx_rooms_host ON public.rooms(host_id);
CREATE INDEX idx_rooms_status ON public.rooms(status);

GRANT SELECT, INSERT, UPDATE, DELETE ON public.rooms TO authenticated;
GRANT ALL ON public.rooms TO service_role;

ALTER TABLE public.rooms ENABLE ROW LEVEL SECURITY;

-- Allow any authenticated user to read rooms (needed to look up by code).
-- Sensitive ops gated by other policies.
CREATE POLICY "Authenticated users can read rooms"
  ON public.rooms FOR SELECT
  TO authenticated
  USING (true);

CREATE POLICY "Authenticated users can create rooms as host"
  ON public.rooms FOR INSERT
  TO authenticated
  WITH CHECK (auth.uid() = host_id);

CREATE POLICY "Only host can update room"
  ON public.rooms FOR UPDATE
  TO authenticated
  USING (auth.uid() = host_id)
  WITH CHECK (auth.uid() = host_id);

CREATE POLICY "Only host can delete room"
  ON public.rooms FOR DELETE
  TO authenticated
  USING (auth.uid() = host_id);

-- =========================================================
-- PARTICIPANTS
-- =========================================================
CREATE TABLE public.participants (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  room_id UUID NOT NULL REFERENCES public.rooms(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  display_name TEXT NOT NULL,
  integrity INTEGER NOT NULL DEFAULT 100 CHECK (integrity BETWEEN 0 AND 100),
  breached BOOLEAN NOT NULL DEFAULT false,
  breach_reason TEXT,
  breach_at TIMESTAMPTZ,
  last_heartbeat TIMESTAMPTZ NOT NULL DEFAULT now(),
  joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  left_at TIMESTAMPTZ,
  UNIQUE (room_id, user_id)
);

CREATE INDEX idx_participants_room ON public.participants(room_id);
CREATE INDEX idx_participants_user ON public.participants(user_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON public.participants TO authenticated;
GRANT ALL ON public.participants TO service_role;

ALTER TABLE public.participants ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Authenticated users can read participants"
  ON public.participants FOR SELECT
  TO authenticated
  USING (true);

CREATE POLICY "Users can join as themselves"
  ON public.participants FOR INSERT
  TO authenticated
  WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own participant or host can update any"
  ON public.participants FOR UPDATE
  TO authenticated
  USING (
    auth.uid() = user_id
    OR EXISTS (SELECT 1 FROM public.rooms r WHERE r.id = room_id AND r.host_id = auth.uid())
  )
  WITH CHECK (
    auth.uid() = user_id
    OR EXISTS (SELECT 1 FROM public.rooms r WHERE r.id = room_id AND r.host_id = auth.uid())
  );

CREATE POLICY "Users can remove own participant or host can remove any"
  ON public.participants FOR DELETE
  TO authenticated
  USING (
    auth.uid() = user_id
    OR EXISTS (SELECT 1 FROM public.rooms r WHERE r.id = room_id AND r.host_id = auth.uid())
  );

-- =========================================================
-- BREAKS (event log)
-- =========================================================
CREATE TABLE public.breaks (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  room_id UUID NOT NULL REFERENCES public.rooms(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  display_name TEXT NOT NULL,
  reason TEXT NOT NULL,
  at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_breaks_room ON public.breaks(room_id);
CREATE INDEX idx_breaks_user ON public.breaks(user_id);

GRANT SELECT, INSERT ON public.breaks TO authenticated;
GRANT ALL ON public.breaks TO service_role;

ALTER TABLE public.breaks ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Authenticated users can read breaks"
  ON public.breaks FOR SELECT
  TO authenticated
  USING (true);

CREATE POLICY "Users can log own break"
  ON public.breaks FOR INSERT
  TO authenticated
  WITH CHECK (auth.uid() = user_id);

-- =========================================================
-- updated_at trigger
-- =========================================================
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_profiles_updated_at
  BEFORE UPDATE ON public.profiles
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER trg_rooms_updated_at
  BEFORE UPDATE ON public.rooms
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- =========================================================
-- Auto-create profile on signup
-- =========================================================
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  INSERT INTO public.profiles (id, display_name, avatar_url)
  VALUES (
    NEW.id,
    COALESCE(
      NEW.raw_user_meta_data->>'display_name',
      NEW.raw_user_meta_data->>'full_name',
      NEW.raw_user_meta_data->>'name',
      split_part(NEW.email, '@', 1),
      'Anonymous'
    ),
    NEW.raw_user_meta_data->>'avatar_url'
  )
  ON CONFLICT (id) DO NOTHING;
  RETURN NEW;
END;
$$;

CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- =========================================================
-- Realtime
-- =========================================================
ALTER PUBLICATION supabase_realtime ADD TABLE public.rooms;
ALTER PUBLICATION supabase_realtime ADD TABLE public.participants;
ALTER PUBLICATION supabase_realtime ADD TABLE public.breaks;
