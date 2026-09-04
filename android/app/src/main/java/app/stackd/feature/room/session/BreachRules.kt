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

    /**
     * Collect orientation for this long before deciding what "flat" is — long
     * enough for a hand to leave the phone, short enough not to stall the start.
     * Ported from the web's `CALIBRATION_MS`; the pair with
     * [CALIBRATION_MIN_SAMPLES] guards a device that emits orientation slowly.
     */
    const val CALIBRATION_MS = 800L
    const val CALIBRATION_MIN_SAMPLES = 5

    /** Window over which shake peaks are counted, and how many are needed. */
    const val SHAKE_WINDOW_MS = 600L
    const val SHAKE_MIN_PEAKS = 3

    fun tiltThreshold(mode: EnforcementMode): Float =
        if (mode == EnforcementMode.GENTLE) 60f else 30f

    fun shakeThreshold(mode: EnforcementMode): Float =
        if (mode == EnforcementMode.GENTLE) 22f else 16f

    fun magnitude(x: Float, y: Float, z: Float): Float = sqrt(x * x + y * y + z * z)

    /**
     * Whether enough evidence has accrued to fix the baseline. Both conditions
     * matter: elapsed time lets the phone settle, the sample count stops a
     * slow-emitting device from arming on two readings.
     */
    fun isCalibrationComplete(sampleCount: Int, elapsedMs: Long): Boolean =
        elapsedMs >= CALIBRATION_MS && sampleCount >= CALIBRATION_MIN_SAMPLES

    /**
     * The resting orientation, taken as the *median* of calibration samples —
     * not the first sample and not the mean. The last readings before a phone
     * comes to rest are the noisiest; one wild value drags a mean but barely
     * moves a median. Using the first sample (the old bug) let a phone caught
     * mid-placement define a tilted pose as level, so every later reading was
     * measured against a wrong zero.
     *
     * @return baseline (beta, gamma), or null if there were no samples.
     */
    fun computeBaseline(betas: List<Float>, gammas: List<Float>): Pair<Float, Float>? {
        if (betas.isEmpty() || gammas.isEmpty()) return null
        val b = betas.sorted()
        val g = gammas.sorted()
        return b[b.size / 2] to g[g.size / 2]
    }

    /**
     * Whether sustained agitation is present in the window. A single sample over
     * threshold is a table bump, a dropped book, a passing truck; real shaking
     * produces repeated peaks. Firing severe on one sample (the old bug) cost
     * honest users sessions to ambient vibration.
     *
     * @param window magnitudes with timestamps, any age — this filters by window.
     */
    fun isShakeSustained(
        window: List<TimedMagnitude>,
        threshold: Float,
        now: Long,
    ): Boolean =
        window.count { now - it.at <= SHAKE_WINDOW_MS && it.mag > threshold } >= SHAKE_MIN_PEAKS

    /** Drops samples aged out of the shake window, keeping it bounded. */
    fun pruneWindow(window: List<TimedMagnitude>, now: Long): List<TimedMagnitude> =
        window.filter { now - it.at <= SHAKE_WINDOW_MS }

    /** One accelerometer magnitude sample with its capture time. */
    data class TimedMagnitude(val mag: Float, val at: Long)

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

    /**
     * Shortest angular distance between two headings, in [0, 180].
     *
     * These angles live on a circle: Android's `getOrientation` roll (gamma)
     * spans -180..180, so a phone resting face-down sits right on the ±180 seam.
     * A plain `abs(current - baseline)` reads +178° vs -178° as 356° apart when
     * they are really 4° apart — which fired an instant LIFT/severe breach the
     * moment calibration locked a baseline near the seam (the web hook never hit
     * this because its DeviceOrientation gamma is only -90..90). Wrapping the
     * difference into [-180, 180] measures the real rotation.
     */
    fun delta(current: Float, baseline: Float): Float {
        val raw = (current - baseline) % 360f
        val wrapped = (raw + 540f) % 360f - 180f
        return abs(wrapped)
    }
}
