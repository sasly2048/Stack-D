-- =========================================================
-- 1) Achievements use the user's local hour, not UTC.
-- =========================================================
-- evaluate_achievements still read EXTRACT(HOUR FROM created_at AT TIME ZONE
-- 'UTC'), so a 06:01 IST session (00:31 UTC) earned Night Owl ("22:00-04:00")
-- and evening IST sessions never did. Day/hour boundaries everywhere else
-- already resolve through user_timezone() (20260824030000); this was missed.
-- The unlock event also carries the achievement's display name so clients show
-- "Unbroken", not a humanised key like "No Breach".

CREATE OR REPLACE FUNCTION public.evaluate_achievements(_user_id UUID, _history_id UUID)
RETURNS SETOF TEXT LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE
  _h public.focus_history%ROWTYPE;
  _profile public.profiles%ROWTYPE;
  _total_hours NUMERIC;
  _hour INT;
  _in_group BOOLEAN;
  _new TEXT;
BEGIN
  SELECT * INTO _h FROM public.focus_history WHERE id = _history_id AND profile_id = _user_id;
  IF _h.id IS NULL THEN RETURN; END IF;
  SELECT * INTO _profile FROM public.profiles WHERE id = _user_id;
  _total_hours := COALESCE(_profile.total_focus_seconds,0)::NUMERIC / 3600.0;
  _hour := EXTRACT(HOUR FROM _h.created_at AT TIME ZONE public.user_timezone(_user_id))::INT;
  SELECT EXISTS(SELECT 1 FROM public.group_members WHERE profile_id = _user_id) INTO _in_group;

  FOR _new IN
    WITH candidates(aid, ok) AS (
      VALUES
        ('first_stack', TRUE),
        ('streak_7',  _profile.current_focus_streak >= 7),
        ('streak_30', _profile.current_focus_streak >= 30),
        ('hours_10',  _total_hours >= 10),
        ('hours_100', _total_hours >= 100),
        ('no_breach', _h.breaches_count = 0 AND _h.duration_seconds >= 300),
        ('flow_state', _h.score >= 95),
        ('team_player', _in_group),
        ('night_owl', _hour >= 22 OR _hour < 4),
        ('early_bird', _hour >= 4 AND _hour < 8)
    ), ins AS (
      INSERT INTO public.user_achievements (user_id, achievement_id)
      SELECT _user_id, aid FROM candidates WHERE ok
      ON CONFLICT DO NOTHING
      RETURNING achievement_id
    )
    SELECT achievement_id FROM ins
  LOOP
    UPDATE public.profiles p
       SET lifetime_xp = lifetime_xp + a.xp_reward, updated_at = now()
      FROM public.achievements a
     WHERE a.id = _new AND p.id = _user_id;
    INSERT INTO public.activity_events (user_id, kind, payload)
    VALUES (_user_id, 'achievement_unlock', jsonb_build_object(
      'id', _new,
      'name', (SELECT a.name FROM public.achievements a WHERE a.id = _new)
    ));
    RETURN NEXT _new;
  END LOOP;
END;
$$;

-- =========================================================
-- 2) Restore function privileges lost in the project move.
-- =========================================================
-- Restoring onto the new project (grsekkegkpwwgzrqvlqk) re-granted EXECUTE on
-- public functions to anon/authenticated: evaluate_achievements answered an
-- anonymous call with 200. This replays, in migration order, every REVOKE/GRANT
-- ON FUNCTION the repo declares, so the live grants match the repo's intent.
-- Each runs on its own and skips functions that don't exist here.
DO $g$ BEGIN EXECUTE 'REVOKE EXECUTE ON FUNCTION public.update_updated_at_column() FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260620092931
DO $g$ BEGIN EXECUTE 'REVOKE EXECUTE ON FUNCTION public.handle_new_user() FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260620092931
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.check_and_record_hit(TEXT, INT, INT) FROM PUBLIC'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260620102838
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.check_and_record_hit(TEXT, INT, INT) FROM anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260620102838
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.recent_auth_failures(TEXT, TEXT, INT) FROM PUBLIC'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260620102838
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.recent_auth_failures(TEXT, TEXT, INT) FROM anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260620102838
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.record_breach(UUID, UUID, TEXT, public.breach_severity, INTEGER) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260621033614
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.finalize_focus_session(UUID, INTEGER, INTEGER, INTEGER, INTEGER, TEXT) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260621033614
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.is_group_member(UUID, UUID) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260621033641
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.record_breach(UUID, UUID, TEXT, public.breach_severity, INTEGER) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260621033641
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.finalize_focus_session(UUID, INTEGER, INTEGER, INTEGER, INTEGER, TEXT) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260621033641
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.is_group_member(UUID, UUID) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260621033641
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.record_breach(UUID, UUID, TEXT, public.breach_severity, INTEGER) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260621033641
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.finalize_focus_session(UUID, INTEGER, INTEGER, INTEGER, INTEGER, TEXT) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260621033641
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.is_room_participant(uuid, uuid) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260621052404
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.claim_room_seat(text) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260621052404
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.room_code_exists(text) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260621052404
DO $g$ BEGIN EXECUTE 'REVOKE EXECUTE ON FUNCTION public.check_and_record_hit(text, integer, integer) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260621052404
DO $g$ BEGIN EXECUTE 'REVOKE EXECUTE ON FUNCTION public.recent_auth_failures(text, text, integer) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260621052404
DO $g$ BEGIN EXECUTE 'REVOKE EXECUTE ON FUNCTION public.handle_new_user() FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260621052404
DO $g$ BEGIN EXECUTE 'REVOKE EXECUTE ON FUNCTION public.update_updated_at_column() FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260621052404
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.recent_auth_failures(text, text, integer, text) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260621053337
DO $g$ BEGIN EXECUTE 'REVOKE EXECUTE ON FUNCTION public.record_auth_alert_if_new(TEXT,TEXT,INT,INT,JSONB) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260624074934
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.update_updated_at_column() FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260717064403
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.handle_new_user() FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260717064403
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.check_and_record_hit(text, integer, integer) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260717064403
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.recent_auth_failures(text, text, integer) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260717064403
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.recent_auth_failures(text, text, integer, text) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260717064403
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.record_auth_alert_if_new(text, text, integer, integer, jsonb) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260717064403
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.is_group_member(uuid, uuid) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260717064403
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.is_room_participant(uuid, uuid) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260717064403
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.record_breach(uuid, uuid, text, breach_severity, integer) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260717064403
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.record_breach(uuid, uuid, text, breach_severity, integer) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260717064403
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.room_code_exists(text) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260717064403
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.room_code_exists(text) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260717064403
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.claim_room_seat(text) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260717064403
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.claim_room_seat(text) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260717064403
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.finalize_focus_session(uuid, integer, integer, integer, integer, text) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260717064403
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.finalize_focus_session(uuid, integer, integer, integer, integer, text) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260717064403
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.is_room_participant(uuid, uuid) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260722085945
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.is_group_member(uuid, uuid) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260722085945
DO $g$ BEGIN EXECUTE 'REVOKE EXECUTE ON FUNCTION public.enqueue_email(TEXT, JSONB) FROM PUBLIC'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260722093104
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.enqueue_email(TEXT, JSONB) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260722093104
DO $g$ BEGIN EXECUTE 'REVOKE EXECUTE ON FUNCTION public.read_email_batch(TEXT, INT, INT) FROM PUBLIC'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260722093104
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.read_email_batch(TEXT, INT, INT) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260722093104
DO $g$ BEGIN EXECUTE 'REVOKE EXECUTE ON FUNCTION public.delete_email(TEXT, BIGINT) FROM PUBLIC'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260722093104
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.delete_email(TEXT, BIGINT) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260722093104
DO $g$ BEGIN EXECUTE 'REVOKE EXECUTE ON FUNCTION public.move_to_dlq(TEXT, TEXT, BIGINT, JSONB) FROM PUBLIC'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260722093104
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.move_to_dlq(TEXT, TEXT, BIGINT, JSONB) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260722093104
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.dispatch_group_sprint(uuid, uuid, text, timestamptz, timestamptz) FROM PUBLIC'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260722100505
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.dispatch_group_sprint(uuid, uuid, text, timestamptz, timestamptz) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260722100505
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.delete_email(text, bigint) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260722100505
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.enqueue_email(text, jsonb) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260722100505
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.move_to_dlq(text, text, bigint, jsonb) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260722100505
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.read_email_batch(text, integer, integer) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260722100505
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.email_queue_dispatch() FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260722100505
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.email_queue_wake() FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260722100505
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.delete_email(text, bigint) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260722100505
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.enqueue_email(text, jsonb) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260722100505
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.move_to_dlq(text, text, bigint, jsonb) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260722100505
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.read_email_batch(text, integer, integer) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260722100505
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.email_queue_dispatch() TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260722100505
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.email_queue_wake() TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260722100505
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.are_friends(UUID,UUID) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260723025731
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.are_friends(UUID,UUID) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260723025731
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.evaluate_achievements(UUID,UUID) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260723025731
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.is_group_member(uuid, uuid) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260723030044
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.is_room_participant(uuid, uuid) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260723030044
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.evaluate_challenges(UUID,UUID) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260723030308
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.update_session_meta(UUID, TEXT, TEXT[]) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260723030308
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.update_session_meta(UUID, TEXT, TEXT[]) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260723030308
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.presence_heartbeat() FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260723030308
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.presence_heartbeat() TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260723030308
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.evaluate_achievements(UUID,UUID) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260723030501
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.is_room_host(uuid, uuid) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260723031300
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.is_room_host(uuid, uuid) TO authenticated, service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260723031300
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.is_room_moderator(uuid, uuid) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260723032039
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.is_room_moderator(uuid, uuid) TO authenticated, service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260723032039
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.record_room_event(uuid, text, jsonb) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260723032039
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.record_room_event(uuid, text, jsonb) TO authenticated, service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260723032039
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.claim_daily_reward() FROM PUBLIC'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260723041852
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.claim_daily_reward() TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260723041852
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.prestige_up() FROM PUBLIC'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260723041852
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.prestige_up() TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260723041852
DO $g$ BEGIN EXECUTE 'REVOKE EXECUTE ON FUNCTION public.claim_daily_reward() FROM anon, PUBLIC'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260723112101
DO $g$ BEGIN EXECUTE 'REVOKE EXECUTE ON FUNCTION public.prestige_up() FROM anon, PUBLIC'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260723112101
DO $g$ BEGIN EXECUTE 'REVOKE EXECUTE ON FUNCTION public.dispatch_group_sprint(uuid, uuid, text, timestamptz, timestamptz) FROM anon, PUBLIC'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260723112101
DO $g$ BEGIN EXECUTE 'REVOKE EXECUTE ON FUNCTION public.friendship_accepted_activity() FROM anon, PUBLIC'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260723123314
DO $g$ BEGIN EXECUTE 'REVOKE EXECUTE ON FUNCTION public.rooms_add_collective_seconds() FROM anon, PUBLIC'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260723123314
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.ensure_current_season() FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260730022718
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.ensure_current_season() TO authenticated, service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260730022718
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.evaluate_milestones(uuid) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260730022718
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.evaluate_milestones(uuid) TO authenticated, service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260730022718
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.refresh_personality(uuid) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260730022718
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.refresh_personality(uuid) TO authenticated, service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260730022718
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.finalize_focus_session(uuid, integer, integer, integer, integer, text) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260730022718
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.finalize_focus_session(uuid, integer, integer, integer, integer, text) TO authenticated, service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260730022718
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.season_standings(uuid, integer) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260804013441
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.season_standings(uuid, integer) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260804013441
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.my_season_rank(uuid) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260804013441
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.my_season_rank(uuid) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260804013441
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.is_room_participant(uuid, uuid) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805145433
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.is_room_participant(uuid, uuid) TO authenticated, service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805145433
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.check_and_record_hit(text, integer, integer) FROM anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.recent_auth_failures(text, text, integer) FROM anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.recent_auth_failures(text, text, integer, text) FROM anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.record_auth_alert_if_new(text, text, integer, integer, jsonb) FROM anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.enqueue_email(text, jsonb) FROM anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.delete_email(text, bigint) FROM anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.read_email_batch(text, integer, integer) FROM anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.move_to_dlq(text, text, bigint, jsonb) FROM anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.email_queue_dispatch() FROM anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.email_queue_wake() FROM anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.evaluate_achievements(uuid, uuid) FROM anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.evaluate_challenges(uuid, uuid) FROM anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.evaluate_milestones(uuid) FROM anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.refresh_personality(uuid) FROM anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.ensure_current_season() FROM anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.handle_new_user() FROM anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.friendship_accepted_activity() FROM anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.rooms_add_collective_seconds() FROM anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.check_and_record_hit(text, integer, integer) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.recent_auth_failures(text, text, integer) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.recent_auth_failures(text, text, integer, text) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.record_auth_alert_if_new(text, text, integer, integer, jsonb) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.enqueue_email(text, jsonb) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.delete_email(text, bigint) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.read_email_batch(text, integer, integer) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.move_to_dlq(text, text, bigint, jsonb) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.email_queue_dispatch() TO service_role, postgres'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.evaluate_achievements(uuid, uuid) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.evaluate_challenges(uuid, uuid) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.evaluate_milestones(uuid) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.refresh_personality(uuid) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.ensure_current_season() TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260805155534
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.are_friends(uuid, uuid) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260806075521
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.is_group_member(uuid, uuid) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260806075521
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.is_room_host(uuid, uuid) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260806075521
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.is_room_moderator(uuid, uuid) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260806075521
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.is_room_participant(uuid, uuid) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260806075521
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.are_friends(uuid, uuid) TO authenticated, service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260806075521
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.is_group_member(uuid, uuid) TO authenticated, service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260806075521
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.is_room_host(uuid, uuid) TO authenticated, service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260806075521
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.is_room_moderator(uuid, uuid) TO authenticated, service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260806075521
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.is_room_participant(uuid, uuid) TO authenticated, service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260806075521
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.room_code_exists(text) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260806075521
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.room_code_exists(text) TO authenticated, service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260806075521
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.room_code_exists(text) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260806085153
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.room_code_exists(text) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260806085153
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.finalize_focus_session(uuid, integer, integer, integer, integer, text, smallint) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260809180000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.finalize_focus_session(uuid, integer, integer, integer, integer, text, smallint) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260809180000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.start_focus_session(uuid) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260809181000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.start_focus_session(uuid) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260809181000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.reap_stale_rooms() FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260810000000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.join_season(uuid) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260813002936
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.join_season(uuid) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260813002936
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.participants_protect_scoring() FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260813004353
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.mentorship_freeze_parties() FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260813004353
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.participants_protect_scoring() FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260813004353
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.my_entitlement() FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260817050000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.has_tier(public.access_tier) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260817050000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.lifetime_promo_status() FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260817050000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.redeem_lifetime(TEXT) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260817050000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.tier_rank(public.access_tier) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260817050000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.my_entitlement() TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260817050000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.has_tier(public.access_tier) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260817050000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.lifetime_promo_status() TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260817050000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.redeem_lifetime(TEXT) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260817050000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.tier_rank(public.access_tier) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260817050000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.grant_subscription(UUID, public.access_tier, TEXT, TIMESTAMPTZ) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260817060000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.record_webhook_event(TEXT, TEXT) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260817060000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.grant_subscription(UUID, public.access_tier, TEXT, TIMESTAMPTZ) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260817060000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.record_webhook_event(TEXT, TEXT) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260817060000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.begin_webhook_event(TEXT, TEXT) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260818120000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.complete_webhook_event(TEXT) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260818120000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.fail_webhook_event(TEXT) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260818120000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.begin_webhook_event(TEXT, TEXT) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260818120000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.complete_webhook_event(TEXT) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260818120000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.fail_webhook_event(TEXT) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260818120000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.grant_subscription(UUID, public.access_tier, TEXT, TIMESTAMPTZ, TEXT) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260819050000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.grant_subscription(UUID, public.access_tier, TEXT, TIMESTAMPTZ, TEXT) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260819050000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.friendship_guard() FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260819060000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.award_earned_titles() FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260821015144
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.award_earned_titles() TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260821015144
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.username_is_taken(text, uuid) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260822051831
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.username_is_taken(text, uuid) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260822051831
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.username_is_taken(text, uuid) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260822051831
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.get_my_private_profile() FROM PUBLIC'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260822051951
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.get_my_private_profile() FROM anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260822051951
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.get_my_private_profile() TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260822051951
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.get_my_private_profile() TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260822051951
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.ai_meter() FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260823120000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.ai_usage_status() FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260823120000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.ai_allowance(public.access_tier) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260823120000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.ai_meter() TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260823120000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.ai_usage_status() TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260823120000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.ai_allowance(public.access_tier) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260823120000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.grant_subscription(UUID, public.access_tier, TEXT, TIMESTAMPTZ, TEXT) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260823160000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.grant_subscription(UUID, public.access_tier, TEXT, TIMESTAMPTZ, TEXT) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260823160000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.profiles_protect_scoring() FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260823170000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.rooms_protect_lifecycle() FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260823200000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.room_join_request_guard() FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260823210000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.blocks_exist(uuid, uuid) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260824020000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.blocks_exist(uuid, uuid) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260824020000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.friendship_block_guard() FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260824020000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.user_timezone(uuid) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260824030000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.user_timezone(uuid) TO authenticated, service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260824030000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.set_my_timezone(text) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260824030000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.set_my_timezone(text) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260824030000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.claim_daily_reward() FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260824030000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.claim_daily_reward() TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260824030000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.evaluate_challenges(UUID,UUID) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260824030000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.evaluate_challenges(UUID,UUID) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260824030000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.refresh_personality(uuid) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260824030000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.refresh_personality(uuid) TO authenticated, service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260824030000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.reaction_block_guard() FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260824040000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.mentorship_block_guard() FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260824040000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.equip_title(text) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260824050000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.equip_title(text) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260824050000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.refresh_personality(uuid) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260824060000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.refresh_personality(uuid) TO authenticated, service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260824060000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.user_timezone(uuid) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260824060000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.user_timezone(uuid) TO authenticated, service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260824060000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.blocks_exist(uuid, uuid) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260824060000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.blocks_exist(uuid, uuid) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260824060000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.open_capsule(uuid) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260824060000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.open_capsule(uuid) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260824060000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.ai_refund() FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260825040000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.ai_refund() TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260825040000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.ai_refund(uuid) FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260825050000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.ai_refund(uuid) TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260825050000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.finalize_focus_session(uuid, integer, integer, integer, integer, text, smallint, integer) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260825060000
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.finalize_focus_session(uuid, integer, integer, integer, integer, text, smallint, integer) TO authenticated, service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260825060000
DO $g$ BEGIN EXECUTE 'REVOKE ALL ON FUNCTION public.finish_focus_room(uuid, public.room_status) FROM PUBLIC, anon'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260826100814
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.finish_focus_room(uuid, public.room_status) TO authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260826100814
DO $g$ BEGIN EXECUTE 'REVOKE EXECUTE ON FUNCTION public.sync_public_profile() FROM PUBLIC, anon, authenticated'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260924023820
DO $g$ BEGIN EXECUTE 'GRANT EXECUTE ON FUNCTION public.sync_public_profile() TO service_role'; EXCEPTION WHEN undefined_function OR undefined_object THEN NULL; END $g$; -- 20260924023820

-- Supabase default privileges re-grant EXECUTE to anon on CREATE, so the
-- function rewritten in (1) is locked down again explicitly.
REVOKE ALL ON FUNCTION public.evaluate_achievements(UUID,UUID) FROM PUBLIC, anon, authenticated;

