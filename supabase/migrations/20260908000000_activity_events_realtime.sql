-- Realtime for activity_events.
--
-- The global toast dispatcher (web's GlobalRealtimeToasts, Android's
-- app.stackd.core.ui.GlobalRealtimeToasts) subscribes to this table filtered by
-- user_id to surface achievement/challenge/friend/session events. The table has
-- existed and been RLS-scoped since 20260723030308, but it was never added to
-- the supabase_realtime publication — so the subscription connected and then
-- received nothing, on BOTH platforms. This publishes it so those INSERTs
-- actually stream to the subscribed client.
--
-- REPLICA IDENTITY FULL so the streamed change carries the whole new row
-- (user_id, kind, payload) the client needs to build the toast; the default
-- (primary key only) would deliver just the id.

ALTER TABLE public.activity_events REPLICA IDENTITY FULL;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables
    WHERE pubname = 'supabase_realtime'
      AND schemaname = 'public'
      AND tablename = 'activity_events'
  ) THEN
    EXECUTE 'ALTER PUBLICATION supabase_realtime ADD TABLE public.activity_events';
  END IF;
END $$;
