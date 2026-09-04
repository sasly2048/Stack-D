package app.stackd.feature.timeline

import app.stackd.data.room.FocusHistoryRow
import app.stackd.data.timeline.Reaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the two pieces of timeline logic that can be wrong without crashing:
 * the insight heuristic (ported from the web's deterministic branch) and the
 * optimistic reaction toggle.
 */
class ProactiveInsightsTest {

    private fun row(
        score: Int,
        hourUtc: Int = 9,
        durationSeconds: Int = 1800,
        breaches: Int = 0,
        day: Int = 1,
    ) = FocusHistoryRow(
        id = "s$score-$day-$hourUtc",
        score = score,
        xp = 10,
        durationSeconds = durationSeconds,
        breachesCount = breaches,
        tier = "steady",
        createdAt = "2026-08-%02dT%02d:00:00Z".format(day, hourUtc),
    )

    @Test
    fun `under three sessions falls back`() {
        val out = proactiveInsights(listOf(row(80), row(70)))
        assertNull(out.smartSchedule)
        assertEquals(75, out.focusPrediction.nextScore)
        assertEquals("low", out.focusPrediction.confidence)
    }

    @Test
    fun `best hour needs two samples`() {
        // 04:00 has the single highest score but only one session; 09:00 has two.
        val rows = listOf(
            row(99, hourUtc = 4, day = 1),
            row(80, hourUtc = 9, day = 2),
            row(70, hourUtc = 9, day = 3),
        )
        assertEquals(9, proactiveInsights(rows).smartSchedule?.hour)
    }

    @Test
    fun `falling scores predict lower and raise burnout risk`() {
        // Newest first: five weak sessions after five strong ones.
        val rows = List(5) { row(50, day = it + 1) } + List(5) { row(90, day = it + 6) }
        val out = proactiveInsights(rows)
        assertTrue(out.focusPrediction.nextScore < 50)
        assertTrue(out.burnout.signals.any { "down 40 points" in it })
        assertEquals("medium", out.burnout.risk)
        assertEquals("medium", out.focusPrediction.confidence)
    }

    @Test
    fun `two signals escalate to high risk`() {
        // 8h/day for three weeks plus a heavy breach rate.
        val rows = List(21) { row(70, durationSeconds = 8 * 3600, breaches = 3, day = it + 1) }
        val out = proactiveInsights(rows)
        assertEquals("high", out.burnout.risk)
        assertEquals(2, out.burnout.signals.size)
    }

    @Test
    fun `toggle adds removes and decrements`() {
        val added = applyToggle(emptyList(), "🔥")
        assertEquals(listOf(Reaction("🔥", 1, mine = true)), added)

        // Toggling my own single reaction off drops the bucket entirely.
        assertTrue(applyToggle(added, "🔥").isEmpty())

        // Someone else's reaction that I join, then leave, keeps their count.
        val theirs = listOf(Reaction("🔥", 2, mine = false))
        val joined = applyToggle(theirs, "🔥")
        assertEquals(3, joined.single().count)
        assertTrue(joined.single().mine)
        val left = applyToggle(joined, "🔥")
        assertEquals(2, left.single().count)
        assertFalse(left.single().mine)
    }

    @Test
    fun `duration formats like the web`() {
        assertEquals("25m", fmtDuration(1500))
        assertEquals("1h 30m", fmtDuration(5400))
        assertEquals("2h 0m", fmtDuration(7200))
    }
}
