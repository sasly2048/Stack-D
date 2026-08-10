-- Stop anonymous reads of profiles and rooms.
--
-- Both tables carried a SELECT policy targeting the `public` role. In Postgres,
-- `public` means every role — including `anon`, the role every unauthenticated
-- visitor gets via the publishable key that ships in the browser bundle. So
-- these were not "public" in the sense of "any signed-in user"; they were
-- readable by the open internet.
--
-- profiles: VERIFIED EXPLOITABLE before this migration. An unauthenticated
-- GET /rest/v1/profiles returned 200 with real display names, XP and streaks.
-- A duplicate `authenticated`-scoped policy already existed granting exactly
-- the intended access, so dropping the public one loses nothing.
--
-- rooms: the policy was equally misconfigured but happened not to be
-- exploitable, because `anon` lacks EXECUTE on is_room_participant() and the
-- query errored out before returning rows. That is an accident, not a control
-- — granting anon execute on that helper for any unrelated reason would
-- silently open every open-visibility room. The policy is rescoped so the
-- protection is intentional rather than incidental.

-- ---------------------------------------------------------------------------
-- profiles
-- ---------------------------------------------------------------------------

-- "Profiles are viewable by everyone" — USING (true) TO public.
DROP POLICY IF EXISTS "Profiles are viewable by everyone" ON public.profiles;

-- The surviving policy ("Authenticated can view basic profile fields",
-- USING true TO authenticated) already covers every legitimate reader: the
-- app only renders profiles for signed-in users.

-- ---------------------------------------------------------------------------
-- rooms
-- ---------------------------------------------------------------------------

DROP POLICY IF EXISTS "Host, participant, or open visibility can read room" ON public.rooms;

CREATE POLICY "Host, participant, or open visibility can read room"
  ON public.rooms
  FOR SELECT
  -- TO authenticated, not the implicit `public`. Same predicate as before;
  -- only the audience changes.
  TO authenticated
  USING (
    auth.uid() = host_id
    OR public.is_room_participant(id, auth.uid())
    OR visibility = 'open'
  );
