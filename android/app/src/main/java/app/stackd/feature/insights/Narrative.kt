package app.stackd.feature.insights

import app.stackd.data.room.FocusHistoryRow

/**
 * Pure client math the web keeps in `copy.ts` (narrative chapters) and
 * `forecast.functions.ts` (XP projection). Both are deterministic derivations
 * over data already on the client — no server call — so they live here and are
 * unit-tested.
 */

/* ------------------------------- Chapters -------------------------------- */

data class NarrativeChapter(val title: String, val subtitle: String, val minXp: Long)

val NARRATIVE_CHAPTERS = listOf(
    NarrativeChapter("The Arrival", "You showed up. That was the hard part.", 0),
    NarrativeChapter("The Kindling", "A first small fire — held long enough to warm.", 150),
    NarrativeChapter("The Cadence", "You've found a rhythm. It's yours now.", 600),
    NarrativeChapter("The Keeper", "You keep the silence. Others feel it.", 1500),
    NarrativeChapter("The Steward", "You hold space for the room, not just yourself.", 3500),
    NarrativeChapter("The Elder", "You are what a full attention looks like.", 7500),
)

fun chapterForXp(xp: Long): NarrativeChapter =
    NARRATIVE_CHAPTERS.last { xp >= it.minXp }

fun nextChapter(xp: Long): NarrativeChapter? =
    NARRATIVE_CHAPTERS.firstOrNull { it.minXp > xp }

/** 0..1 progress from the current chapter's floor to the next chapter's floor. */
fun chapterProgress(xp: Long): Float {
    val current = chapterForXp(xp)
    val next = nextChapter(xp) ?: return 1f
    val span = (next.minXp - current.minXp).toFloat()
    return if (span <= 0f) 1f else ((xp - current.minXp) / span).coerceIn(0f, 1f)
}

/* ------------------------------- Forecast -------------------------------- */

private val FORECAST_MILESTONES =
    listOf(10_000L, 25_000L, 50_000L, 100_000L, 250_000L, 500_000L, 1_000_000L)

data class Projection(val label: String, val targetXp: Long, val daysNeeded: Int)

data class Forecast(
    val avgDailyMinutes: Int,
    val avgDailyXp: Int,
    val currentXp: Long,
    val projections: List<Projection>,
    val weeklyForecastMinutes: Int,
    val monthlyForecastMinutes: Int,
)

/**
 * Projects XP milestones from the last 30 days' pace. [rows] must be that
 * window's history; [currentXp] the profile's lifetime XP. Mirrors the web's
 * `getForecast` exactly — 30-day average, next three unhit milestones.
 */
fun forecast(rows: List<FocusHistoryRow>, currentXp: Long): Forecast {
    if (rows.isEmpty()) {
        return Forecast(0, 0, currentXp, emptyList(), 0, 0)
    }
    val totalSec = rows.sumOf { it.durationSeconds }
    val totalXp = rows.sumOf { it.xp }
    val avgDailyMinutes = Math.round(totalSec / 60.0 / 30).toInt()
    val avgDailyXp = Math.round(totalXp / 30.0).toInt()
    val projections = FORECAST_MILESTONES.filter { it > currentXp }.take(3).map { target ->
        Projection(
            label = "${target / 1000}k XP",
            targetXp = target,
            daysNeeded = if (avgDailyXp > 0) {
                Math.ceil((target - currentXp).toDouble() / avgDailyXp).toInt()
            } else 9999,
        )
    }
    return Forecast(
        avgDailyMinutes = avgDailyMinutes,
        avgDailyXp = avgDailyXp,
        currentXp = currentXp,
        projections = projections,
        weeklyForecastMinutes = avgDailyMinutes * 7,
        monthlyForecastMinutes = avgDailyMinutes * 30,
    )
}

/* ---------------------------- Next-session rec --------------------------- */

data class SessionRecommendation(
    val durationMinutes: Int,
    val topic: String,
    val rationale: String,
    val confidence: String,
    val basedOnSessions: Int,
)

/**
 * Suggests the next focus session from recent history — the Atlas companion's
 * content.
 *
 * The web's `recommendNextSession` asks an LLM, but falls back to a
 * deterministic rule whenever that call is unavailable. That fallback is pure
 * math over the same history rows, so it's the honest, always-available version
 * — ported verbatim (same thresholds: 85/70 score gates, breach < 3, the 45/30/
 * 20-minute tiers, and the ≥15 / ≥5 confidence bands). No server AI dependency.
 *
 * [rows] is the user's recent focus history (web reads the last 20).
 */
fun recommendNextSession(rows: List<FocusHistoryRow>): SessionRecommendation {
    if (rows.isEmpty()) {
        return SessionRecommendation(
            durationMinutes = 20,
            topic = "First stack — settle in",
            rationale = "No history yet. Start light: twenty minutes is long enough to " +
                "feel the silence, short enough to complete cleanly.",
            confidence = "low",
            basedOnSessions = 0,
        )
    }
    val basedOnSessions = rows.size
    val avgScore = Math.round(rows.sumOf { it.score }.toDouble() / rows.size).toInt()
    val avgMin = Math.round(rows.sumOf { it.durationSeconds }.toDouble() / rows.size / 60).toInt()
    val totalBreaches = rows.sumOf { it.breachesCount }

    val tier = when {
        avgScore >= 85 && totalBreaches < 3 -> 45
        avgScore >= 70 -> 30
        else -> 20
    }
    return SessionRecommendation(
        durationMinutes = tier,
        topic = if (avgScore >= 80) "Deep work, one task" else "Rebuild the baseline",
        rationale = "Averaging $avgScore/100 across $basedOnSessions sessions at $avgMin min. " +
            if (avgScore >= 80) "Room to push longer." else "Shorter, cleaner runs first.",
        confidence = when {
            basedOnSessions >= 15 -> "high"
            basedOnSessions >= 5 -> "medium"
            else -> "low"
        },
        basedOnSessions = basedOnSessions,
    )
}
