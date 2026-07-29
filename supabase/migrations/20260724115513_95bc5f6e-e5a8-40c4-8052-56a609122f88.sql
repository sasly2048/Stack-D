
-- 1) focus_history: remove direct client INSERT policy; SECURITY DEFINER RPC finalize_focus_session bypasses RLS
DROP POLICY IF EXISTS "Users insert own focus history" ON public.focus_history;

-- 2) focus_groups: restrict UPDATE to non-sensitive columns via column-level grants.
-- SECURITY DEFINER RPCs (finalize_focus_session, dispatch_group_sprint) run as owner and bypass column grants.
REVOKE UPDATE ON public.focus_groups FROM authenticated;
GRANT UPDATE (name, updated_at) ON public.focus_groups TO authenticated;
