package app.stackd.feature.room.session

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The focus/abandonment split decides the score, so it's asserted against the
 * web's exact behaviour (`room.$code.tsx` 350–361). Millisecond inputs; the
 * caller divides by 1000 later.
 */
class FinalizeInputsTest {

    @Test
    fun `held the whole session is all focus, no abandonment`() {
        // started at 1000, ended at 1_801_000 → 1800s held, never breached.
        val split = FinalizeInputs.compute(
            startedAtMillis = 1_000,
            endedAtMillis = 1_801_000,
            breached = false,
            breachAtMillis = null,
        )
        assertEquals(1_800_000, split.focusMillis)
        assertEquals(0, split.abandonmentMillis)
    }

    // A realistic server start — epoch 0 (year 1970) reads as "no start" on both
    // platforms (JS `started_at ? … : 0`), so tests must use a real instant.
    private val start = 1_700_000_000_000L

    @Test
    fun `breached partway splits focus before and abandonment after`() {
        // breach 10 min in, end 30 min in.
        val split = FinalizeInputs.compute(
            startedAtMillis = start,
            endedAtMillis = start + 1_800_000,
            breached = true,
            breachAtMillis = start + 600_000,
        )
        assertEquals(600_000, split.focusMillis) // 10 min focused
        assertEquals(1_200_000, split.abandonmentMillis) // 20 min abandoned
    }

    @Test
    fun `breached flag without a breach timestamp falls back to full focus`() {
        // Defensive: a breached participant whose breach_at hasn't propagated is
        // credited the full session rather than losing everything to a null.
        val split = FinalizeInputs.compute(start, start + 1_800_000, breached = true, breachAtMillis = null)
        assertEquals(1_800_000, split.focusMillis)
        assertEquals(0, split.abandonmentMillis)
    }

    @Test
    fun `no server start means zero focus`() {
        // Without a server started_at there is no trustworthy elapsed time.
        val split = FinalizeInputs.compute(0, 1_800_000, breached = false, breachAtMillis = null)
        assertEquals(0, split.focusMillis)
        assertEquals(0, split.abandonmentMillis)
    }

    @Test
    fun `end before start clamps to zero rather than going negative`() {
        val split = FinalizeInputs.compute(start + 1_000_000, start, breached = false, breachAtMillis = null)
        assertEquals(0, split.focusMillis)
    }

    @Test
    fun `a breach-then-finalize round trip matches a hand-computed score`() {
        // 30-min target, broke at 10 min with one severe breach.
        // focus = 600s, abandonment = 1200s - 15s grace = 1185 penalty.
        // raw = (600/1800)*100 - (40 + 1185) = 33.33 - 1225 → clamps to 0.
        val split = FinalizeInputs.compute(start, start + 1_800_000, breached = true, breachAtMillis = start + 600_000)
        val result = FocusScore.compute(
            targetSeconds = 1800.0,
            focusSeconds = split.focusMillis / 1000.0,
            severeBreaches = 1,
            minorBreaches = 0,
            abandonmentSeconds = split.abandonmentMillis / 1000.0,
        )
        assertEquals(0, result.score)
        assertEquals(FocusScore.Tier.COMPROMISED, result.tier)
        assertEquals(600, result.focusSecondsInt)
    }
}
