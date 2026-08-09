-- Finish rooms server-side so a closed browser cannot strand a session.
--
-- The room lifecycle depended entirely on a live client: whoever's tab was
-- open drove the transition to 'complete'. If the host closed the tab, lost
-- battery, or drove into a tunnel, the room stayed 'active' forever — it kept
-- appearing in dashboards as a live session, participants could not cleanly
-- rejoin, and nothing ever wrote the finalisation.
--
-- This adds a sweep that completes rooms whose target duration has elapsed,
-- independent of any browser being open. It does NOT score participants:
-- finalize_focus_session stays the single place a score is written, remains
-- idempotent per (profile, room), and still runs when a client reconnects.
-- The sweep only closes the room so the state is truthful.

CREATE OR REPLACE FUNCTION public.reap_stale_rooms()
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
  _closed INTEGER;
BEGIN
  -- A room is finished when its target duration has elapsed since started_at,
  -- plus a grace margin. The grace exists so a client that is merely slow to
  -- report — a backgrounded tab catching up, a reconnect in progress — gets to
  -- finish the job itself before the server steps in.
  WITH done AS (
    UPDATE public.rooms
       SET status = 'complete',
           ended_at = COALESCE(ended_at, now()),
           updated_at = now()
     WHERE status = 'active'
       AND started_at IS NOT NULL
       AND now() > started_at
                 + make_interval(secs => target_duration_seconds)
                 + INTERVAL '2 minutes'
    RETURNING id
  )
  SELECT COUNT(*)::INT INTO _closed FROM done;

  -- Rooms that were opened and never started are abandoned lobbies. Left
  -- alone they accumulate forever and clutter every room list.
  UPDATE public.rooms
     SET status = 'aborted',
         ended_at = COALESCE(ended_at, now()),
         updated_at = now()
   WHERE status = 'lobby'
     AND created_at < now() - INTERVAL '6 hours';

  RETURN _closed;
END;
$function$;

-- Called by cron only. No client has any reason to invoke this.
REVOKE ALL ON FUNCTION public.reap_stale_rooms() FROM PUBLIC, anon, authenticated;

COMMENT ON FUNCTION public.reap_stale_rooms() IS
  'Completes active rooms past their duration and aborts stale lobbies, so room state does not depend on a browser staying open. Does not score — finalize_focus_session remains the only writer of focus_history.';

CREATE EXTENSION IF NOT EXISTS pg_cron;

DO $$
BEGIN
  PERFORM cron.unschedule('stackd-reap-rooms');
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

-- Every 5 minutes: frequent enough that a stranded room resolves quickly,
-- infrequent enough to be invisible load.
SELECT cron.schedule('stackd-reap-rooms', '*/5 * * * *', $$ SELECT public.reap_stale_rooms(); $$);
