package app.stackd.data.social

import app.stackd.core.parseIsoMillis
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Focus circles — web's `groups.tsx` (direct Supabase) + `circles.functions.ts`.
 *
 * There is no `groups.functions.ts`; the web talks to `focus_groups` and
 * `group_members` straight from the browser under RLS, so Android does the
 * same. The one privileged step — fanning a sprint out to every member —
 * goes through the `dispatch_group_sprint` RPC, which owns the 3/user/60s and
 * 5/group/60s rate limits and raises `rate_limited`.
 */

@Serializable
internal data class FocusGroupRow(
    val id: String,
    val name: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("total_group_xp") val totalGroupXp: Long = 0,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
internal data class GroupMemberRow(
    @SerialName("group_id") val groupId: String,
    @SerialName("profile_id") val profileId: String,
    val profiles: MemberProfile? = null,
) {
    @Serializable
    data class MemberProfile(
        @SerialName("display_name") val displayName: String? = null,
        @SerialName("lifetime_xp") val lifetimeXp: Long = 0,
    )
}

/** One circle plus the caller's relationship to it — web's per-row derivation. */
data class GroupSummary(
    val id: String,
    val name: String,
    val createdBy: String,
    val totalGroupXp: Long,
    val memberCount: Int,
    val isMember: Boolean,
    val isOwner: Boolean,
    val members: List<GroupMemberEntry>,
)

data class GroupMemberEntry(val profileId: String, val displayName: String?, val lifetimeXp: Long)

/** A row of either leaderboard on the groups screen. */
data class BoardEntry(val name: String, val value: Long)

data class GroupsSnapshot(
    val meId: String,
    val groups: List<GroupSummary>,
    /** Circles ranked by average member lifetime XP. */
    val circleBoard: List<BoardEntry>,
    /** Top members across all circles by lifetime XP. */
    val personalBoard: List<BoardEntry>,
)

/* ------------------------------- Circles view ----------------------------- */

@Serializable
internal data class CircleProfileRow(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("current_focus_streak") val currentFocusStreak: Int = 0,
    @SerialName("last_active_at") val lastActiveAt: String? = null,
)

@Serializable
internal data class CircleHistoryRow(
    @SerialName("profile_id") val profileId: String,
    @SerialName("xp_earned") val xpEarned: Long = 0,
    @SerialName("duration_seconds") val durationSeconds: Long = 0,
)

data class CircleRef(val id: String, val name: String, val totalXp: Long)

data class CircleMember(
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?,
    val weeklyXp: Long,
    val weeklyMinutes: Int,
    val currentStreak: Int,
    val isOnline: Boolean,
)

data class CircleDetail(
    val id: String,
    val name: String,
    val totalXp: Long,
    val memberCount: Int,
    val members: List<CircleMember>,
)

class GroupsRepository(private val client: SupabaseClient) {

    /**
     * The whole groups screen in one read set: every circle, every membership,
     * both leaderboards. The web fetches groups and members separately and
     * derives the boards in the component; the derivation is pure, so it lives
     * here where it can be tested.
     */
    suspend fun snapshot(userId: String): GroupsSnapshot {
        val groups = client.postgrest.from("focus_groups")
            .select(Columns.list("id", "name", "created_by", "total_group_xp", "created_at")) {
                order("total_group_xp", Order.DESCENDING)
            }
            .decodeList<FocusGroupRow>()

        val members = if (groups.isEmpty()) emptyList() else
            client.postgrest.from("group_members")
                .select(Columns.raw("group_id, profile_id, profiles(display_name, lifetime_xp)")) {
                    filter { isIn("group_id", groups.map { it.id }) }
                }
                .decodeList<GroupMemberRow>()

        val byGroup = members.groupBy { it.groupId }
        val summaries = groups.map { g ->
            val ms = byGroup[g.id].orEmpty()
            GroupSummary(
                id = g.id,
                name = g.name,
                createdBy = g.createdBy,
                totalGroupXp = g.totalGroupXp,
                memberCount = ms.size,
                isMember = ms.any { it.profileId == userId },
                isOwner = g.createdBy == userId,
                members = ms.map {
                    GroupMemberEntry(it.profileId, it.profiles?.displayName, it.profiles?.lifetimeXp ?: 0)
                },
            )
        }

        val circleBoard = summaries
            .map { s ->
                val avg = if (s.members.isEmpty()) 0L
                else s.members.sumOf { it.lifetimeXp } / s.members.size
                BoardEntry(s.name, avg)
            }
            .sortedByDescending { it.value }
            .take(10)

        // De-dup by member across circles, then rank — a member in three
        // circles is one leaderboard row, not three.
        val personalBoard = members
            .filter { it.profiles != null }
            .associate { it.profileId to BoardEntry(it.profiles!!.displayName ?: "—", it.profiles.lifetimeXp) }
            .values
            .sortedByDescending { it.value }
            .take(20)

        return GroupsSnapshot(userId, summaries, circleBoard, personalBoard)
    }

    suspend fun createGroup(name: String, userId: String): String {
        val group = client.postgrest.from("focus_groups").insert(
            buildJsonObject {
                put("name", name.trim().take(80))
                put("created_by", userId)
            },
        ) { select() }.decodeSingle<FocusGroupRow>()
        // The creator is the first member; the web inserts this row right after.
        client.postgrest.from("group_members").insert(
            buildJsonObject {
                put("group_id", group.id)
                put("profile_id", userId)
            },
        )
        return group.id
    }

    suspend fun joinGroup(groupId: String, userId: String) {
        client.postgrest.from("group_members").insert(
            buildJsonObject {
                put("group_id", groupId)
                put("profile_id", userId)
            },
        )
    }

    suspend fun leaveGroup(groupId: String, userId: String) {
        client.postgrest.from("group_members").delete {
            filter {
                eq("group_id", groupId)
                eq("profile_id", userId)
            }
        }
    }

    /**
     * Announces [roomCode] to every member of [groupId]. The RPC is the trust
     * boundary — it checks membership and enforces the dispatch rate limits —
     * so a `rate_limited` here means the caller must wait, not that the sprint
     * failed to create.
     */
    suspend fun dispatchSprint(groupId: String, roomId: String, roomCode: String, expiresAtMillis: Long) {
        client.postgrest.rpc(
            "dispatch_group_sprint",
            buildJsonObject {
                put("_group_id", groupId)
                put("_active_session_id", roomId)
                put("_active_session_code", roomCode)
                put("_started_at", java.time.Instant.now().toString())
                put("_expires_at", java.time.Instant.ofEpochMilli(expiresAtMillis).toString())
            },
        )
    }

    /* ----------------------------- Circles view --------------------------- */

    suspend fun listMyCircles(userId: String): List<CircleRef> =
        client.postgrest.from("group_members")
            .select(Columns.raw("group_id, focus_groups!inner(id, name, total_group_xp)")) {
                filter { eq("profile_id", userId) }
            }
            .decodeList<MyCircleRow>()
            .map { CircleRef(it.focusGroups.id, it.focusGroups.name, it.focusGroups.totalGroupXp) }

    @Serializable
    internal data class MyCircleRow(
        @SerialName("focus_groups") val focusGroups: FocusGroupRow,
    )

    /**
     * A circle's weekly board: last 7 days of XP and minutes per member,
     * sorted by weekly XP. `weekStart` is 7 days back so "this week" tracks a
     * rolling window, matching the web exactly.
     */
    suspend fun circleDetail(id: String, nowMillis: Long): CircleDetail? {
        val group = client.postgrest.from("focus_groups")
            .select(Columns.list("id", "name", "created_by", "total_group_xp")) {
                filter { eq("id", id) }
                limit(1)
            }
            .decodeList<FocusGroupRow>()
            .firstOrNull() ?: return null

        val memberIds = client.postgrest.from("group_members")
            .select(Columns.list("group_id", "profile_id")) { filter { eq("group_id", id) } }
            .decodeList<GroupMemberRow>()
            .map { it.profileId }
        if (memberIds.isEmpty()) {
            return CircleDetail(group.id, group.name, group.totalGroupXp, 0, emptyList())
        }

        val since = java.time.Instant.ofEpochMilli(nowMillis - 7L * 86_400_000).toString()
        val profiles = client.postgrest.from("profiles")
            .select(
                Columns.list("id", "display_name", "avatar_url", "current_focus_streak", "last_active_at"),
            ) { filter { isIn("id", memberIds) } }
            .decodeList<CircleProfileRow>()
        val history = client.postgrest.from("focus_history")
            .select(Columns.list("profile_id", "xp_earned", "duration_seconds")) {
                filter {
                    isIn("profile_id", memberIds)
                    gte("created_at", since)
                }
            }
            .decodeList<CircleHistoryRow>()
            .groupBy { it.profileId }

        val members = profiles.map { p ->
            val h = history[p.id].orEmpty()
            val last = parseIsoMillis(p.lastActiveAt) ?: 0L
            CircleMember(
                userId = p.id,
                displayName = p.displayName,
                avatarUrl = p.avatarUrl,
                weeklyXp = h.sumOf { it.xpEarned },
                weeklyMinutes = Math.round(h.sumOf { it.durationSeconds } / 60.0).toInt(),
                currentStreak = p.currentFocusStreak,
                isOnline = last > nowMillis - 5 * 60_000,
            )
        }.sortedByDescending { it.weeklyXp }

        return CircleDetail(group.id, group.name, group.totalGroupXp, members.size, members)
    }
}
