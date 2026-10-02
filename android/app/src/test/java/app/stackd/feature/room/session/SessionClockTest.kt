package app.stackd.feature.room.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The drift cases are the point here: a session backgrounded for ten minutes
 * must come back with ten minutes gone, not with whatever a throttled timer
 * managed to count.
 */
class SessionClockTest {

    private val start = 1_700_000_000_000L
    private val thirtyMinutes = 30 * 60L

    @Test
    fun `elapsed tracks wall clock, not ticks`() {
        assertEquals(0L, SessionClock.elapsedSeconds(start, start))
        assertEquals(60L, SessionClock.elapsedSeconds(start, start + 60_000))
        assertEquals(600L, SessionClock.elapsedSeconds(start, start + 600_000))
    }

    @Test
    fun `ten minutes in the background are still ten minutes gone`() {
        val resumed = start + 10 * 60 * 1000
        assertEquals(600L, SessionClock.elapsedSeconds(start, resumed))
        assertEquals(
            thirtyMinutes - 600,
            SessionClock.remainingSeconds(start, thirtyMinutes, resumed),
        )
    }

    @Test
    fun `remaining never goes negative once the session is over`() {
        val wayPast = start + 60 * 60 * 1000
        assertEquals(0L, SessionClock.remainingSeconds(start, thirtyMinutes, wayPast))
    }

    @Test
    fun `a clock that jumped backwards does not report negative elapsed`() {
        assertEquals(0L, SessionClock.elapsedSeconds(start, start - 5_000))
    }

    @Test
    fun `an unstarted session reports its full duration`() {
        assertEquals(
            thirtyMinutes,
            SessionClock.remainingSeconds(startedAtMillis = 0, thirtyMinutes, nowMillis = start),
        )
        assertEquals(0L, SessionClock.elapsedSeconds(startedAtMillis = 0, nowMillis = start))
    }

    @Test
    fun `end time is the anchor the notification counts down to`() {
        assertEquals(start + thirtyMinutes * 1000, SessionClock.endsAtMillis(start, thirtyMinutes))
    }

    @Test
    fun `expiry flips exactly at the end, not before`() {
        val oneSecondLeft = start + (thirtyMinutes - 1) * 1000
        val exactlyDone = start + thirtyMinutes * 1000

        assertFalse(SessionClock.isExpired(start, thirtyMinutes, oneSecondLeft))
        assertTrue(SessionClock.isExpired(start, thirtyMinutes, exactlyDone))
    }

    @Test
    fun `an unstarted session has not expired`() {
        assertFalse(SessionClock.isExpired(0, thirtyMinutes, start))
    }

    @Test
    fun `progress runs zero to one and clamps past the end`() {
        assertEquals(0f, SessionClock.progress(start, thirtyMinutes, start), 0.001f)
        assertEquals(
            0.5f,
            SessionClock.progress(start, thirtyMinutes, start + 15 * 60 * 1000),
            0.001f,
        )
        assertEquals(
            1f,
            SessionClock.progress(start, thirtyMinutes, start + 90 * 60 * 1000),
            0.001f,
        )
    }

    @Test
    fun `progress is zero when there is nothing to divide by`() {
        assertEquals(0f, SessionClock.progress(start, 0, start + 1000), 0.001f)
        assertEquals(0f, SessionClock.progress(0, thirtyMinutes, start), 0.001f)
    }

    @Test
    fun `formatting stays MM SS until an hour, then grows`() {
        assertEquals("00:00", SessionClock.format(0))
        assertEquals("00:09", SessionClock.format(9))
        assertEquals("01:30", SessionClock.format(90))
        assertEquals("30:00", SessionClock.format(thirtyMinutes))
        assertEquals("59:59", SessionClock.format(3599))
        assertEquals("1:00:00", SessionClock.format(3600))
        assertEquals("2:05:07", SessionClock.format(2 * 3600 + 5 * 60 + 7))
    }

    @Test
    fun `formatting a negative duration shows zero rather than a minus sign`() {
        assertEquals("00:00", SessionClock.format(-30))
    }
}
