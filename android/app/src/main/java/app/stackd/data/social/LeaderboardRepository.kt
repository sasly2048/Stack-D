package app.stackd.data.social

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One person on the board — web's `IndividualRow`. */
@Serializable
data class LeaderboardProfile(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("lifetime_xp") val lifetimeXp: Long = 0,
    @SerialName("current_focus_streak") val currentFocusStreak: Int = 0,
)

/** One group on the board — web's `GroupRow` (member count joined in). */
@Serializable
data class LeaderboardGroup(
    val id: String,
    val name: String,
    @SerialName("total_group_xp") val totalGroupXp: Long = 0,
) {
    var memberCount: Int = 0
}

@Serializable
internal data class GroupMemberRef(@SerialName("group_id") val groupId: String)

/**
 * The leaderboard's three reads, ported from `leaderboard.tsx` verbatim:
 * top-100 profiles by lifetime XP, top-100 groups by group XP, and member
 * counts scoped to only the groups actually on screen.
 */
class LeaderboardRepository(private val client: SupabaseClient) {

    suspend fun topIndividuals(): List<LeaderboardProfile> =
        client.postgrest.from("profiles")
            .select(
                Columns.list(
                    "id", "display_name", "avatar_url", "lifetime_xp", "current_focus_streak",
                ),
            ) {
                order("lifetime_xp", Order.DESCENDING)
                limit(100)
            }
            .decodeList()

    suspend fun topGroups(): List<LeaderboardGroup> {
        val groups = client.postgrest.from("focus_groups")
            .select(Columns.list("id", "name", "total_group_xp")) {
                order("total_group_xp", Order.DESCENDING)
                limit(100)
            }
            .decodeList<LeaderboardGroup>()
        if (groups.isEmpty()) return groups

        val members = client.postgrest.from("group_members")
            .select(Columns.list("group_id")) {
                filter { isIn("group_id", groups.map { it.id }) }
            }
            .decodeList<GroupMemberRef>()
        val counts = members.groupingBy { it.groupId }.eachCount()
        groups.forEach { it.memberCount = counts[it.id] ?: 0 }
        return groups
    }
}
