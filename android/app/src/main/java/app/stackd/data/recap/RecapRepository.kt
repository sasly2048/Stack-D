package app.stackd.data.recap

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneOffset

/**
 * Year-in-review + day replay — web's `wrapped.functions.ts` and
 * `replay.functions.ts`. Pure aggregations over the caller's own
 * `focus_history` / `activity_events` (both owner-scoped by RLS) plus a
 * world-readable `profiles` count for the percentile, so every read runs
 * straight from the client and returns the same numbers the web computes.
 */

private val WEEKDAYS = listOf(
    "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday",
)
private val MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/* --------------------------------- Wrapped -------------------------------- */

@Serializable
internal data class WrappedHistoryRow(
    @SerialName("duration_seconds") val durationSeconds: Long = 0,
    @SerialName("xp_earned") val xpEarned: Long = 0,
    val score: Int = 0,
    @SerialName("breaches_count") val breachesCount: Int = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("room_id") val roomId: String? = null,
)

@Serializable
internal data class WrappedProfileRow(
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("lifetime_xp") val lifetimeXp: Long = 0,
    @SerialName("best_streak") val bestStreak: Int = 0,
    @SerialName("productivity_dna") val productivityDna: String? = null,
)

@Serializable
internal data class MateRow(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String? = null,
)

data class TopCollaborator(val name: String, val sessions: Int)
data class MonthHours(val month: String, val hours: Double)

data class WrappedStats(
    val year: Int,
    val rolling: Boolean,
    val totalHours: Double,
    val totalSessions: Int,
    val totalXp: Long,
    val longestSessionMinutes: Int,
    val bestStreak: Int,
    val topWeekday: String,
    val peakHour: Int,
    val perfectSessions: Int,
    val flowSessions: Int,
    val personality: String?,
    val percentile: Int,
    val topCollaborator: TopCollaborator?,
    val monthly: List<MonthHours>,
    val displayName: String,
)

/* --------------------------------- Replay --------------------------------- */

@Serializable
internal data class ReplayHistoryRow(
    @SerialName("created_at") val createdAt: String,
    val score: Int = 0,
    val tier: String = "",
    @SerialName("duration_seconds") val durationSeconds: Long = 0,
)

@Serializable
internal data class ReplayActivityRow(
    val kind: String,
    val payload: kotlinx.serialization.json.JsonObject? = null,
    @SerialName("created_at") val createdAt: String,
)

data class ReplayEvent(
    val at: String,
    /** session | breach | achievement | milestone */
    val kind: String,
    val label: String,
    val durationSeconds: Long = 0,
)

class RecapRepository(private val client: SupabaseClient) {

    /** The year (or rolling 12 months, early in the year) wrapped into stats. */
    suspend fun getWrapped(userId: String): WrappedStats {
        val now = Instant.now()
        val nowZ = now.atZone(ZoneOffset.UTC)
        val year = nowZ.year
        val jan1 = Instant.parse("%04d-01-01T00:00:00Z".format(year))
        // Early in the year there isn't enough of it to wrap, so fall back to a
        // rolling 12-month window — same 90-day cutoff the web uses.
        val rolling = now.toEpochMilli() - jan1.toEpochMilli() < 90L * 86_400_000
        val since = if (rolling) now.minusSeconds(365 * 86_400L) else jan1

        val hist = client.postgrest.from("focus_history")
            .select(
                Columns.list(
                    "duration_seconds", "xp_earned", "score",
                    "breaches_count", "created_at", "room_id",
                ),
            ) {
                filter {
                    eq("profile_id", userId)
                    gte("created_at", since.toString())
                }
                limit(2000)
            }
            .decodeList<WrappedHistoryRow>()

        val totalSeconds = hist.sumOf { it.durationSeconds }
        val longest = hist.maxOfOrNull { it.durationSeconds } ?: 0

        val weekdaySeconds = LongArray(7)
        val hourCounts = IntArray(24)
        val monthlyHours = DoubleArray(12)
        hist.forEach { r ->
            val z = Instant.parse(r.createdAt).atZone(ZoneOffset.UTC)
            weekdaySeconds[z.dayOfWeek.value % 7] += r.durationSeconds
            hourCounts[z.hour] += 1
            monthlyHours[z.monthValue - 1] += r.durationSeconds / 3600.0
        }

        val prof = client.postgrest.from("profiles")
            .select(Columns.list("display_name", "lifetime_xp", "best_streak", "productivity_dna")) {
                filter { eq("id", userId) }
                limit(1)
            }
            .decodeList<WrappedProfileRow>()
            .firstOrNull()
        val lifetimeXp = prof?.lifetimeXp ?: 0

        val total = client.postgrest.from("profiles")
            .select(Columns.list("id")) {
                count(Count.EXACT)
                head = true
            }
            .countOrNull() ?: 0
        val below = client.postgrest.from("profiles")
            .select(Columns.list("id")) {
                count(Count.EXACT)
                head = true
                filter { lt("lifetime_xp", lifetimeXp) }
            }
            .countOrNull() ?: 0
        val percentile = if (total > 0) Math.round(below.toDouble() / total * 100).toInt() else 0

        val topCollaborator = topCollaborator(userId, hist.mapNotNull { it.roomId })

        return WrappedStats(
            year = year,
            rolling = rolling,
            totalHours = Math.round(totalSeconds / 3600.0 * 10) / 10.0,
            totalSessions = hist.size,
            totalXp = hist.sumOf { it.xpEarned },
            longestSessionMinutes = Math.round(longest / 60.0).toInt(),
            bestStreak = prof?.bestStreak ?: 0,
            topWeekday = WEEKDAYS[weekdaySeconds.indices.maxByOrNull { weekdaySeconds[it] } ?: 1],
            peakHour = hourCounts.indices.maxByOrNull { hourCounts[it] } ?: 0,
            perfectSessions = hist.count { it.breachesCount == 0 },
            flowSessions = hist.count { it.score >= 95 },
            personality = prof?.productivityDna,
            percentile = percentile,
            topCollaborator = topCollaborator,
            monthly = monthlyHours.mapIndexed { i, h -> MonthHours(MONTHS[i], Math.round(h * 10) / 10.0) },
            displayName = prof?.displayName?.takeIf { it.isNotBlank() } ?: "Anon",
        )
    }

    /** Most co-attended room-mate this period, or null when solo. */
    private suspend fun topCollaborator(userId: String, roomIds: List<String>): TopCollaborator? {
        if (roomIds.isEmpty()) return null
        val mates = client.postgrest.from("participants")
            .select(Columns.list("user_id", "display_name")) {
                filter {
                    isIn("room_id", roomIds.distinct().take(200))
                    neq("user_id", userId)
                }
                limit(1000)
            }
            .decodeList<MateRow>()
        val best = mates.groupBy { it.userId }.maxByOrNull { it.value.size } ?: return null
        return TopCollaborator(
            name = best.value.first().displayName?.takeIf { it.isNotBlank() } ?: "Anon",
            sessions = best.value.size,
        )
    }

    /** One UTC day's focus + achievement events, chronological. */
    suspend fun getDayReplay(userId: String, isoDate: String): List<ReplayEvent> {
        val start = Instant.parse("${isoDate}T00:00:00Z")
        val end = start.plusSeconds(86_400)

        val history = client.postgrest.from("focus_history")
            .select(
                Columns.list("created_at", "score", "tier", "duration_seconds"),
            ) {
                filter {
                    eq("profile_id", userId)
                    gte("created_at", start.toString())
                    lt("created_at", end.toString())
                }
            }
            .decodeList<ReplayHistoryRow>()

        val activity = client.postgrest.from("activity_events")
            .select(Columns.list("kind", "payload", "created_at")) {
                filter {
                    eq("user_id", userId)
                    gte("created_at", start.toString())
                    lt("created_at", end.toString())
                }
            }
            .decodeList<ReplayActivityRow>()

        val events = buildList {
            history.forEach { h ->
                val mins = Math.round(h.durationSeconds / 60.0)
                add(
                    ReplayEvent(
                        at = h.createdAt,
                        kind = "session",
                        label = "${h.tier} · ${mins}m · ${h.score}pt",
                        durationSeconds = h.durationSeconds,
                    ),
                )
            }
            activity.filter { it.kind == "achievement_unlock" }.forEach { a ->
                val id = (a.payload?.get("id") as? kotlinx.serialization.json.JsonPrimitive)?.content
                add(ReplayEvent(a.createdAt, "achievement", "Unlocked ${id ?: "mark"}"))
            }
        }
        return events.sortedBy { it.at }
    }

    /* ----------------------------- Session summary ---------------------------- */

    /**
     * The rich post-session summary powering the ceremony's beats — web's
     * `getSessionSummary`. Every read is a plain, RLS-scoped table query plus the
     * triangular level curve; no server function or LLM, so it runs from the
     * client and returns the same numbers.
     */
    suspend fun getSessionSummary(userId: String, historyId: String): SessionSummary {
        val h = client.postgrest.from("focus_history")
            .select(
                Columns.list("score", "tier", "duration_seconds", "breaches_count", "xp_earned"),
            ) {
                filter { eq("id", historyId); eq("profile_id", userId) }
                limit(1)
            }
            .decodeList<SummaryHistoryRow>()
            .firstOrNull()
        val xpEarned = h?.xpEarned ?: 0

        val prof = client.postgrest.from("profiles")
            .select(
                Columns.list("lifetime_xp", "prestige_level", "current_focus_streak", "productivity_dna"),
            ) {
                filter { eq("id", userId) }
                limit(1)
            }
            .decodeList<SummaryProfileRow>()
            .firstOrNull()
        val lifetimeXp = prof?.lifetimeXp ?: 0
        val (level, into, span) = levelFromXp(lifetimeXp)

        // Rank = profiles strictly ahead + 1, now and before this session's XP.
        suspend fun countAhead(xp: Long): Int {
            val ahead = client.postgrest.from("profiles")
                .select(Columns.list("id")) {
                    count(Count.EXACT)
                    head = true
                    filter { gt("lifetime_xp", xp) }
                }
                .countOrNull() ?: 0
            return ahead.toInt() + 1
        }
        val rankNow = countAhead(lifetimeXp)
        val rankBefore = countAhead(maxOf(0, lifetimeXp - xpEarned))

        // Awards unlocked in this session's window (last 5 minutes).
        val since = Instant.ofEpochMilli(nowMillis() - 5 * 60 * 1000).toString()
        val fresh = client.postgrest.from("user_achievements")
            .select(Columns.list("achievement_id", "unlocked_at")) {
                filter { eq("user_id", userId); gte("unlocked_at", since) }
            }
            .decodeList<FreshAwardRow>()
        val ids = fresh.map { it.achievementId }
        var achievements = emptyList<AwardCard>()
        var milestones = emptyList<AwardCard>()
        if (ids.isNotEmpty()) {
            val defs = client.postgrest.from("achievements")
                .select(Columns.list("id", "name", "description", "icon", "tier", "xp_reward")) {
                    filter { isIn("id", ids) }
                }
                .decodeList<AwardCard>()
            milestones = defs.filter { it.tier == "milestone" }
            achievements = defs.filter { it.tier != "milestone" }
        }

        // Friends who also finished a session today.
        val links = client.postgrest.from("friendships")
            .select(Columns.list("requester_id", "addressee_id", "status")) {
                filter { eq("status", "accepted") }
            }
            .decodeList<FriendshipRow>()
        val friendIds = links.map {
            if (it.requesterId == userId) it.addresseeId else it.requesterId
        }.filter { it != userId }.distinct()

        var friendsFinished = emptyList<FriendFinish>()
        if (friendIds.isNotEmpty()) {
            val dayStart = Instant.ofEpochMilli(nowMillis())
                .atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant()
            val acts = client.postgrest.from("activity_events")
                .select(Columns.list("user_id", "payload", "created_at")) {
                    filter {
                        eq("kind", "session_complete")
                        isIn("user_id", friendIds)
                        gte("created_at", dayStart.toString())
                    }
                    order("created_at", Order.DESCENDING)
                    limit(50)
                }
                .decodeList<SummaryActivityRow>()
            val xpByUser = LinkedHashMap<String, Int>()
            acts.forEach { a ->
                val xp = (a.payload?.get("xp") as? kotlinx.serialization.json.JsonPrimitive)
                    ?.content?.toIntOrNull() ?: 0
                xpByUser[a.userId] = (xpByUser[a.userId] ?: 0) + xp
            }
            if (xpByUser.isNotEmpty()) {
                val profs = client.postgrest.from("profiles")
                    .select(Columns.list("id", "display_name", "avatar_url")) {
                        filter { isIn("id", xpByUser.keys.toList()) }
                    }
                    .decodeList<SummaryFriendProfile>()
                friendsFinished = profs.map {
                    FriendFinish(it.id, it.displayName, it.avatarUrl, xpByUser[it.id] ?: 0)
                }
            }
        }

        return SessionSummary(
            score = h?.score ?: 0,
            tier = h?.tier ?: "steady",
            durationSeconds = h?.durationSeconds ?: 0,
            breaches = h?.breachesCount ?: 0,
            xpEarned = xpEarned.toInt(),
            lifetimeXp = lifetimeXp,
            prestige = prof?.prestigeLevel ?: 0,
            level = level,
            levelXpInto = into,
            levelXpSpan = span,
            streak = prof?.currentFocusStreak ?: 0,
            achievements = achievements,
            milestones = milestones,
            rankNow = rankNow,
            rankBefore = rankBefore,
            personality = prof?.productivityDna,
            friendsFinished = friendsFinished,
        )
    }

    // System time pulled through a helper so the queries above read cleanly.
    private fun nowMillis(): Long = System.currentTimeMillis()
}

/** Triangular level curve — web's levelFromXp: each level costs 500 XP more. */
fun levelFromXp(xp: Long): Triple<Int, Long, Long> {
    var level = 1
    var remaining = maxOf(0L, xp)
    var span = 1000L
    while (remaining >= span) {
        remaining -= span
        level += 1
        span += 500
    }
    return Triple(level, remaining, span)
}

@Serializable
internal data class SummaryHistoryRow(
    val score: Int = 0,
    val tier: String = "steady",
    @SerialName("duration_seconds") val durationSeconds: Int = 0,
    @SerialName("breaches_count") val breachesCount: Int = 0,
    @SerialName("xp_earned") val xpEarned: Long = 0,
)

@Serializable
internal data class SummaryProfileRow(
    @SerialName("lifetime_xp") val lifetimeXp: Long = 0,
    @SerialName("prestige_level") val prestigeLevel: Int = 0,
    @SerialName("current_focus_streak") val currentFocusStreak: Int = 0,
    @SerialName("productivity_dna") val productivityDna: String? = null,
)

@Serializable
internal data class FreshAwardRow(
    @SerialName("achievement_id") val achievementId: String,
    @SerialName("unlocked_at") val unlockedAt: String? = null,
)

@Serializable
internal data class FriendshipRow(
    @SerialName("requester_id") val requesterId: String,
    @SerialName("addressee_id") val addresseeId: String,
    val status: String,
)

@Serializable
internal data class SummaryActivityRow(
    @SerialName("user_id") val userId: String,
    val payload: kotlinx.serialization.json.JsonObject? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
internal data class SummaryFriendProfile(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class AwardCard(
    val id: String,
    val name: String,
    val description: String = "",
    val icon: String = "",
    val tier: String = "",
    @SerialName("xp_reward") val xpReward: Int = 0,
)

data class FriendFinish(
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?,
    val xp: Int,
)

data class SessionSummary(
    val score: Int,
    val tier: String,
    val durationSeconds: Int,
    val breaches: Int,
    val xpEarned: Int,
    val lifetimeXp: Long,
    val prestige: Int,
    val level: Int,
    val levelXpInto: Long,
    val levelXpSpan: Long,
    val streak: Int,
    val achievements: List<AwardCard>,
    val milestones: List<AwardCard>,
    val rankNow: Int,
    val rankBefore: Int,
    val personality: String?,
    val friendsFinished: List<FriendFinish>,
)
