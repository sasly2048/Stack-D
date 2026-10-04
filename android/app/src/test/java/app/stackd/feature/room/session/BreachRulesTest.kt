package app.stackd.feature.room.session

import app.stackd.feature.room.session.BreachRules.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the thresholds ported from the web app's `useSensors` hook. These are
 * tuned product values — if one drifts, a session breaches at a different angle
 * on Android than it does on the web, which is a real behavioural divergence
 * rather than an implementation detail.
 */
class BreachRulesTest {

    // --- thresholds -------------------------------------------------------

    @Test
    fun `tilt threshold is 60 degrees in gentle and 30 in absolute`() {
        assertEquals(60f, BreachRules.tiltThreshold(EnforcementMode.GENTLE))
        assertEquals(30f, BreachRules.tiltThreshold(EnforcementMode.ABSOLUTE))
    }

    @Test
    fun `shake threshold is 22 in gentle and 16 in absolute`() {
        assertEquals(22f, BreachRules.shakeThreshold(EnforcementMode.GENTLE))
        assertEquals(16f, BreachRules.shakeThreshold(EnforcementMode.ABSOLUTE))
    }

    // --- absolute mode ----------------------------------------------------

    @Test
    fun `absolute mode breaches severely the moment threshold is crossed`() {
        val verdict = BreachRules.evaluateOrientation(
            mode = EnforcementMode.ABSOLUTE,
            deltaBeta = 31f,
            deltaGamma = 0f,
            heldMs = 0,
        )
        assertEquals(Verdict.Severe(BreachReason.TILT), verdict)
    }

    @Test
    fun `absolute mode tolerates drift below threshold`() {
        val verdict = BreachRules.evaluateOrientation(
            mode = EnforcementMode.ABSOLUTE,
            deltaBeta = 29f,
            deltaGamma = 29f,
            heldMs = 0,
        )
        assertEquals(Verdict.Settled, verdict)
    }

    // --- gentle mode ------------------------------------------------------

    @Test
    fun `gentle mode holds off until the tilt has been sustained`() {
        val verdict = BreachRules.evaluateOrientation(
            mode = EnforcementMode.GENTLE,
            deltaBeta = 61f,
            deltaGamma = 0f,
            heldMs = 1_000,
        )
        assertEquals(Verdict.TiltingNotYetSevere, verdict)
    }

    @Test
    fun `gentle mode escalates once the tilt outlasts the hold window`() {
        val verdict = BreachRules.evaluateOrientation(
            mode = EnforcementMode.GENTLE,
            deltaBeta = 61f,
            deltaGamma = 0f,
            heldMs = BreachRules.TILT_HOLD_MS + 1,
        )
        assertEquals(Verdict.Severe(BreachReason.TILT), verdict)
    }

    @Test
    fun `gentle mode counts a brief tilt that settles back as a nudge`() {
        val verdict = BreachRules.evaluateOrientation(
            mode = EnforcementMode.GENTLE,
            deltaBeta = 0f,
            deltaGamma = 0f,
            heldMs = 500,
        )
        assertEquals(Verdict.SettledAfterBriefTilt, verdict)
    }

    @Test
    fun `absolute mode does not report nudges`() {
        // Only gentle mode has a minor tier; absolute already fired severe.
        val verdict = BreachRules.evaluateOrientation(
            mode = EnforcementMode.ABSOLUTE,
            deltaBeta = 0f,
            deltaGamma = 0f,
            heldMs = 500,
        )
        assertEquals(Verdict.Settled, verdict)
    }

    @Test
    fun `settling with no prior tilt is not a nudge`() {
        val verdict = BreachRules.evaluateOrientation(
            mode = EnforcementMode.GENTLE,
            deltaBeta = 0f,
            deltaGamma = 0f,
            heldMs = 0,
        )
        assertEquals(Verdict.Settled, verdict)
    }

    // --- lift -------------------------------------------------------------

    @Test
    fun `a steep angle reads as a lift rather than a tilt`() {
        val verdict = BreachRules.evaluateOrientation(
            mode = EnforcementMode.GENTLE,
            deltaBeta = 91f,
            deltaGamma = 0f,
            heldMs = 0,
        )
        assertEquals(Verdict.Severe(BreachReason.LIFT), verdict)
    }

    @Test
    fun `a steep angle short-circuits the gentle hold window`() {
        // Past 90 degrees the phone has clearly been picked up: no waiting.
        val verdict = BreachRules.evaluateOrientation(
            mode = EnforcementMode.GENTLE,
            deltaBeta = 0f,
            deltaGamma = 120f,
            heldMs = 10,
        )
        assertEquals(Verdict.Severe(BreachReason.LIFT), verdict)
    }

    @Test
    fun `gamma drift is treated the same as beta drift`() {
        val verdict = BreachRules.evaluateOrientation(
            mode = EnforcementMode.ABSOLUTE,
            deltaBeta = 0f,
            deltaGamma = 31f,
            heldMs = 0,
        )
        assertEquals(Verdict.Severe(BreachReason.TILT), verdict)
    }

    // --- shake ------------------------------------------------------------

    private fun mag(m: Float, at: Long) = BreachRules.TimedMagnitude(m, at)
    private val absThresh = BreachRules.shakeThreshold(EnforcementMode.ABSOLUTE) // 16
    private val gentleThresh = BreachRules.shakeThreshold(EnforcementMode.GENTLE) // 22

    @Test
    fun `a single spike over threshold is not a shake`() {
        // The core fail-safe: one sample must never end a session. A table bump
        // or passing truck produces exactly this — one magnitude over threshold.
        val window = listOf(mag(30f, 100))
        assertTrue(!BreachRules.isShakeSustained(window, absThresh, 100))
    }

    @Test
    fun `three peaks inside the window is a real shake`() {
        val window = listOf(mag(20f, 0), mag(20f, 200), mag(20f, 400))
        assertTrue(BreachRules.isShakeSustained(window, absThresh, 500))
    }

    @Test
    fun `peaks that aged out of the window do not count`() {
        // Two peaks 700ms and 650ms ago are past the 600ms window; only the
        // fresh one remains, so a lone recent spike is still not a shake.
        val window = listOf(mag(20f, 0), mag(20f, 50), mag(20f, 700))
        assertTrue(!BreachRules.isShakeSustained(window, absThresh, 700))
    }

    @Test
    fun `gentle mode absorbs agitation that absolute mode flags`() {
        // Sustained 18-magnitude agitation: over the 16 absolute threshold,
        // under the 22 gentle one.
        val window = listOf(mag(18f, 0), mag(18f, 200), mag(18f, 400))
        assertTrue(BreachRules.isShakeSustained(window, absThresh, 500))
        assertTrue(!BreachRules.isShakeSustained(window, gentleThresh, 500))
    }

    @Test
    fun `pruneWindow drops aged samples and keeps fresh ones`() {
        // Window is 600ms, inclusive. At now=700: at=0 is 700ms old (dropped),
        // at=100 is exactly 600ms old (kept), at=700 is fresh (kept).
        val window = listOf(mag(20f, 0), mag(20f, 100), mag(20f, 700))
        val kept = BreachRules.pruneWindow(window, 700)
        assertEquals(2, kept.size)
    }

    @Test
    fun `computeBaseline takes the median, not the first sample`() {
        // A phone caught mid-placement emits a wild first reading (80f) then
        // settles near 2f. The median must reject the outlier — the whole point
        // of calibrating instead of trusting sample one.
        val betas = listOf(80f, 1f, 2f, 3f, 2f)
        val gammas = listOf(70f, 0f, 1f, 2f, 1f)
        val base = BreachRules.computeBaseline(betas, gammas)!!
        assertEquals(2f, base.first, 0.0001f)
        assertEquals(1f, base.second, 0.0001f)
    }

    @Test
    fun `calibration completes only after both time and sample floors`() {
        // Enough samples but too soon: not complete.
        assertTrue(!BreachRules.isCalibrationComplete(sampleCount = 5, elapsedMs = 100))
        // Enough time but too few samples: not complete.
        assertTrue(!BreachRules.isCalibrationComplete(sampleCount = 2, elapsedMs = 1000))
        // Both floors met: complete.
        assertTrue(BreachRules.isCalibrationComplete(sampleCount = 5, elapsedMs = 800))
    }

    @Test
    fun `magnitude combines all three axes`() {
        assertEquals(5f, BreachRules.magnitude(3f, 4f, 0f), 0.0001f)
    }

    // --- conversions ------------------------------------------------------

    @Test
    fun `radians convert to the degrees the web hook works in`() {
        assertEquals(180f, BreachRules.degreesFromRadians(Math.PI.toFloat()), 0.001f)
        assertEquals(-90f, BreachRules.degreesFromRadians((-Math.PI / 2).toFloat()), 0.001f)
    }

    @Test
    fun `delta is direction-agnostic`() {
        assertEquals(40f, BreachRules.delta(current = -20f, baseline = 20f), 0.0001f)
        assertEquals(40f, BreachRules.delta(current = 20f, baseline = -20f), 0.0001f)
    }

    // --- face-down gate ---------------------------------------------------

    @Test
    fun `face-down requires the screen axis pointing down`() {
        // rotationMatrix[8]: +1 screen-up, 0 vertical, -1 screen flat down.
        assertTrue(BreachRules.isFaceDown(-1.0f))          // dead flat, face down
        assertTrue(BreachRules.isFaceDown(-0.7f))          // ~45° off, still counts
        assertTrue(!BreachRules.isFaceDown(-0.5f))         // too tilted to arm
        assertTrue(!BreachRules.isFaceDown(0.0f))          // held vertical
        assertTrue(!BreachRules.isFaceDown(1.0f))          // face up — must never arm
    }

    // --- strict placement gate --------------------------------------------

    @Test
    fun `a flat face-down still phone counts as placed`() {
        // Resting face-down: z ≈ -9.8, x/y near zero, magnitude ≈ 1 g.
        assertTrue(BreachRules.isPlacedSample(0.1f, -0.2f, -9.8f))
    }

    @Test
    fun `an upright or face-up phone is never placed`() {
        assertTrue(!BreachRules.isPlacedSample(0f, 9.8f, 0f))    // portrait upright
        assertTrue(!BreachRules.isPlacedSample(0f, 0f, 9.8f))    // face-up flat
        assertTrue(!BreachRules.isPlacedSample(0f, 0f, 0f))      // free fall
    }

    @Test
    fun `face-down but tilted past the horizontal bound is not placed`() {
        // z is over threshold but a 5f horizontal component (> 4.5 max) means it's
        // propped at an angle, not stacked flat.
        assertTrue(!BreachRules.isPlacedSample(5f, 0f, -8.5f))
    }

    @Test
    fun `face-down but in motion is not placed`() {
        // Right orientation, but total magnitude 13 is 3 g off rest — the phone is
        // being moved, not resting. Must not arm.
        assertTrue(!BreachRules.isPlacedSample(0f, 0f, -13f))
    }

    @Test
    fun `gravity angle is zero at rest and grows smoothly with tilt`() {
        // Same vector → 0°. This is the resting case that the Euler roll seam
        // wrongly reported as ~180°; the gravity angle has no such pole.
        assertEquals(0f, BreachRules.gravityAngleDelta(0f, 0f, -9.8f, 0f, 0f, -9.8f), 0.01f)
        // Face-down → vertical (upright) is a 90° tilt.
        assertEquals(90f, BreachRules.gravityAngleDelta(0f, 0f, -9.8f, 0f, -9.8f, 0f), 0.5f)
        // Face-down → face-up is a full 180° flip.
        assertEquals(180f, BreachRules.gravityAngleDelta(0f, 0f, -9.8f, 0f, 0f, 9.8f), 0.5f)
    }

    @Test
    fun `gravity angle crosses the absolute threshold on a real lift`() {
        // A ~35° tilt off the stack (still well short of upright) already exceeds
        // the 30° absolute threshold → severe on the first sample.
        val tilt = BreachRules.gravityAngleDelta(0f, 0f, -9.8f, 0f, -5.6f, -8.03f)
        assertTrue("expected >30, got $tilt", tilt > 30f)
    }

    @Test
    fun `gravity angle is safe on degenerate zero input`() {
        assertEquals(0f, BreachRules.gravityAngleDelta(0f, 0f, 0f, 0f, 0f, -9.8f), 0.01f)
    }

    @Test
    fun `delta wraps across the plus-minus 180 seam`() {
        // Face-down roll sits on the ±180 seam. +178° vs -178° is a 4° wobble,
        // not a 356° flip — the bug that fired an instant LIFT breach at start.
        assertEquals(4f, BreachRules.delta(current = -178f, baseline = 178f), 0.0001f)
        assertEquals(4f, BreachRules.delta(current = 178f, baseline = -178f), 0.0001f)
        // A genuine 90° lift from a seam baseline still reads as 90°, not 270°.
        assertEquals(90f, BreachRules.delta(current = -92f, baseline = 178f), 0.0001f)
        // Distance never exceeds 180° (the far side is always the short way round).
        assertEquals(180f, BreachRules.delta(current = 0f, baseline = 180f), 0.0001f)
    }
}
