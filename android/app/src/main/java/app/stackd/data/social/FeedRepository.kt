package app.stackd.data.social

import app.stackd.core.parseIsoMillis
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Activity feed + friend presence — web's `social.functions.ts`.
 *
 * The web reads these through a server function purely to hydrate profile
 * names in one place; there is no privileged write here. `activity_events`
 * carries an RLS policy of "own or friends' activity" and `profiles` is
 * world-readable, so the same two reads run straight from the client and
 * return exactly the rows the web sees.
 */

@Serializable
internal data class ActivityEventRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val kind: String,
    val payload: JsonObject? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
internal data class PresenceProfileRow(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("lifetime_xp") val lifetimeXp: Long = 0,
    @SerialName("current_focus_streak") val currentFocusStreak: Int = 0,
    @SerialName("last_active_at") val lastActiveAt: String? = null,
)

@Serializable
internal data class ActiveParticipantRow(
    @SerialName("user_id") val userId: String,
)

/** Web's `FeedItem`, with the actor's profile already folded in. */
data class FeedItem(
    val id: String,
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?,
    /** session_complete | achievement_unlock | challenge_complete | friend_add */
    val kind: String,
    val payload: JsonObject?,
    val createdAt: String,
) {
    /** The one-line description the web's `describe()` renders per kind. */
    val line: String
        get() {
            fun str(key: String) = (payload?.get(key) as? JsonPrimitive)?.content
            return when (kind) {
                "session_complete" -> {
                    val tier = str("tier").orEmpty()
                    val mins = Math.round((str("duration_seconds")?.toDoubleOrNull() ?: 0.0) / 60)
                    "completed a $mins-minute session · $tier"
                }
                "achievement_unlock" -> "unlocked ${str("id") ?: "an achievement"}"
                "challenge_complete" -> "finished the ${str("name") ?: "challenge"} rite"
                "friend_add" -> "formed a new tie"
                else -> kind.replace('_', ' ')
            }
        }
}

enum class PresenceStatus { FOCUSING, IDLE, OFFLINE }

data class FriendPresence(
    val id: String,
    val displayName: String?,
    val avatarUrl: String?,
    val status: PresenceStatus,
)

class FeedRepository(private val client: SupabaseClient) {

    suspend fun listFeed(userId: String, limit: Int = 30): List<FeedItem> {
        val events = client.postgrest.from("activity_events")
            .select(Columns.list("id", "user_id", "kind", "payload", "created_at")) {
                order("created_at", Order.DESCENDING)
                limit(limit.toLong())
            }
            .decodeList<ActivityEventRow>()
        if (events.isEmpty()) return emptyList()

        // One profiles read covers everyone in the page, self included — the web
        // splits it into "others" plus a separate self lookup only because its
        // server function already had the caller's row on hand.
        val ids = events.map { it.userId }.distinct()
        val profiles = client.postgrest.from("profiles")
            .select(Columns.list("id", "display_name", "avatar_url")) {
                filter { isIn("id", ids) }
            }
            .decodeList<PersonRef>()
            .associateBy { it.id }

        return events.map { e ->
            FeedItem(
                id = e.id,
                userId = e.userId,
                displayName = profiles[e.userId]?.displayName,
                avatarUrl = profiles[e.userId]?.avatarUrl,
                kind = e.kind,
                payload = e.payload,
                createdAt = e.createdAt,
            )
        }
    }

    /** Presence + status for accepted friends. */
    suspend fun friendsPresence(userId: String, nowMillis: Long): List<FriendPresence> {
        val friendIds = client.postgrest.from("friendships")
            .select(Columns.list("id", "requester_id", "addressee_id", "status", "created_at")) {
                filter {
                    eq("status", "accepted")
                    or {
                        eq("requester_id", userId)
                        eq("addressee_id", userId)
                    }
                }
            }
            .decodeList<FriendshipRow>()
            .map { if (it.requesterId == userId) it.addresseeId else it.requesterId }
        if (friendIds.isEmpty()) return emptyList()

        val profiles = client.postgrest.from("profiles")
            .select(
                Columns.list(
                    "id", "display_name", "avatar_url", "lifetime_xp",
                    "current_focus_streak", "last_active_at",
                ),
            ) { filter { isIn("id", friendIds) } }
            .decodeList<PresenceProfileRow>()

        // `rooms!inner(status)` turns the embed into a join filter, so this
        // returns only participants whose room is actually running.
        val focusing = client.postgrest.from("participants")
            .select(Columns.raw("user_id, rooms!inner(status)")) {
                filter {
                    isIn("user_id", friendIds)
                    eq("rooms.status", "active")
                }
            }
            .decodeList<ActiveParticipantRow>()
            .map { it.userId }
            .toSet()

        return profiles.map { p ->
            val last = parseIsoMillis(p.lastActiveAt) ?: 0L
            FriendPresence(
                id = p.id,
                displayName = p.displayName,
                avatarUrl = p.avatarUrl,
                status = when {
                    p.id in focusing -> PresenceStatus.FOCUSING
                    last > 0 && nowMillis - last < 5 * 60_000 -> PresenceStatus.IDLE
                    else -> PresenceStatus.OFFLINE
                },
            )
        }
    }

    /** Fire-and-forget presence beat; the RPC stamps `profiles.last_active_at`. */
    suspend fun heartbeat() {
        client.postgrest.rpc("presence_heartbeat")
    }
}
