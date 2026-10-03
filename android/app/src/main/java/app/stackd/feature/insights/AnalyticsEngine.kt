package app.stackd.feature.insights

import app.stackd.core.parseIsoMillis
import app.stackd.data.room.FocusHistoryRow
import java.time.Instant
import java.time.ZoneId

/**
 * Pure derivations over the caller's own `focus_history` rows — the web's
 * `getAnalytics` and `getProductivityDna` math ported line for line, computed
 * on-device instead of in a server function (same rows, same caps). Hours and
 * days use the device zone, matching the per-user-timezone server behavior.
 */
object AnalyticsEngine {

    data class Trait(val label: String, val value: Int)

    data class Dna(
        val archetype: String,
        val traits: List<Trait>,
        val peakHour: Int,
        val consistencyScore: Int,
        val totalSessions: Int,
        val signature: String,
    )

    data class HourBucket(val hour: Int, val seconds: Int, val sessions: Int, val scoreAvg: Int)

    data class Totals(
        val sessions: Int,
        val hours: Double,
        val xp: Long,
        val avgScore: Int,
        val breaches: Int,
        val cleanSessions: Int,
        val cleanRate: Int,
        val breachesPerSession: Double,
    )

    private val ARCHETYPES = mapOf(
        "Depth" to "The Diver", "Precision" to "The Marksman", "Discipline" to "The Monk",
        "Flow" to "The Channeler", "Consistency" to "The Metronome", "Volume" to "The Marathoner",
    )
    private val MODIFIERS = mapOf(
        "Depth" to "Deep", "Precision" to "Precise", "Discipline" to "Disciplined",
        "Flow" to "In-Flow", "Consistency" to "Steady", "Volume" to "Relentless",
    )

    private fun hourAndDay(iso: String?, zone: ZoneId): Pair<Int, String>? {
        val ms = parseIsoMillis(iso) ?: return null
        val zdt = Instant.ofEpochMilli(ms).atZone(zone)
        return zdt.hour to zdt.toLocalDate().toString()
    }

    /** The web's getProductivityDna over a 60-day window. */
    fun dna(rows: List<FocusHistoryRow>, zone: ZoneId = ZoneId.systemDefault()): Dna {
        val total = rows.size.coerceAtLeast(1)
        val avgScore = rows.sumOf { it.score }.toDouble() / total
        val avgDur = rows.sumOf { it.durationSeconds }.toDouble() / total
        val perfect = rows.count { it.breachesCount == 0 }
        val flow = rows.count { it.score >= 95 }

        val hours = IntArray(24)
        val days = mutableSetOf<String>()
        rows.forEach { r ->
            hourAndDay(r.createdAt, zone)?.let { (h, d) ->
                hours[h]++
                days += d
            }
        }
        val peakHour = hours.indices.maxByOrNull { hours[it] } ?: 0
        val consistency = ((days.size / 60.0) * 100).toInt().coerceAtMost(100)

        val traits = listOf(
            Trait("Depth", ((avgDur / 3600) * 100).toInt().coerceAtMost(100)),
            Trait("Precision", Math.round(avgScore).toInt()),
            Trait("Discipline", ((perfect.toDouble() / total) * 100).toInt()),
            Trait("Flow", ((flow.toDouble() / total) * 100).toInt()),
            Trait("Consistency", consistency),
            Trait("Volume", ((rows.size / 60.0) * 100).toInt().coerceAtMost(100)),
        )

        val ranked = traits.sortedByDescending { it.value }
        val primary = ARCHETYPES[ranked[0].label] ?: "The Wanderer"
        val second = ranked.getOrNull(1)
        val archetype = if (
            second != null && second.value >= 40 && ranked[0].value - second.value <= 15
        ) "$primary, ${MODIFIERS[second.label].orEmpty()}".trim() else primary

        // 6-char signature: last base-36 digit of each trait, uppercased.
        val signature = traits.joinToString("") { t ->
            t.value.toString(36).padStart(2, '0').takeLast(1).uppercase()
        }

        return Dna(archetype, traits, peakHour, consistency, rows.size, signature)
    }

    fun hourBuckets(rows: List<FocusHistoryRow>, zone: ZoneId = ZoneId.systemDefault()): List<HourBucket> {
        val secs = IntArray(24); val count = IntArray(24); val scoreSum = IntArray(24)
        rows.forEach { r ->
            hourAndDay(r.createdAt, zone)?.let { (h, _) ->
                secs[h] += r.durationSeconds
                count[h]++
                scoreSum[h] += r.score
            }
        }
        return (0 until 24).map { h ->
            HourBucket(h, secs[h], count[h], if (count[h] > 0) scoreSum[h] / count[h] else 0)
        }
    }

    fun totals(rows: List<FocusHistoryRow>): Totals {
        val sessions = rows.size
        val breaches = rows.sumOf { it.breachesCount }
        val clean = rows.count { it.breachesCount == 0 }
        return Totals(
            sessions = sessions,
            hours = rows.sumOf { it.durationSeconds }.toDouble() / 3600,
            xp = rows.sumOf { it.xp.toLong() },
            avgScore = if (sessions > 0) rows.sumOf { it.score } / sessions else 0,
            breaches = breaches,
            cleanSessions = clean,
            cleanRate = if (sessions > 0) (clean * 100) / sessions else 0,
            breachesPerSession = if (sessions > 0) breaches.toDouble() / sessions else 0.0,
        )
    }
}
