ALTER TABLE public.activity_events DROP CONSTRAINT IF EXISTS activity_events_kind_check;

ALTER TABLE public.activity_events
  ADD CONSTRAINT activity_events_kind_check
  CHECK (kind = ANY (ARRAY[
    'session_complete',
    'achievement_unlock',
    'challenge_complete',
    'friend_add',
    'daily_reward',
    'prestige',
    'session.started',
    'session.completed',
    'session.breached',
    'room.created',
    'room.joined',
    'atlas.recommendation_shown',
    'atlas.recommendation_dismissed',
    'low_power.toggled',
    'integration.viewed'
  ]::text[]));