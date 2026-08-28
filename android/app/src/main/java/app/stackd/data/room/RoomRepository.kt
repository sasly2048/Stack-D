package app.stackd.data.room

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Mirrors the web's `room.functions.ts` / `rooms2.functions.ts` and the direct
 * Supabase calls in `room.$code.tsx`, one method per web call.
 *
 * There is no server tier here: the web's `supabaseAdmin` paths don't exist on
 * Android, so anything that needed elevated rights is reached through the same
 * SECURITY DEFINER RPCs the web already relies on. Where the web wrapped a call
 * purely to rate-limit it pre-auth (`validateRoomCode`), Android calls
 * [claimRoomSeat] directly — that RPC performs the real authorization check,
 * and the throttle was protecting an open web form the APK doesn't have.
 */
class RoomRepository(private val client: SupabaseClient) {

    /**
     * Resolves a room by code and seats the caller as a participant, atomically.
     *
     * `rooms` SELECT is scoped to host/participants, so a non-member cannot read
     * the room at all — splitting this into a lookup followed by an insert would
     * fail the lookup. The definer RPC does both inside one transaction, which
     * is what makes the follow-up participant/break reads below succeed.
     */
    suspend fun claimRoomSeat(code: String): RoomRow? =
        client.postgrest.rpc(
            function = "claim_room_seat",
            parameters = buildJsonObject { put("_code", code.uppercase()) },
        ).decodeAsOrNull<RoomRow>()

    suspend fun getRoom(roomId: String): RoomRow? =
        client.postgrest.from("rooms")
            .select { filter { eq("id", roomId) } }
            .decodeSingleOrNull()

    suspend fun listParticipants(roomId: String): List<ParticipantRow> =
        client.postgrest.from("participants")
            .select {
                filter { eq("room_id", roomId) }
                order("joined_at", Order.ASCENDING)
            }
            .decodeList()

    suspend fun listBreaks(roomId: String, limit: Long = BREAK_FEED_LIMIT): List<BreakRow> =
        client.postgrest.from("breaks")
            .select(io.github.jan.supabase.postgrest.query.Columns.list(
                "id", "user_id", "display_name", "reason", "severity", "at",
            )) {
                filter { eq("room_id", roomId) }
                order("at", Order.DESCENDING)
                limit(limit)
            }
            .decodeList()

    /**
     * Starts the session. The server sets `started_at` from its own clock —
     * every score derives from that timestamp, so it must never come from an
     * unverified device clock. The RPC is host-checked and idempotent, so a
     * double-tap cannot restart a running session.
     */
    suspend fun startSession(roomId: String) {
        client.postgrest.rpc(
            function = "start_focus_session",
            parameters = buildJsonObject { put("_room_id", roomId) },
        )
    }

    /**
     * Records a breach atomically — the RPC writes the `breaks` row and flips
     * the participant's `breached` flag together, so the feed and the roster can
     * never disagree.
     */
    suspend fun recordBreach(
        roomId: String,
        participantId: String,
        reason: String,
        severity: String,
        integrity: Int,
    ) {
        client.postgrest.rpc(
            function = "record_breach",
            parameters = buildJsonObject {
                put("_room_id", roomId)
                put("_participant_id", participantId)
                put("_reason", reason)
                put("_severity", severity)
                put("_integrity", integrity)
            },
        )
    }

    /**
     * Writes this participant's result into `focus_history`, returning the new
     * row id. One row per (profile, room) server-side, so a retry after a
     * dropped connection is safe rather than duplicating a session.
     */
    suspend fun finalizeSession(
        roomId: String,
        score: Int,
        xp: Int,
        durationSeconds: Int,
        breachesCount: Int,
        tier: String,
        scoringVersion: Int,
        abandonmentSeconds: Int = 0,
    ): String? =
        client.postgrest.rpc(
            function = "finalize_focus_session",
            parameters = buildJsonObject {
                put("_room_id", roomId)
                put("_score", score)
                put("_xp", xp)
                put("_duration_seconds", durationSeconds)
                put("_breaches_count", breachesCount)
                put("_tier", tier)
                put("_scoring_version", scoringVersion)
                // Since 20260825060000 the server derives score/duration/XP from
                // its own timestamps and ignores the values above. Abandonment is
                // the one term it can't reconstruct; it is clamped server-side so
                // it can only lower the score, never inflate it.
                put("_abandonment_seconds", abandonmentSeconds)
            },
        ).decodeAsOrNull<String>()

    /**
     * Host-only: closes the session via the `finish_focus_room` RPC. Direct
     * writes to `rooms.status`/`ended_at` are revoked at the column level
     * (20260824050000), so the RPC is the only sanctioned lifecycle exit —
     * host-checked, idempotent, `ended_at` stamped from the server clock.
     */
    suspend fun completeSession(roomId: String) {
        client.postgrest.rpc(
            function = "finish_focus_room",
            parameters = buildJsonObject {
                put("_room_id", roomId)
                put("_outcome", "complete")
            },
        )
    }

    suspend fun abortSession(roomId: String) {
        client.postgrest.rpc(
            function = "finish_focus_room",
            parameters = buildJsonObject {
                put("_room_id", roomId)
                put("_outcome", "aborted")
            },
        )
    }

    suspend fun leaveRoom(roomId: String, userId: String) {
        client.postgrest.from("participants").delete {
            filter {
                eq("room_id", roomId)
                eq("user_id", userId)
            }
        }
    }

    /* ---------------------------------------------------------------------- */
    /*  Dashboard / start                                                      */
    /* ---------------------------------------------------------------------- */

    /**
     * The caller's rooms, newest first. RLS scopes the rows to host-or-
     * participant, so no explicit ownership filter is needed — and adding one
     * would only narrow what the policy already decided.
     *
     * Over-fetches by one row to answer "is there another page" without a count
     * query, matching the web's `listMyRooms`.
     */
    suspend fun listMyRooms(
        page: Int = 0,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        status: String? = null,
    ): Pair<List<RoomListItem>, Boolean> {
        val size = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        val from = (page.coerceAtLeast(0)).toLong() * size
        val rows: List<RoomListItem> = client.postgrest.from("rooms")
            .select(io.github.jan.supabase.postgrest.query.Columns.list(
                "id", "code", "status", "target_duration_seconds",
                "started_at", "ended_at", "created_at", "host_id",
            )) {
                filter { if (status != null && status != "any") eq("status", status) }
                order("created_at", Order.DESCENDING)
                range(from, from + size) // +1 row to detect a further page
            }
            .decodeList()
        return rows.take(size) to (rows.size > size)
    }

    /**
     * Creates a room from the manual form (the "Custom" path), mirroring the
     * web Start screen's inline insert. Same code-collision retry as the
     * template path — a room code is a small space and two hosts opening rooms
     * in the same second can collide.
     */
    suspend fun createRoom(
        targetDurationSeconds: Long,
        title: String? = null,
        collectiveGoalSeconds: Long? = null,
        hostId: String,
        hostDisplayName: String,
    ): Result<String> {
        repeat(CODE_RETRIES) {
            val attempt = runCatching {
                client.postgrest.from("rooms").insert(
                    buildJsonObject {
                        put("code", generateCode())
                        put("host_id", hostId)
                        put("target_duration_seconds", targetDurationSeconds)
                        put("status", "lobby")
                        title?.takeIf { it.isNotBlank() }?.let { put("title", it) }
                        collectiveGoalSeconds?.let { put("collective_goal_seconds", it) }
                    },
                ) { select() }.decodeSingle<RoomRow>()
            }
            attempt.onSuccess { room ->
                client.postgrest.from("participants").insert(
                    buildJsonObject {
                        put("room_id", room.id)
                        put("user_id", hostId)
                        put("display_name", hostDisplayName)
                    },
                )
                return Result.success(room.code)
            }
            val err = attempt.exceptionOrNull()
            if (err != null && err.message?.contains("23505") != true &&
                err.message?.contains("duplicate", ignoreCase = true) != true
            ) {
                return Result.failure(err)
            }
        }
        return Result.failure(IllegalStateException("room_code_collision"))
    }

    suspend fun listTemplates(): List<RoomTemplate> =
        client.postgrest.from("room_templates")
            .select { order("sort_order", Order.ASCENDING) }
            .decodeList()

    /**
     * Creates a room from a template, retrying on a code collision.
     *
     * The code alphabet omits I/O/0/1 — a room code is read aloud and typed by
     * hand, and those four are the pairs people get wrong.
     */
    suspend fun createRoomFromTemplate(
        templateKey: String,
        title: String? = null,
        description: String? = null,
        collectiveGoalSeconds: Long? = null,
        hostId: String,
        hostDisplayName: String,
    ): Result<String> {
        val template: RoomTemplate = client.postgrest.from("room_templates")
            .select { filter { eq("key", templateKey) } }
            .decodeSingleOrNull() ?: return Result.failure(IllegalStateException("template_not_found"))

        repeat(CODE_RETRIES) {
            val attempt = runCatching {
                client.postgrest.from("rooms").insert(
                    buildJsonObject {
                        put("code", generateCode())
                        put("host_id", hostId)
                        put("target_duration_seconds", template.targetDurationSeconds)
                        put("status", "lobby")
                        put("title", title ?: template.title)
                        put("description", description ?: template.description)
                        collectiveGoalSeconds?.let { put("collective_goal_seconds", it) }
                        put("visibility", template.visibility)
                        put("template_key", template.key)
                    },
                ) { select() }.decodeSingle<RoomRow>()
            }
            attempt.onSuccess { room ->
                client.postgrest.from("participants").insert(
                    buildJsonObject {
                        put("room_id", room.id)
                        put("user_id", hostId)
                        put("display_name", hostDisplayName)
                    },
                )
                return Result.success(room.code)
            }
            // Only a unique-violation on the code is worth another attempt;
            // anything else would just repeat the same failure five times.
            val err = attempt.exceptionOrNull()
            if (err != null && err.message?.contains("23505") != true &&
                err.message?.contains("duplicate", ignoreCase = true) != true
            ) {
                return Result.failure(err)
            }
        }
        return Result.failure(IllegalStateException("room_code_collision"))
    }

    /* ---------------------------------------------------------------------- */
    /*  Phase 2 room panels — events, milestones, join requests, workspace     */
    /* ---------------------------------------------------------------------- */

    /** The room's activity feed, newest first, capped like the web's rail. */
    suspend fun listRoomEvents(roomId: String, limit: Long = EVENT_FEED_LIMIT): List<RoomEvent> =
        client.postgrest.from("room_events")
            .select {
                filter { eq("room_id", roomId) }
                order("created_at", Order.DESCENDING)
                limit(limit)
            }
            .decodeList()

    /**
     * Records a room event via the definer RPC — it stamps the actor from the
     * caller's identity, so a client can't forge who did what. Used for the
     * ready/unready toggle and similar presence beats.
     */
    suspend fun recordRoomEvent(roomId: String, kind: String, payload: JsonObject? = null) {
        client.postgrest.rpc(
            function = "record_room_event",
            parameters = buildJsonObject {
                put("_room_id", roomId)
                put("_kind", kind)
                payload?.let { put("_payload", it) }
            },
        )
    }

    /** The room's milestone timeline, newest first. */
    suspend fun listMilestones(roomId: String, limit: Long = EVENT_FEED_LIMIT): List<Milestone> =
        client.postgrest.from("room_milestones")
            .select {
                filter { eq("room_id", roomId) }
                order("reached_at", Order.DESCENDING)
                limit(limit)
            }
            .decodeList()

    /** Pending join requests — only a moderator's RLS lets these rows through. */
    suspend fun listJoinRequests(roomId: String): List<JoinRequest> =
        client.postgrest.from("room_join_requests")
            .select {
                filter {
                    eq("room_id", roomId)
                    eq("status", "pending")
                }
                order("created_at", Order.ASCENDING)
            }
            .decodeList()

    /** Approve or deny a join request. Moderator-gated server-side. */
    suspend fun respondToJoinRequest(requestId: String, approve: Boolean) {
        client.postgrest.from("room_join_requests").update(
            {
                set("status", if (approve) "approved" else "denied")
                set("responded_at", nowIso())
            },
        ) {
            filter { eq("id", requestId) }
        }
    }

    /**
     * Host/moderator: edits the room's cosmetic columns — the only `rooms`
     * columns still client-writable after 20260824050000. RLS enforces who may
     * write; a non-host update just affects zero rows. Mirrors the web's
     * `updateRoomMeta`, including the `pinned` room event.
     */
    suspend fun updateRoomMeta(
        roomId: String,
        title: String?,
        description: String?,
        pinnedMessage: String?,
        collectiveGoalSeconds: Long?,
        visibility: String,
    ) {
        client.postgrest.from("rooms").update(
            {
                set("title", title?.takeIf { it.isNotBlank() })
                set("description", description?.takeIf { it.isNotBlank() })
                set("pinned_message", pinnedMessage?.takeIf { it.isNotBlank() })
                set("collective_goal_seconds", collectiveGoalSeconds)
                set("visibility", visibility)
            },
        ) {
            filter { eq("id", roomId) }
        }
        if (!pinnedMessage.isNullOrBlank()) {
            recordRoomEvent(
                roomId, "pinned",
                buildJsonObject { put("message", pinnedMessage.take(200)) },
            )
        }
    }

    /**
     * Total focused seconds banked against this room, for the header's
     * collective-goal bar. Members/breached come from the roster already held
     * in state; this is the one aggregate that needs its own read.
     */
    suspend fun sumRoomFocusSeconds(roomId: String): Long =
        client.postgrest.from("focus_history")
            .select(io.github.jan.supabase.postgrest.query.Columns.list("duration_seconds")) {
                filter { eq("room_id", roomId) }
            }
            .decodeList<DurationRow>()
            .sumOf { it.durationSeconds }

    @kotlinx.serialization.Serializable
    private data class DurationRow(
        @kotlinx.serialization.SerialName("duration_seconds") val durationSeconds: Long,
    )

    /** Moderator user ids for the header's "Mods · n" line. */
    suspend fun listModeratorIds(roomId: String): List<String> =
        client.postgrest.from("room_moderators")
            .select(io.github.jan.supabase.postgrest.query.Columns.list("user_id")) {
                filter { eq("room_id", roomId) }
                limit(100)
            }
            .decodeList<ModeratorRow>()
            .map { it.userId }

    @kotlinx.serialization.Serializable
    private data class ModeratorRow(
        @kotlinx.serialization.SerialName("user_id") val userId: String,
    )

    /** Upcoming (and last-24h) scheduled events, soonest first. */
    suspend fun listSchedule(roomId: String): List<ScheduledEvent> =
        client.postgrest.from("room_scheduled_events")
            .select(io.github.jan.supabase.postgrest.query.Columns.list(
                "id", "title", "description", "starts_at", "duration_minutes", "created_by",
            )) {
                filter {
                    eq("room_id", roomId)
                    gte(
                        "starts_at",
                        java.time.Instant.now().minusSeconds(86_400).toString(),
                    )
                }
                order("starts_at", Order.ASCENDING)
                limit(100)
            }
            .decodeList()

    suspend fun createScheduledEvent(
        roomId: String,
        createdBy: String,
        title: String,
        startsAtIso: String,
        durationMinutes: Int,
        description: String? = null,
    ) {
        client.postgrest.from("room_scheduled_events").insert(
            buildJsonObject {
                put("room_id", roomId)
                put("created_by", createdBy)
                put("title", title.take(120))
                description?.takeIf { it.isNotBlank() }?.let { put("description", it.take(500)) }
                put("starts_at", startsAtIso)
                put("duration_minutes", durationMinutes.coerceIn(5, 480))
            },
        )
    }

    /**
     * This user's workspace items for the room, ordered as captured.
     *
     * The table is `session_workspace_items` — an earlier port wrote to a
     * `workspace_items` that has never existed, so every call here failed.
     * RLS (`workspace_own_all`) already scopes reads and writes to the caller,
     * so no `user_id` filter is needed on the way out; the insert still has to
     * name it because the column is NOT NULL with no default and the policy's
     * WITH CHECK requires `user_id = auth.uid()`.
     */
    suspend fun listWorkspace(roomId: String): List<WorkspaceItem> =
        client.postgrest.from("session_workspace_items")
            .select {
                filter { eq("room_id", roomId) }
                order("position", Order.ASCENDING)
            }
            .decodeList()

    suspend fun addWorkspaceItem(
        roomId: String,
        kind: String,
        content: String,
        url: String? = null,
    ): WorkspaceItem? {
        val userId = client.auth.currentUserOrNull()?.id ?: return null
        return client.postgrest.from("session_workspace_items").insert(
            buildJsonObject {
                put("room_id", roomId)
                put("user_id", userId)
                put("kind", kind)
                put("content", content)
                url?.takeIf { it.isNotBlank() }?.let { put("url", it) }
            },
        ) { select() }.decodeSingleOrNull()
    }

    suspend fun updateWorkspaceItem(id: String, done: Boolean) {
        client.postgrest.from("session_workspace_items").update(
            { set("done", done) },
        ) {
            filter { eq("id", id) }
        }
    }

    suspend fun deleteWorkspaceItem(id: String) {
        client.postgrest.from("session_workspace_items").delete {
            filter { eq("id", id) }
        }
    }

    /**
     * Stamps this participant's `last_heartbeat`, driving the roster's
     * disconnect detection. The web writes it every 15s while active; the
     * presence dot goes stale after 45s without one.
     */
    suspend fun heartbeat(roomId: String, userId: String) {
        client.postgrest.from("participants").update(
            { set("last_heartbeat", nowIso()) },
        ) {
            filter {
                eq("room_id", roomId)
                eq("user_id", userId)
            }
        }
    }

    private fun nowIso(): String =
        java.time.Instant.ofEpochMilli(System.currentTimeMillis()).toString()

    /* ---------------------------------------------------------------------- */
    /*  Realtime                                                               */
    /* ---------------------------------------------------------------------- */

    /**
     * The `room:{roomId}` channel, matching the web's channel name and filters
     * exactly: every event on `rooms` and `participants` for this room, plus
     * INSERTs on `breaks`.
     *
     * Returned as flows for the caller to collect in a ViewModel scope; the
     * caller owns subscribe/unsubscribe so a channel lives exactly as long as
     * the screen does, the same way the web scopes it to the component.
     */
    fun roomChannel(roomId: String): RoomChannel {
        val channel = client.realtime.channel("room:$roomId")
        return RoomChannel(
            channel = channel,
            rooms = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "rooms"
                filter("id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.EQ, roomId)
            },
            participants = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "participants"
                filter("room_id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.EQ, roomId)
            },
            breaks = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "breaks"
                filter("room_id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.EQ, roomId)
            },
            events = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "room_events"
                filter("room_id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.EQ, roomId)
            },
            milestones = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "room_milestones"
                filter("room_id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.EQ, roomId)
            },
            joinRequests = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "room_join_requests"
                filter("room_id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.EQ, roomId)
            },
        )
    }

    data class RoomChannel(
        val channel: io.github.jan.supabase.realtime.RealtimeChannel,
        val rooms: Flow<PostgresAction>,
        val participants: Flow<PostgresAction>,
        val breaks: Flow<PostgresAction.Insert>,
        val events: Flow<PostgresAction.Insert>,
        val milestones: Flow<PostgresAction.Insert>,
        val joinRequests: Flow<PostgresAction>,
    )

    private companion object {
        const val BREAK_FEED_LIMIT = 30L
        const val EVENT_FEED_LIMIT = 30L
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 50
        const val CODE_RETRIES = 5

        /** Same alphabet as the web: no I, O, 0 or 1. */
        const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun generateCode(): String =
            (1..6).map { CODE_ALPHABET.random() }.joinToString("")
    }
}

/** `decodeAs` that tolerates a null/empty RPC result instead of throwing. */
private inline fun <reified T : Any> io.github.jan.supabase.postgrest.result.PostgrestResult.decodeAsOrNull(): T? =
    runCatching { decodeAs<T>() }.getOrNull()
