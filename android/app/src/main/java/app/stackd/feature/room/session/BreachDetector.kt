package app.stackd.feature.room.session

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

enum class BreachReason(val wire: String) {
    TILT("tilt"),
    LIFT("lift"),
    SHAKE("shake"),

    /**
     * The app left the foreground. Named for the web app's `visibilitychange`
     * origin so the value stays comparable across platforms in analytics.
     */
    TAB_HIDDEN("tab-hidden"),

    /**
     * The screen stopped being held awake. On the web this arrives as its own
     * Wake Lock `release` event; Android surfaces no such callback, so in
     * practice [TAB_HIDDEN] fires first for the same underlying situation.
     * Kept because the column accepts it and the web build still emits it.
     */
    WAKE_LOST("wake-lost"),

    MANUAL("manual"),
}

enum class BreachSeverity(val wire: String) {
    MINOR("minor"),
    SEVERE("severe"),
}

enum class EnforcementMode(val wire: String) {
    GENTLE("gentle"),
    ABSOLUTE("absolute"),
}

/**
 * Multi-signal "phone is face-down and still" detector — a direct port of the
 * web app's `useSensors` hook (src/hooks/use-sensors.ts).
 *
 * Every threshold and interval below is copied deliberately rather than
 * re-derived: they are tuned product values, and a session that breaches at
 * different angles on Android than on the web is a different product.
 *
 * Like the web hook, this only *detects*. Reporting the breach to the server
 * is the caller's job, which keeps the sensor loop free of network concerns.
 */
class BreachDetector(
    private val sensorManager: SensorManager,
    private val vibrate: (durationMs: Long) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) : SensorEventListener {

    /** Held in a property so the room can rebuild its lambda every timer tick
     *  without tearing down and rebinding sensor listeners (web "Optim 01"). */
    var onBreach: ((BreachReason, BreachSeverity) -> Unit)? = null

    var mode: EnforcementMode = EnforcementMode.ABSOLUTE

    private var baselineBeta: Float? = null
    private var baselineGamma: Float? = null
    private var tiltStartedAt: Long = 0
    private var firedSevere: Boolean = false
    private var lastMinorAt: Long = 0
    private var running: Boolean = false

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    fun start() {
        if (running) return
        running = true
        reset()
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        if (!running) return
        running = false
        sensorManager.unregisterListener(this)
    }

    /** Clears the baseline and the severe latch, re-arming for a new session. */
    fun reset() {
        baselineBeta = null
        baselineGamma = null
        tiltStartedAt = 0
        firedSevere = false
        lastMinorAt = 0
    }

    /**
     * The app left the foreground. Covers both the web's `tab-hidden` and its
     * `wake-lost` — Android reports no distinct wake-lock release, and either
     * way the phone is no longer sitting untouched on the table.
     */
    fun onAppBackgrounded() {
        if (!running) return
        fireSevere(BreachReason.TAB_HIDDEN)
    }

    fun fireManual() = fireSevere(BreachReason.MANUAL)

    override fun onSensorChanged(event: SensorEvent) {
        if (!running) return
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> handleOrientation(event)
            Sensor.TYPE_ACCELEROMETER -> handleMotion(event)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun handleOrientation(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientation)

        // getOrientation yields radians as [azimuth, pitch, roll]; the web hook
        // works in DeviceOrientationEvent's degrees, where beta is front-back
        // (pitch) and gamma is left-right (roll).
        val beta = Math.toDegrees(orientation[1].toDouble()).toFloat()
        val gamma = Math.toDegrees(orientation[2].toDouble()).toFloat()

        val bBase = baselineBeta
        val gBase = baselineGamma
        if (bBase == null || gBase == null) {
            baselineBeta = beta
            baselineGamma = gamma
            return
        }

        val db = BreachRules.delta(beta, bBase)
        val dg = BreachRules.delta(gamma, gBase)

        // Start the clock before evaluating, so a reading that is both the
        // first over-threshold sample and already steep still reads as a lift.
        if (tiltStartedAt == 0L && (db > BreachRules.tiltThreshold(mode) || dg > BreachRules.tiltThreshold(mode))) {
            tiltStartedAt = now()
        }
        val held = if (tiltStartedAt == 0L) 0L else now() - tiltStartedAt

        when (val verdict = BreachRules.evaluateOrientation(mode, db, dg, held)) {
            BreachRules.Verdict.Settled -> tiltStartedAt = 0
            BreachRules.Verdict.SettledAfterBriefTilt -> {
                fireMinor(BreachReason.TILT)
                tiltStartedAt = 0
            }
            BreachRules.Verdict.TiltingNotYetSevere -> Unit
            is BreachRules.Verdict.Severe -> fireSevere(verdict.reason)
        }
    }

    private fun handleMotion(event: SensorEvent) {
        val x = event.values.getOrElse(0) { 0f }
        val y = event.values.getOrElse(1) { 0f }
        val z = event.values.getOrElse(2) { 0f }
        if (BreachRules.isShake(mode, x, y, z)) fireSevere(BreachReason.SHAKE)
    }

    private fun fireSevere(reason: BreachReason) {
        if (firedSevere) return
        firedSevere = true
        vibrate(BreachRules.VIBRATE_SEVERE_MS)
        onBreach?.invoke(reason, BreachSeverity.SEVERE)
    }

    private fun fireMinor(reason: BreachReason) {
        val timestamp = now()
        if (timestamp - lastMinorAt < BreachRules.MINOR_THROTTLE_MS) return
        lastMinorAt = timestamp
        vibrate(BreachRules.VIBRATE_MINOR_MS)
        onBreach?.invoke(reason, BreachSeverity.MINOR)
    }
}
