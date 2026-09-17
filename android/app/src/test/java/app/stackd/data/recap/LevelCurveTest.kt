package app.stackd.data.recap

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the triangular level curve ported from web's `levelFromXp`: level 1
 * costs 1000 XP, and each level after costs 500 more (1000, 1500, 2000, …).
 * A drift here would show the wrong level/progress in the session ceremony.
 */
class LevelCurveTest {

    @Test
    fun `zero and sub-first-level xp stay at level 1`() {
        assertEquals(Triple(1, 0L, 1000L), levelFromXp(0))
        assertEquals(Triple(1, 999L, 1000L), levelFromXp(999))
    }

    @Test
    fun `crossing the first threshold reaches level 2`() {
        // 1000 clears L1 exactly → L2, 0 into a 1500 span.
        assertEquals(Triple(2, 0L, 1500L), levelFromXp(1000))
        assertEquals(Triple(2, 499L, 1500L), levelFromXp(1499))
    }

    @Test
    fun `third level opens after 1000 + 1500`() {
        // 2500 = 1000 + 1500 → L3, span 2000.
        assertEquals(Triple(3, 0L, 2000L), levelFromXp(2500))
        assertEquals(Triple(3, 300L, 2000L), levelFromXp(2800))
    }

    @Test
    fun `negative xp is clamped to level 1`() {
        assertEquals(Triple(1, 0L, 1000L), levelFromXp(-50))
    }
}
