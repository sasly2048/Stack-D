package app.stackd.feature.room.session

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Parity checks against the web's `computeFocusScore` — a score computed here
 * and one computed on the web for the same session land in the same
 * `focus_history` table and the same leaderboards, so they must agree exactly.
 *
 * Values are hand-derived from the spec, not from the implementation, so a
 * regression in the port is caught rather than blessed.
 */
class FocusScoreTest {

    @Test
    fun `perfect session earns the flow tier and full xp`() {
        // 30 min held perfectly: 1800/1800*100 = 100 → flow (x1.5).
        // xp = floor(100 * (1800/60) * 1.5) = floor(4500).
        val r = FocusScore.compute(
            targetSeconds = 1800.0,
            focusSeconds = 1800.0,
            severeBreaches = 0,
            minorBreaches = 0,
        )
        assertEquals(100, r.score)
        assertEquals(FocusScore.Tier.FLOW, r.tier)
        assertEquals(4500, r.xp)
        assertEquals(FocusScore.SCORING_VERSION, r.scoringVersion)
    }

    @Test
    fun `half the target with no breach is fragmented and earns no xp`() {
        // 900/1800*100 = 50 → fragmented (x0). Multiplier zero ⇒ zero xp.
        val r = FocusScore.compute(1800.0, 900.0, 0, 0)
        assertEquals(50, r.score)
        assertEquals(FocusScore.Tier.FRAGMENTED, r.tier)
        assertEquals(0, r.xp)
    }

    @Test
    fun `tier reads the unrounded score, not the rounded one (v2)`() {
        // Construct a raw 84.5: focus/target*100 = 84.5, no penalty. It rounds
        // to 85, but v2 must classify it as STEADY (< 85), not PRISTINE — the
        // whole point of the version bump. xp uses the unrounded 84.5.
        // 845/1000*100 = 84.5, steady (x0.5).
        val r = FocusScore.compute(1000.0, 845.0, 0, 0)
        assertEquals(85, r.score) // display value rounds
        assertEquals(FocusScore.Tier.STEADY, r.tier) // reward reads 84.5
        // xp = floor(84.5 * (845/60) * 0.5) = floor(84.5 * 14.0833.. * 0.5)
        assertEquals(595, r.xp)
    }

    @Test
    fun `breaches subtract fixed penalties`() {
        // Full focus (100) minus one severe (40) and one minor (10) = 50.
        val r = FocusScore.compute(1800.0, 1800.0, severeBreaches = 1, minorBreaches = 1)
        assertEquals(50, r.score)
        assertEquals(50, r.penalty)
        assertEquals(FocusScore.Tier.FRAGMENTED, r.tier)
    }

    @Test
    fun `abandonment past the grace window subtracts second-for-second`() {
        // 40s abandonment, 15s grace ⇒ 25 penalty. 100 - 25 = 75 → steady.
        val r = FocusScore.compute(1800.0, 1800.0, 0, 0, abandonmentSeconds = 40.0)
        assertEquals(75, r.score)
        assertEquals(25, r.abandonmentPenalty)
        assertEquals(FocusScore.Tier.STEADY, r.tier)
    }

    @Test
    fun `abandonment within grace is free`() {
        val r = FocusScore.compute(1800.0, 1800.0, 0, 0, abandonmentSeconds = 15.0)
        assertEquals(0, r.abandonmentPenalty)
        assertEquals(100, r.score)
    }
}
