package app.stackd.data.room

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
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
            },
        ).decodeAsOrNull<String>()

    /** Host-only: closes the session. Guarded on `status = active` so a late tap is a no-op. */
    suspend fun completeSession(roomId: String, endedAtIso: String) {
        client.postgrest.from("rooms").update(
            {
                set("status", "complete")
                set("ended_at", endedAtIso)
            },
        ) {
            filter {
                eq("id", roomId)
                eq("status", "active")
            }
        }
    }

    suspend fun abortSession(roomId: String, endedAtIso: String) {
        client.postgrest.from("rooms").update(
            {
                set("status", "aborted")
                set("ended_at", endedAtIso)
            },
        ) {
            filter { eq("id", roomId) }
        }
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
        )
    }

    data class RoomChannel(
        val channel: io.github.jan.supabase.realtime.RealtimeChannel,
        val rooms: Flow<PostgresAction>,
        val participants: Flow<PostgresAction>,
        val breaks: Flow<PostgresAction.Insert>,
    )

    private companion object {
        const val BREAK_FEED_LIMIT = 30L
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
