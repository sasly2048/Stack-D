package app.stackd.feature.timeline

import app.stackd.data.room.FocusHistoryRow
import java.time.Instant
import java.time.ZoneOffset

/**
 * Pure port of the web's `proactive-ai.functions.ts`.
 *
 * Despite the name nothing here is a model: the web computes all three panels
 * from the last three weeks of `focus_history` and only *optionally* asks an
 * LLM to reword the copy, falling back to exactly the strings below when that
 * call fails. Android ships the deterministic branch — same numbers, same
 * fallback prose. The rewording is server-side only (the AI key never reaches
 * a client), so there is nothing further to port.
 */

data class SmartSchedule(val hour: Int, val label: String, val rationale: String)

data class FocusPrediction(
    val nextScore: Int,
    /** low | medium | high */
    val confidence: String,
    val note: String,
)

data class Burnout(
    /** low | medium | high */
    val risk: String,
    val signals: List<String>,
    val recommendation: String,
)

data class ProactiveInsight(
    val smartSchedule: SmartSchedule?,
    val focusPrediction: FocusPrediction,
    val burnout: Burnout,
)

private val FALLBACK = ProactiveInsight(
    smartSchedule = null,
    focusPrediction = FocusPrediction(
        nextScore = 75,
        confidence = "low",
        note = "Not enough sessions yet — the model needs a week of data.",
    ),
    burnout = Burnout(
        risk = "low",
        signals = emptyList(),
        recommendation = "Keep the cadence steady. Rest days count as data.",
    ),
)

/** [rows] must be the caller's last ~21 days of history, newest first. */
fun proactiveInsights(rows: List<FocusHistoryRow>): ProactiveInsight {
    if (rows.size < 3) return FALLBACK

    // Best hour by average score, ignoring hours with a single sample — one
    // lucky 98 at 4am is not a schedule.
    val byHour = rows.groupBy { row ->
        row.createdAt?.let {
            runCatching { Instant.parse(it).atZone(ZoneOffset.UTC).hour }.getOrNull()
        }
    }.filterKeys { it != null }
    var bestHour: Int? = null
    var bestAvg = -1.0
    byHour.forEach { (hour, hourRows) ->
        val avg = hourRows.sumOf { it.score }.toDouble() / hourRows.size
        if (hourRows.size >= 2 && avg > bestAvg) {
            bestAvg = avg
            bestHour = hour
        }
    }

    val avgOf = { xs: List<FocusHistoryRow> ->
        if (xs.isEmpty()) 0.0 else xs.sumOf { it.score }.toDouble() / xs.size
    }
    val recentAvg = avgOf(rows.take(5))
    val delta = recentAvg - avgOf(rows.drop(5).take(5))
    val nextScore = Math.round((recentAvg + delta * 0.3).coerceIn(0.0, 100.0)).toInt()
    val confidence = when {
        rows.size >= 15 -> "high"
        rows.size >= 8 -> "medium"
        else -> "low"
    }

    val signals = buildList {
        val totalMinutes = rows.sumOf { it.durationSeconds } / 60.0
        val breachRate = rows.sumOf { it.breachesCount }.toDouble() / rows.size
        if (totalMinutes / 21 > 240) add("Averaging over 4h focus/day for 3 weeks.")
        if (breachRate > 2) add("Breach rate climbing — attention slipping mid-session.")
        if (delta < -8) add("Recent scores down ${Math.abs(Math.round(delta))} points.")
    }
    val risk = when {
        signals.size >= 2 -> "high"
        signals.size == 1 -> "medium"
        else -> "low"
    }

    return ProactiveInsight(
        smartSchedule = bestHour?.let { h ->
            SmartSchedule(
                hour = h,
                label = "${h.toString().padStart(2, '0')}:00 UTC window",
                rationale = "Your average score in this hour is ${Math.round(bestAvg)}.",
            )
        },
        focusPrediction = FocusPrediction(
            nextScore = nextScore,
            confidence = confidence,
            note = when {
                delta > 3 -> "Momentum is up. Next session should extend the run."
                delta < -3 -> "Signal degrading. Consider a shorter, cleaner block."
                else -> "Stable. Repeat what worked."
            },
        ),
        burnout = Burnout(
            risk = risk,
            signals = signals,
            recommendation = when (risk) {
                "high" -> "Cut one session tomorrow. Sleep the deficit off."
                "medium" -> "Hold volume flat this week; recovery is compounding."
                else -> "Cadence is sustainable. Extend one block by 15 minutes."
            },
        ),
    )
}
