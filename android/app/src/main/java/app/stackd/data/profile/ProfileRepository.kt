package app.stackd.data.profile

import app.stackd.data.room.FocusHistoryRow
import app.stackd.data.room.ProfileRow
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Reads the caller's profile and recent sessions.
 *
 * Every method here is subject to RLS — there is no elevated path on Android —
 * so a query that returns zero rows means the policy said no, not that the data
 * is missing. Where a table is locked down to definer-only access (season
 * standings, for instance) the RPC must be used instead of the table.
 */
class ProfileRepository(private val client: SupabaseClient) {

    suspend fun getProfile(userId: String): ProfileRow? =
        client.postgrest.from("profiles")
            .select { filter { eq("id", userId) } }
            .decodeSingleOrNull()

    /**
     * Daily-reward state, derived from the caller's `login_streaks` row exactly
     * like the web's `getRewardStatus` (same cycle table, same chaining rule).
     */
    suspend fun rewardStatus(userId: String): RewardStatus {
        val row = client.postgrest.from("login_streaks")
            .select(
                io.github.jan.supabase.postgrest.query.Columns.list(
                    "streak", "last_claim_date", "total_claims",
                ),
            ) { filter { eq("user_id", userId) } }
            .decodeList<LoginStreakRow>()
            .firstOrNull()
        val today = java.time.LocalDate.now().toString()
        val yesterday = java.time.LocalDate.now().minusDays(1).toString()
        val streak = row?.streak ?: 0
        val claimedToday = row?.lastClaimDate == today
        val willChain = row?.lastClaimDate == yesterday || (row == null && !claimedToday)
        val nextStreak = if (claimedToday) streak else if (willChain) streak + 1 else 1
        val idx = (nextStreak - 1).mod(7)
        return RewardStatus(
            streak = streak,
            totalClaims = row?.totalClaims ?: 0,
            claimedToday = claimedToday,
            nextRewardXp = DAILY_REWARDS[idx],
            nextDayOfStreak = idx + 1,
        )
    }

    /** Claims today's reward; XP grant + streak math live in the RPC. */
    suspend fun claimDailyReward(): ClaimResult? =
        client.postgrest.rpc("claim_daily_reward")
            .decodeList<ClaimResult>()
            .firstOrNull()

    /**
     * Reports the device's IANA timezone so streak / daily-reward / challenge
     * day boundaries roll at the user's local midnight, not UTC — mirrors the
     * web's `setMyTimezone`. The RPC validates against `pg_timezone_names`.
     */
    suspend fun setMyTimezone(tz: String) {
        client.postgrest.rpc(
            function = "set_my_timezone",
            parameters = buildJsonObject { put("_tz", tz) },
        )
    }

    /**
     * Display name with the web's fallback chain: profile name, then the local
     * part of the email, then "Anon". Kept in one place so the roster, the
     * breach feed and the results screen can't disagree about what to call
     * someone.
     */
    suspend fun displayNameFor(userId: String, email: String?): String =
        getProfile(userId)?.displayName?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: "Anon"

    /**
     * The dashboard history: recent sessions with the embedded room reference so
     * each row can link back to its room by code. The `room:rooms(...)` join is
     * PostgREST's foreign-table embed, matching the web dashboard's select
     * string exactly.
     */
    suspend fun recentSessions(userId: String, limit: Long = 50): List<FocusHistoryRow> =
        client.postgrest.from("focus_history")
            .select(
                io.github.jan.supabase.postgrest.query.Columns.raw(
                    "id, room_id, score, xp_earned, duration_seconds, breaches_count, " +
                        "tier, created_at, room:rooms(id, code, status, started_at)",
                ),
            ) {
                filter { eq("profile_id", userId) }
                order("created_at", Order.DESCENDING)
                limit(limit)
            }
            .decodeList()

    /**
     * Rooms the caller is in that are running right now — the dashboard's
     * "LIVE_NOW" rail. RLS already scopes `rooms` to host-or-participant, so a
     * direct query on `rooms` is equivalent to the web's participant→rooms join
     * and one round-trip shorter. Only started rooms qualify: an active row with
     * no `started_at` is mid-transition and has no elapsed time to show.
     */
    suspend fun activeSessions(): List<app.stackd.data.room.RoomRow> =
        client.postgrest.from("rooms")
            .select {
                filter {
                    eq("status", "active")
                    filterNot("started_at", io.github.jan.supabase.postgrest.query.filter.FilterOperator.IS, null)
                }
                order("started_at", Order.DESCENDING)
            }
            .decodeList()

    /**
     * Raw history rows since a cutoff — the analytics/DNA screens derive every
     * stat from these on-device, mirroring the web's getAnalytics /
     * getProductivityDna server math (same columns, same caps).
     */
    suspend fun historySince(userId: String, sinceIso: String, limit: Long = 500): List<FocusHistoryRow> =
        client.postgrest.from("focus_history")
            .select(
                io.github.jan.supabase.postgrest.query.Columns.list(
                    "id", "room_id", "score", "xp_earned", "duration_seconds",
                    "breaches_count", "tier", "created_at",
                ),
            ) {
                filter {
                    eq("profile_id", userId)
                    gte("created_at", sinceIso)
                }
                order("created_at", Order.ASCENDING)
                limit(limit)
            }
            .decodeList()

    suspend fun sessionForRoom(userId: String, roomId: String): FocusHistoryRow? =
        client.postgrest.from("focus_history")
            .select {
                filter {
                    eq("profile_id", userId)
                    eq("room_id", roomId)
                }
            }
            .decodeSingleOrNull()
}

/** The web's 7-day reward cycle, verbatim. */
val DAILY_REWARDS = intArrayOf(10, 20, 40, 60, 80, 100, 200)

data class RewardStatus(
    val streak: Int,
    val totalClaims: Int,
    val claimedToday: Boolean,
    val nextRewardXp: Int,
    val nextDayOfStreak: Int,
)

@kotlinx.serialization.Serializable
data class ClaimResult(
    @kotlinx.serialization.SerialName("reward_xp") val rewardXp: Int,
    @kotlinx.serialization.SerialName("new_streak") val newStreak: Int,
    @kotlinx.serialization.SerialName("day_of_streak") val dayOfStreak: Int,
)

@kotlinx.serialization.Serializable
internal data class LoginStreakRow(
    val streak: Int = 0,
    @kotlinx.serialization.SerialName("last_claim_date") val lastClaimDate: String? = null,
    @kotlinx.serialization.SerialName("total_claims") val totalClaims: Int = 0,
)
