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
}
