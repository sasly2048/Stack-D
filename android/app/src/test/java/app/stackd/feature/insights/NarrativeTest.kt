package app.stackd.feature.insights

import app.stackd.data.room.FocusHistoryRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Guards the narrative-chapter thresholds and the XP forecast math. */
class NarrativeTest {

    @Test
    fun `chapter picks the highest cleared threshold`() {
        assertEquals("The Arrival", chapterForXp(0).title)
        assertEquals("The Arrival", chapterForXp(149).title)
        assertEquals("The Kindling", chapterForXp(150).title)
        assertEquals("The Cadence", chapterForXp(600).title)
        assertEquals("The Elder", chapterForXp(7500).title)
        assertEquals("The Elder", chapterForXp(999_999).title)
    }

    @Test
    fun `next chapter is the first higher threshold, null at the top`() {
        assertEquals("The Kindling", nextChapter(0)?.title)
        assertEquals("The Cadence", nextChapter(150)?.title)
        assertNull(nextChapter(7500))
    }

    @Test
    fun `chapter progress fills toward the next floor`() {
        // 375 sits halfway between Kindling (150) and Cadence (600) → 0.5.
        assertEquals(0.5f, chapterProgress(375), 0.0001f)
        // At or past the top chapter progress is full.
        assertEquals(1f, chapterProgress(10_000), 0f)
    }

    private fun row(xp: Int, durationSeconds: Int) = FocusHistoryRow(
        id = "x$xp", score = 80, xp = xp, durationSeconds = durationSeconds,
        breachesCount = 0, tier = "steady", createdAt = "2026-08-01T09:00:00Z",
    )

    @Test
    fun `forecast averages over 30 days and projects next milestones`() {
        // 30 sessions, 1000 XP each = 30000 total → 1000 XP/day.
        val rows = List(30) { row(1000, 1800) }
        val f = forecast(rows, currentXp = 5000)
        assertEquals(1000, f.avgDailyXp)
        // Next unhit milestones above 5000: 10k, 25k, 50k.
        assertEquals(listOf("10k XP", "25k XP", "50k XP"), f.projections.map { it.label })
        // 10k needs (10000-5000)/1000 = 5 days.
        assertEquals(5, f.projections.first().daysNeeded)
    }

    @Test
    fun `empty history yields a zeroed forecast`() {
        val f = forecast(emptyList(), currentXp = 1234)
        assertEquals(0, f.avgDailyXp)
        assertEquals(1234, f.currentXp)
        assertEquals(0, f.projections.size)
    }

    private fun scored(score: Int, breaches: Int, minutes: Int = 30) = FocusHistoryRow(
        id = "s$score-$breaches", score = score, xp = 100,
        durationSeconds = minutes * 60, breachesCount = breaches, tier = "steady",
        createdAt = "2026-08-01T09:00:00Z",
    )

    @Test
    fun `recommendation gates duration on score and breaches`() {
        // High score AND low breaches → nudge up to 45 min.
        assertEquals(45, recommendNextSession(List(3) { scored(90, 0) }).durationMinutes)
        // High score but too many breaches → falls to the mid tier.
        assertEquals(30, recommendNextSession(List(3) { scored(90, 5) }).durationMinutes)
        // Mid score → 30 min.
        assertEquals(30, recommendNextSession(List(3) { scored(72, 1) }).durationMinutes)
        // Low score → 20 min, rebuild.
        val low = recommendNextSession(List(3) { scored(50, 4) })
        assertEquals(20, low.durationMinutes)
        assertEquals("Rebuild the baseline", low.topic)
    }

    @Test
    fun `recommendation confidence follows session count`() {
        assertEquals("low", recommendNextSession(List(3) { scored(80, 0) }).confidence)
        assertEquals("medium", recommendNextSession(List(8) { scored(80, 0) }).confidence)
        assertEquals("high", recommendNextSession(List(20) { scored(80, 0) }).confidence)
    }

    @Test
    fun `empty history returns the first-stack recommendation`() {
        val r = recommendNextSession(emptyList())
        assertEquals(20, r.durationMinutes)
        assertEquals(0, r.basedOnSessions)
        assertEquals("low", r.confidence)
    }
}
