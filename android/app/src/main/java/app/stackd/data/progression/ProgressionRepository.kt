package app.stackd.data.progression

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Challenges + seasons — web's `challenges.functions.ts` / `seasons.functions.ts`.
 * Challenge periods use UTC day / Monday-start UTC week exactly like the web's
 * isoDay/isoWeekStart, so both clients look up the same progress rows.
 */

@Serializable
internal data class ChallengeCatalogRow(
    val id: String,
    val name: String,
    val description: String,
    val cadence: String,
    val metric: String,
    val target: Int,
    @SerialName("xp_reward") val xpReward: Int,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
internal data class ChallengeProgressRow(
    @SerialName("challenge_id") val challengeId: String,
    @SerialName("period_start") val periodStart: String,
    val progress: Int = 0,
    @SerialName("completed_at") val completedAt: String? = null,
)

/** Web's `ChallengeRow`: catalog joined with this period's progress. */
data class Challenge(
    val id: String,
    val name: String,
    val description: String,
    val cadence: String,
    val metric: String,
    val target: Int,
    val xpReward: Int,
    val progress: Int,
    val completedAt: String?,
)

@Serializable
data class Season(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
    @SerialName("xp_multiplier") val xpMultiplier: Double = 1.0,
)

@Serializable
data class SeasonStanding(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val xp: Long = 0,
    val rank: Int = 0,
)

class ProgressionRepository(private val client: SupabaseClient) {

    private fun utcToday(): String = LocalDate.now(ZoneOffset.UTC).toString()

    private fun utcWeekStart(): String =
        LocalDate.now(ZoneOffset.UTC).with(DayOfWeek.MONDAY).toString()

    suspend fun listChallenges(userId: String): List<Challenge> {
        val today = utcToday()
        val weekStart = utcWeekStart()
        val catalog = client.postgrest.from("challenges")
            .select(
                Columns.list(
                    "id", "name", "description", "cadence", "metric", "target",
                    "xp_reward", "sort_order",
                ),
            ) { order("sort_order", Order.ASCENDING) }
            .decodeList<ChallengeCatalogRow>()
        val progress = client.postgrest.from("challenge_progress")
            .select(Columns.list("challenge_id", "period_start", "progress", "completed_at")) {
                filter {
                    eq("user_id", userId)
                    isIn("period_start", listOf(today, weekStart))
                }
            }
            .decodeList<ChallengeProgressRow>()

        return catalog.map { c ->
            val period = if (c.cadence == "daily") today else weekStart
            val p = progress.firstOrNull { it.challengeId == c.id && it.periodStart == period }
            Challenge(
                id = c.id, name = c.name, description = c.description,
                cadence = c.cadence, metric = c.metric, target = c.target,
                xpReward = c.xpReward,
                progress = p?.progress ?: 0,
                completedAt = p?.completedAt,
            )
        }
    }

    suspend fun activeSeason(): Season? {
        val now = java.time.Instant.now().toString()
        return client.postgrest.from("seasons")
            .select(
                Columns.list(
                    "id", "name", "description", "starts_at", "ends_at", "xp_multiplier",
                ),
            ) {
                filter {
                    lte("starts_at", now)
                    gte("ends_at", now)
                }
                order("starts_at", Order.DESCENDING)
                limit(1)
            }
            .decodeList<Season>()
            .firstOrNull()
    }

    /** Definer RPC — `season_participants` itself is locked to definer access. */
    suspend fun standings(seasonId: String, limit: Int = 50): List<SeasonStanding> =
        client.postgrest.rpc(
            "season_standings",
            buildJsonObject {
                put("_season_id", seasonId)
                put("_limit", limit)
            },
        ).decodeList()

    suspend fun joinSeason(seasonId: String) {
        client.postgrest.rpc(
            "join_season",
            buildJsonObject { put("_season_id", seasonId) },
        )
    }
}
