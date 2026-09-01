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
     * Another witness's public profile — web's `getProfile` for `profile/$id`.
     * Bundles the profile row, session count, unlocked achievements, and the
     * viewer's friendship edge (null when viewing self). All owner/world reads
     * under RLS; the friendship row is the canonical pair either direction.
     */
    suspend fun publicProfile(targetId: String, viewerId: String): PublicProfile? {
        val prof = getProfile(targetId) ?: return null

        val sessionCount = client.postgrest.from("focus_history")
            .select(io.github.jan.supabase.postgrest.query.Columns.list("id")) {
                count(io.github.jan.supabase.postgrest.query.Count.EXACT)
                head = true
                filter { eq("profile_id", targetId) }
            }
            .countOrNull() ?: 0

        val achievements = client.postgrest.from("user_achievements")
            .select(
                io.github.jan.supabase.postgrest.query.Columns.raw(
                    "achievement_id, unlocked_at, achievements(name, tier)",
                ),
            ) {
                filter { eq("user_id", targetId) }
                order("unlocked_at", Order.DESCENDING)
            }
            .decodeList<PublicUnlockRow>()
            .map {
                PublicAchievement(
                    id = it.achievementId,
                    name = it.achievements?.name ?: it.achievementId,
                    tier = it.achievements?.tier ?: "bronze",
                )
            }

        // Friendship edge only matters when viewing someone else.
        val friendship = if (targetId == viewerId) null else {
            client.postgrest.from("friendships")
                .select(
                    io.github.jan.supabase.postgrest.query.Columns.list(
                        "id", "requester_id", "addressee_id", "status",
                    ),
                ) {
                    filter {
                        or {
                            and {
                                eq("requester_id", viewerId)
                                eq("addressee_id", targetId)
                            }
                            and {
                                eq("requester_id", targetId)
                                eq("addressee_id", viewerId)
                            }
                        }
                    }
                    limit(1)
                }
                .decodeList<PublicFriendshipRow>()
                .firstOrNull()
                ?.let { row ->
                    PublicFriendship(
                        id = row.id,
                        direction = when {
                            row.status == "accepted" -> "friend"
                            row.requesterId == viewerId -> "outgoing"
                            else -> "incoming"
                        },
                    )
                }
        }

        return PublicProfile(prof, sessionCount, achievements, friendship)
    }

    /**
     * The caller's whole focus history as a CSV string — web's
     * `exportFocusHistoryCsv`. Same columns, same header, same minute rounding
     * and `;`-joined tags, so a file exported from either client is identical.
     */
    suspend fun exportFocusHistoryCsv(userId: String): CsvExport {
        val rows = client.postgrest.from("focus_history")
            .select(
                io.github.jan.supabase.postgrest.query.Columns.list(
                    "created_at", "tier", "score", "xp_earned",
                    "duration_seconds", "breaches_count", "notes", "tags", "room_id",
                ),
            ) {
                filter { eq("profile_id", userId) }
                order("created_at", Order.DESCENDING)
                limit(5000)
            }
            .decodeList<ExportRow>()

        val header = "date,tier,score,xp_earned,duration_minutes,breaches,notes,tags,room_id"
        val body = rows.joinToString("\n") { r ->
            listOf(
                r.createdAt.orEmpty(),
                r.tier,
                r.score.toString(),
                r.xpEarned.toString(),
                Math.round(r.durationSeconds / 60.0).toString(),
                r.breachesCount.toString(),
                r.notes.orEmpty(),
                r.tags.orEmpty().joinToString(";"),
                r.roomId.orEmpty(),
            ).joinToString(",") { escapeCsv(it) }
        }
        return CsvExport(
            csv = if (body.isEmpty()) header else "$header\n$body",
            rowCount = rows.size,
        )
    }

    /**
     * Claims or changes the caller's username — web's `setMyUsername`, minus
     * the server-side moderation ruleset.
     *
     * The web screens against a DB-backed blocklist (`ruleset.server.ts`) and
     * logs decisions via service_role; neither is reachable from the APK, so
     * Android enforces only the *format* rules (length, leading letter, allowed
     * chars) and the 24h cooldown here, then leans on the DB unique index on
     * `username_canonical` for collisions. The blocklist gap is a known ceiling:
     * an unusual handle the web would reject can slip through, but the column is
     * cosmetic and the server can still reject on write if a trigger exists.
     */
    suspend fun setMyUsername(userId: String, raw: String): UsernameResult {
        val username = raw.trim()
        // ponytail: client format check only; server blocklist not portable.
        if (username.length < 3) return UsernameResult.Rejected("Usernames are at least 3 characters.")
        if (username.length > 20) return UsernameResult.Rejected("Usernames are at most 20 characters.")
        if (!Regex("^[A-Za-z][A-Za-z0-9_-]{2,19}$").matches(username)) {
            return UsernameResult.Rejected("Start with a letter; use letters, numbers, _ or - only.")
        }
        val canonical = username.lowercase()

        val me = client.postgrest.from("profiles")
            .select(
                io.github.jan.supabase.postgrest.query.Columns.list(
                    "username", "username_canonical", "username_changed_at",
                ),
            ) { filter { eq("id", userId) }; limit(1) }
            .decodeList<UsernameRow>()
            .firstOrNull()

        // A no-op re-save of the same handle must not burn the cooldown.
        if (me?.usernameCanonical == canonical && me.username == username) {
            return UsernameResult.Ok(username)
        }
        me?.usernameChangedAt?.let { changedAt ->
            val elapsed = System.currentTimeMillis() - (app.stackd.core.parseIsoMillis(changedAt) ?: 0)
            val window = 24L * 3600_000
            if (elapsed < window) {
                val mins = Math.ceil((window - elapsed) / 60_000.0).toInt()
                val human = if (mins >= 60) "${Math.ceil(mins / 60.0).toInt()}h" else "${mins}m"
                return UsernameResult.Rejected("You can change your username again in $human.")
            }
        }

        return runCatching {
            client.postgrest.from("profiles").update(
                {
                    set("username", username)
                    set("username_canonical", canonical)
                    set("username_changed_at", java.time.Instant.now().toString())
                },
            ) { filter { eq("id", userId) } }
            UsernameResult.Ok(username)
        }.getOrElse { err ->
            // 23505 = unique violation: taken between check and write.
            if (err.message?.contains("23505") == true ||
                err.message?.contains("duplicate", ignoreCase = true) == true
            ) UsernameResult.Rejected("That username isn't available.")
            else UsernameResult.Rejected("Couldn't set your username. Try again.")
        }
    }

    /** Edits the cosmetic profile columns the DB still lets clients write. */
    suspend fun updateProfile(userId: String, displayName: String?, bio: String?) {
        client.postgrest.from("profiles").update(
            {
                displayName?.takeIf { it.isNotBlank() }?.let { set("display_name", it.take(60)) }
                set("bio", bio?.takeIf { it.isNotBlank() }?.take(300))
            },
        ) {
            filter { eq("id", userId) }
        }
    }

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

/* ---------------------- Public (other-user) profile ---------------------- */

data class PublicAchievement(val id: String, val name: String, val tier: String)

/** The viewer's tie to the profile owner; null when viewing self. */
data class PublicFriendship(
    val id: String,
    /** friend | outgoing | incoming */
    val direction: String,
)

/** Web's `PublicProfile` — the owner's row plus derived social context. */
data class PublicProfile(
    val profile: ProfileRow,
    val sessionCount: Long,
    val achievements: List<PublicAchievement>,
    val friendship: PublicFriendship?,
)

@kotlinx.serialization.Serializable
internal data class PublicUnlockRow(
    @kotlinx.serialization.SerialName("achievement_id") val achievementId: String,
    val achievements: EmbeddedAchievement? = null,
) {
    @kotlinx.serialization.Serializable
    data class EmbeddedAchievement(val name: String? = null, val tier: String? = null)
}

@kotlinx.serialization.Serializable
internal data class PublicFriendshipRow(
    val id: String,
    @kotlinx.serialization.SerialName("requester_id") val requesterId: String,
    @kotlinx.serialization.SerialName("addressee_id") val addresseeId: String,
    val status: String,
)

/* ------------------------------- Username -------------------------------- */

sealed interface UsernameResult {
    data class Ok(val username: String) : UsernameResult
    data class Rejected(val message: String) : UsernameResult
}

@kotlinx.serialization.Serializable
internal data class UsernameRow(
    val username: String? = null,
    @kotlinx.serialization.SerialName("username_canonical") val usernameCanonical: String? = null,
    @kotlinx.serialization.SerialName("username_changed_at") val usernameChangedAt: String? = null,
)

/* ------------------------------ CSV export ------------------------------- */

data class CsvExport(val csv: String, val rowCount: Int)

@kotlinx.serialization.Serializable
internal data class ExportRow(
    @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
    val tier: String = "",
    val score: Int = 0,
    @kotlinx.serialization.SerialName("xp_earned") val xpEarned: Int = 0,
    @kotlinx.serialization.SerialName("duration_seconds") val durationSeconds: Int = 0,
    @kotlinx.serialization.SerialName("breaches_count") val breachesCount: Int = 0,
    val notes: String? = null,
    val tags: List<String>? = null,
    @kotlinx.serialization.SerialName("room_id") val roomId: String? = null,
)

/** RFC-4180 quoting, matching the web's `escapeCsv`. */
internal fun escapeCsv(v: String): String =
    if (v.any { it == '"' || it == ',' || it == '\n' || it == '\r' }) {
        "\"${v.replace("\"", "\"\"")}\""
    } else v
