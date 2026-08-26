package app.stackd.data.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Row shapes for the room screen, mirroring the web's `room.$code.tsx`
 * interfaces field-for-field. Names are the wire names so the same JSON the
 * web receives decodes here without a translation layer.
 */

enum class RoomStatus(val wire: String) {
    LOBBY("lobby"),
    ACTIVE("active"),
    COMPLETE("complete"),
    ABORTED("aborted");

    companion object {
        fun from(value: String?): RoomStatus =
            entries.firstOrNull { it.wire == value } ?: LOBBY
    }
}

@Serializable
data class RoomRow(
    val id: String,
    val code: String,
    @SerialName("host_id") val hostId: String,
    val status: String,
    @SerialName("target_duration_seconds") val targetDurationSeconds: Long,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    /**
     * Server-set. Orders realtime updates so a replayed stale row is rejected —
     * without it a late-delivered older row can flip an active room back to
     * "lobby" and reset everyone's timer.
     */
    @SerialName("updated_at") val updatedAt: String? = null,
    val title: String? = null,
    val description: String? = null,
    @SerialName("collective_goal_seconds") val collectiveGoalSeconds: Long? = null,
    val visibility: String? = null,
    @SerialName("pinned_message") val pinnedMessage: String? = null,
    @SerialName("banner_url") val bannerUrl: String? = null,
) {
    val statusEnum: RoomStatus get() = RoomStatus.from(status)
}

/** Aggregates behind the room header's goal bar — web's `getRoomStats`. */
data class RoomStats(
    val members: Int,
    val breached: Int,
    val focusSecondsTotal: Long,
    val goalSeconds: Long?,
) {
    val progressPct: Int
        get() = if (goalSeconds != null && goalSeconds > 0) {
            ((focusSecondsTotal * 100) / goalSeconds).toInt().coerceAtMost(100)
        } else 0
}

/** One entry of the room's shared schedule (`room_scheduled_events`). */
@Serializable
data class ScheduledEvent(
    val id: String,
    val title: String,
    val description: String? = null,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("duration_minutes") val durationMinutes: Int,
    @SerialName("created_by") val createdBy: String? = null,
)

@Serializable
data class ParticipantRow(
    val id: String,
    @SerialName("room_id") val roomId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    val integrity: Int = 100,
    val breached: Boolean = false,
    @SerialName("breach_reason") val breachReason: String? = null,
    @SerialName("breach_at") val breachAt: String? = null,
    @SerialName("joined_at") val joinedAt: String,
    @SerialName("last_heartbeat") val lastHeartbeat: String? = null,
    @SerialName("left_at") val leftAt: String? = null,
)

@Serializable
data class BreakRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    val reason: String,
    /** "minor" | "severe" */
    val severity: String,
    val at: String,
) {
    val isSevere: Boolean get() = severity == "severe"
}

/** One row of the dashboard's room list — mirrors the web's `RoomListItem`. */
@Serializable
data class RoomListItem(
    val id: String,
    val code: String,
    val status: String,
    @SerialName("target_duration_seconds") val targetDurationSeconds: Long,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("host_id") val hostId: String,
) {
    val statusEnum: RoomStatus get() = RoomStatus.from(status)
    fun isHost(userId: String?) = userId != null && hostId == userId
}

@Serializable
data class RoomTemplate(
    val key: String,
    val title: String,
    val description: String,
    @SerialName("target_duration_seconds") val targetDurationSeconds: Long,
    @SerialName("banner_tone") val bannerTone: String? = null,
    val visibility: String = "open",
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class ProfileRow(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("lifetime_xp") val lifetimeXp: Long = 0,
    @SerialName("current_focus_streak") val currentFocusStreak: Int = 0,
    val bio: String? = null,
    val title: String? = null,
    val username: String? = null,
    @SerialName("best_streak") val bestStreak: Int = 0,
    @SerialName("total_focus_seconds") val totalFocusSeconds: Long = 0,
)

/**
 * A completed session as it lands in `focus_history` — the dashboard history and
 * results screen read this back. The XP column is `xp_earned` on the wire; the
 * finalize RPC takes it as `_xp`, but the stored row is `xp_earned`.
 */
@Serializable
data class FocusHistoryRow(
    val id: String,
    @SerialName("room_id") val roomId: String? = null,
    val score: Int,
    @SerialName("xp_earned") val xp: Int,
    @SerialName("duration_seconds") val durationSeconds: Int,
    @SerialName("breaches_count") val breachesCount: Int = 0,
    val tier: String,
    @SerialName("created_at") val createdAt: String? = null,
    /** Populated only when the query joins `rooms` — the dashboard row links by code. */
    val room: FocusHistoryRoomRef? = null,
)

/** The `room:rooms(...)` join the dashboard history embeds per session. */
@Serializable
data class FocusHistoryRoomRef(
    val id: String,
    val code: String,
    val status: String,
    @SerialName("started_at") val startedAt: String? = null,
)

/* -------------------------------------------------------------------------- */
/*  Phase 2 room panels — events, milestones, join requests, workspace         */
/* -------------------------------------------------------------------------- */

/**
 * One entry in a room's live activity feed (`room_events`). [payload] is opaque
 * JSON the UI reads per-kind (e.g. a pinned message). Kinds mirror the web's
 * label/glyph maps: joined, left, started, breach, completed, pinned, goal_hit,
 * ready, all_ready, disconnected, reconnected, join_requested, join_approved,
 * join_denied, moderator_added/removed, paused, resumed, unready.
 */
@Serializable
data class RoomEvent(
    val id: String,
    @SerialName("room_id") val roomId: String,
    @SerialName("actor_id") val actorId: String? = null,
    @SerialName("actor_name") val actorName: String? = null,
    val kind: String,
    val payload: kotlinx.serialization.json.JsonObject? = null,
    @SerialName("created_at") val createdAt: String,
)

/** A room story beat (`room_milestones`) — the in-room timeline reads these. */
@Serializable
data class Milestone(
    val id: String,
    val kind: String,
    val label: String,
    @SerialName("reached_at") val reachedAt: String,
)

/** A pending request to join a request-visibility room (`room_join_requests`). */
@Serializable
data class JoinRequest(
    val id: String,
    @SerialName("room_id") val roomId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    val message: String? = null,
    /** pending | approved | denied | cancelled */
    val status: String,
    @SerialName("created_at") val createdAt: String,
)

/** A personal note/todo/link captured during a session (`workspace_items`). */
@Serializable
data class WorkspaceItem(
    val id: String,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("room_id") val roomId: String? = null,
    @SerialName("user_id") val userId: String,
    /** note | todo | link */
    val kind: String,
    val content: String,
    val url: String? = null,
    val done: Boolean = false,
    val position: Int = 0,
    @SerialName("created_at") val createdAt: String,
)
