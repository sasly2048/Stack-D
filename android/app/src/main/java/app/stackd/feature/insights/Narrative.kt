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
