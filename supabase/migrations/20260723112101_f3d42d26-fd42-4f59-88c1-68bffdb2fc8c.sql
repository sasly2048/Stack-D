
-- Fix 1: Revoke anon EXECUTE on user-only SECURITY DEFINER functions
REVOKE EXECUTE ON FUNCTION public.claim_daily_reward() FROM anon, PUBLIC;
REVOKE EXECUTE ON FUNCTION public.prestige_up() FROM anon, PUBLIC;
REVOKE EXECUTE ON FUNCTION public.dispatch_group_sprint(uuid, uuid, text, timestamptz, timestamptz) FROM anon, PUBLIC;

-- Fix 2: Restrict group_members INSERT so only the group creator can add members.
-- Previously any authenticated user could self-insert into any group, bypassing visibility.
DROP POLICY IF EXISTS "Self join group" ON public.group_members;

CREATE POLICY "Creator adds members"
  ON public.group_members
  FOR INSERT
  TO authenticated
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM public.focus_groups g
      WHERE g.id = group_members.group_id
        AND g.created_by = auth.uid()
    )
  );
