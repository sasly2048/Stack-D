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

    @Test
    fun `resting phone at one g is never a shake`() {
        // Gravity alone reads ~9.81 on the vertical axis.
        assertTrue(!BreachRules.isShake(EnforcementMode.ABSOLUTE, 0f, 0f, 9.81f))
        assertTrue(!BreachRules.isShake(EnforcementMode.GENTLE, 0f, 0f, 9.81f))
    }

    @Test
    fun `a jolt past the threshold is a shake`() {
        assertTrue(BreachRules.isShake(EnforcementMode.ABSOLUTE, 0f, 0f, 17f))
        assertTrue(BreachRules.isShake(EnforcementMode.GENTLE, 0f, 0f, 23f))
    }

    @Test
    fun `gentle mode absorbs a jolt that absolute mode would flag`() {
        assertTrue(BreachRules.isShake(EnforcementMode.ABSOLUTE, 0f, 0f, 18f))
        assertTrue(!BreachRules.isShake(EnforcementMode.GENTLE, 0f, 0f, 18f))
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
}
