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

    /**
     * How face-down the screen must be before calibration will lock a baseline
     * and arm the session. `rotationMatrix[8]` is the world-up component of the
     * device's screen-normal axis: +1 screen-up, 0 vertical, −1 screen-down.
     * −0.7 ≈ within ~45° of flat-face-down — generous enough for a phone resting
     * on a slightly uneven stack, strict enough that an upright or face-up phone
     * never calibrates. This is the gate that stops a session arming (and locking
     * a wrong baseline) before the phone is actually stacked.
     */
    const val FACE_DOWN_Z_MAX = -0.7f

    fun isFaceDown(screenUpComponent: Float): Boolean = screenUpComponent <= FACE_DOWN_Z_MAX

    /* ----------------------- strict placement gate ----------------------- */
    //
    // The session must not arm until the phone is verifiably face-down AND at
    // rest — measured directly from the accelerometer's gravity vector, which is
    // unambiguous where beta/gamma are not (they can't tell face-up from
    // face-down when flat). At rest the accelerometer reads gravity: face-down is
    // z ≈ -9.8 with x,y near zero. We require that pose to HOLD still for a short
    // window, so a phone waved screen-down in passing can't arm a session.

    /** Standard gravity, m/s². Accelerometer at rest reads ~this magnitude. */
    const val GRAVITY = 9.81f

    /**
     * How negative the accelerometer Z must be to count as face-down: ≤ -8.0
     * ≈ within ~35° of flat, generous for an uneven stack but firmly excluding
     * upright (z≈0) and face-up (z≈+9.8).
     */
    const val PLACEMENT_Z_MAX = -8.0f

    /**
     * Max horizontal tilt while placed: √(x²+y²) must stay under this. At true
     * flat it's ~0; this bounds how far the stack can lean and still arm.
     */
    const val PLACEMENT_XY_MAX = 4.5f

    /**
     * Stillness tolerance: each sample's total magnitude must stay within this of
     * 1 g. A phone in motion (being flipped, carried) swings well outside; a
     * resting one barely moves. Rejects "face-down but still moving".
     */
    const val PLACEMENT_STILL_TOLERANCE = 1.5f

    /** The pose must hold this long before the session arms. */
    const val PLACEMENT_HOLD_MS = 700L

    /** True if this single gravity sample looks like a flat, face-down, still phone. */
    fun isPlacedSample(x: Float, y: Float, z: Float): Boolean {
        if (z > PLACEMENT_Z_MAX) return false
        if (sqrt(x * x + y * y) > PLACEMENT_XY_MAX) return false
        val mag = magnitude(x, y, z)
        return abs(mag - GRAVITY) <= PLACEMENT_STILL_TOLERANCE
    }

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
