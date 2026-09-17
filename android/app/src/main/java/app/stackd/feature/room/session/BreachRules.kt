package app.stackd.feature.room.session

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The breach decision itself, lifted out of [BreachDetector] so it can be
 * exercised without a SensorManager or a real device.
 *
 * Holds the tuned constants ported from the web app's `useSensors` hook.
 * Everything here is pure: same inputs, same verdict, no Android types.
 */
object BreachRules {

    const val TILT_HOLD_MS = 3000L
    const val MINOR_THROTTLE_MS = 3000L
    const val LIFT_ANGLE = 90f
    const val VIBRATE_SEVERE_MS = 200L
    const val VIBRATE_MINOR_MS = 60L

    fun tiltThreshold(mode: EnforcementMode): Float =
        if (mode == EnforcementMode.GENTLE) 60f else 30f

    fun shakeThreshold(mode: EnforcementMode): Float =
        if (mode == EnforcementMode.GENTLE) 22f else 16f

    fun magnitude(x: Float, y: Float, z: Float): Float = sqrt(x * x + y * y + z * z)

    fun isShake(mode: EnforcementMode, x: Float, y: Float, z: Float): Boolean =
        magnitude(x, y, z) > shakeThreshold(mode)

    /** What a single orientation reading implies, given how long any tilt has held. */
    sealed interface Verdict {
        /** Within tolerance, and no prior tilt to settle. */
        data object Settled : Verdict

        /** Returned to tolerance after a brief tilt — a nudge, in gentle mode. */
        data object SettledAfterBriefTilt : Verdict

        /** Over threshold, but not yet long or steep enough to be severe. */
        data object TiltingNotYetSevere : Verdict

        data class Severe(val reason: BreachReason) : Verdict
    }

    /**
     * @param deltaBeta absolute degrees of front-back drift from baseline
     * @param deltaGamma absolute degrees of left-right drift from baseline
     * @param heldMs how long the current tilt has been over threshold, 0 if none
     */
    fun evaluateOrientation(
        mode: EnforcementMode,
        deltaBeta: Float,
        deltaGamma: Float,
        heldMs: Long,
    ): Verdict {
        val threshold = tiltThreshold(mode)
        val over = deltaBeta > threshold || deltaGamma > threshold

        if (!over) {
            return if (heldMs in 1 until TILT_HOLD_MS && mode == EnforcementMode.GENTLE) {
                Verdict.SettledAfterBriefTilt
            } else {
                Verdict.Settled
            }
        }

        val steep = deltaBeta > LIFT_ANGLE || deltaGamma > LIFT_ANGLE
        return if (mode == EnforcementMode.ABSOLUTE || heldMs > TILT_HOLD_MS || steep) {
            Verdict.Severe(if (steep) BreachReason.LIFT else BreachReason.TILT)
        } else {
            Verdict.TiltingNotYetSevere
        }
    }

    fun degreesFromRadians(radians: Float): Float =
        Math.toDegrees(radians.toDouble()).toFloat()

    fun delta(current: Float, baseline: Float): Float = abs(current - baseline)
}
