
-- Session reactions
CREATE TABLE public.session_reactions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES public.focus_history(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  emoji TEXT NOT NULL CHECK (char_length(emoji) BETWEEN 1 AND 8),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (session_id, user_id, emoji)
);
CREATE INDEX idx_session_reactions_session ON public.session_reactions(session_id);

GRANT SELECT, INSERT, DELETE ON public.session_reactions TO authenticated;
GRANT ALL ON public.session_reactions TO service_role;
ALTER TABLE public.session_reactions ENABLE ROW LEVEL SECURITY;

CREATE POLICY "reactions_select_friends_or_owner" ON public.session_reactions FOR SELECT
TO authenticated USING (
  EXISTS (
    SELECT 1 FROM public.focus_history fh
    WHERE fh.id = session_reactions.session_id
      AND (fh.profile_id = auth.uid() OR public.are_friends(fh.profile_id, auth.uid()))
  )
);
CREATE POLICY "reactions_insert_self_on_friend_or_own" ON public.session_reactions FOR INSERT
TO authenticated WITH CHECK (
  user_id = auth.uid() AND EXISTS (
    SELECT 1 FROM public.focus_history fh
    WHERE fh.id = session_reactions.session_id
      AND (fh.profile_id = auth.uid() OR public.are_friends(fh.profile_id, auth.uid()))
  )
);
CREATE POLICY "reactions_delete_self" ON public.session_reactions FOR DELETE
TO authenticated USING (user_id = auth.uid());

-- Session workspace items
CREATE TABLE public.session_workspace_items (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID REFERENCES public.focus_history(id) ON DELETE CASCADE,
  room_id UUID REFERENCES public.rooms(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  kind TEXT NOT NULL CHECK (kind IN ('note','todo','link')),
  content TEXT NOT NULL CHECK (char_length(content) BETWEEN 1 AND 4000),
  url TEXT,
  done BOOLEAN NOT NULL DEFAULT FALSE,
  position INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_workspace_user ON public.session_workspace_items(user_id, created_at DESC);
CREATE INDEX idx_workspace_session ON public.session_workspace_items(session_id);
CREATE INDEX idx_workspace_room ON public.session_workspace_items(room_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON public.session_workspace_items TO authenticated;
GRANT ALL ON public.session_workspace_items TO service_role;
ALTER TABLE public.session_workspace_items ENABLE ROW LEVEL SECURITY;

CREATE POLICY "workspace_own_all" ON public.session_workspace_items FOR ALL
TO authenticated USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid());

CREATE TRIGGER trg_workspace_updated_at
BEFORE UPDATE ON public.session_workspace_items
FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- Realtime for reactions
ALTER PUBLICATION supabase_realtime ADD TABLE public.session_reactions;
