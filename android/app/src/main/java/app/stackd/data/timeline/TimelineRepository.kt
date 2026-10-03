package app.stackd.data.timeline

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Session timeline + reactions — web's `session-interactions.functions.ts`.
 *
 * `session_reactions` grants only SELECT/INSERT/DELETE to `authenticated` and
 * its select policy is "friends or owner", so the toggle is a plain
 * delete-if-present / insert-otherwise from the client. There is no update
 * path to abuse: a reaction row is either there or it isn't.
 */

@Serializable
internal data class TimelineRow(
    val id: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("duration_seconds") val durationSeconds: Int,
    val score: Int,
    val tier: String,
    @SerialName("xp_earned") val xpEarned: Int,
    @SerialName("breaches_count") val breachesCount: Int = 0,
    val notes: String? = null,
    val tags: List<String>? = null,
    @SerialName("room_id") val roomId: String? = null,
)

@Serializable
internal data class ReactionRow(
    @SerialName("session_id") val sessionId: String,
    val emoji: String,
    @SerialName("user_id") val userId: String,
)

/** One emoji bucket on a session: how many, and whether the caller is in it. */
data class Reaction(val emoji: String, val count: Int, val mine: Boolean)

data class TimelineSession(
    val id: String,
    val createdAt: String,
    val durationSeconds: Int,
    val score: Int,
    val tier: String,
    val xpEarned: Int,
    val breachesCount: Int,
    val notes: String?,
    val tags: List<String>,
    val roomId: String?,
    val reactions: List<Reaction>,
)

/** The emoji set the web's reaction picker offers. */
val REACTION_PICKER = listOf("🔥", "💎", "🧘", "⚡", "🌒", "◆")

class TimelineRepository(private val client: SupabaseClient) {

    /**
     * One page of sessions, newest first. [before] is the `created_at` of the
     * last row already shown — cursor pagination rather than offset, so rows
     * finalized mid-scroll can't shift the window and duplicate an entry.
     */
    suspend fun listTimeline(
        userId: String,
        limit: Int = 20,
        before: String? = null,
        profileId: String? = null,
    ): List<TimelineSession> {
        val target = profileId ?: userId
        val sessions = client.postgrest.from("focus_history")
            .select(
                Columns.list(
                    "id", "created_at", "duration_seconds", "score", "tier",
                    "xp_earned", "breaches_count", "notes", "tags", "room_id",
                ),
            ) {
                filter {
                    eq("profile_id", target)
                    if (before != null) lt("created_at", before)
                }
                order("created_at", Order.DESCENDING)
                limit(limit.toLong())
            }
            .decodeList<TimelineRow>()
        if (sessions.isEmpty()) return emptyList()

        val reactions = client.postgrest.from("session_reactions")
            .select(Columns.list("session_id", "emoji", "user_id")) {
                filter { isIn("session_id", sessions.map { it.id }) }
            }
            .decodeList<ReactionRow>()
            .groupBy { it.sessionId }

        return sessions.map { s ->
            TimelineSession(
                id = s.id,
                createdAt = s.createdAt,
                durationSeconds = s.durationSeconds,
                score = s.score,
                tier = s.tier,
                xpEarned = s.xpEarned,
                breachesCount = s.breachesCount,
                notes = s.notes,
                tags = s.tags.orEmpty(),
                roomId = s.roomId,
                reactions = reactions[s.id].orEmpty()
                    .groupBy { it.emoji }
                    .map { (emoji, rows) ->
                        Reaction(emoji, rows.size, rows.any { it.userId == userId })
                    },
            )
        }
    }

    /** Returns true if the reaction is now on, false if it was removed. */
    suspend fun toggleReaction(userId: String, sessionId: String, emoji: String): Boolean {
        val existing = client.postgrest.from("session_reactions")
            .select(Columns.list("session_id", "emoji", "user_id")) {
                filter {
                    eq("session_id", sessionId)
                    eq("user_id", userId)
                    eq("emoji", emoji)
                }
                limit(1)
            }
            .decodeList<ReactionRow>()

        if (existing.isNotEmpty()) {
            client.postgrest.from("session_reactions").delete {
                filter {
                    eq("session_id", sessionId)
                    eq("user_id", userId)
                    eq("emoji", emoji)
                }
            }
            return false
        }
        client.postgrest.from("session_reactions").insert(
            buildJsonObject {
                put("session_id", sessionId)
                put("user_id", userId)
                put("emoji", emoji)
            },
        )
        return true
    }
}
