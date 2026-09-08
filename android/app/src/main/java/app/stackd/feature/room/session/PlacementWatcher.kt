package app.stackd.feature.room.session

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * A short-lived accelerometer watch that confirms the phone is actually stacked
 * — flat, face-down, and still — before a session is allowed to start.
 *
 * This runs in the LOBBY→start window, while the screen is on and the user is
 * placing the phone. It is deliberately separate from [BreachDetector]: that one
 * lives in the foreground service and guards an already-running session with the
 * screen off; this one is the *gate* that decides whether the session (and its
 * server clock) may begin at all. The decision logic is shared —
 * [BreachRules.isPlacedSample] plus the [BreachRules.PLACEMENT_HOLD_MS] hold — so
 * "placed" means the same thing here as it does to the running detector.
 *
 * [onConfirmed] fires exactly once, on the main-looper thread the SensorManager
 * delivers on. Call [stop] when the session starts or the user leaves — the
 * watcher never stops itself except right before firing [onConfirmed].
 */
class PlacementWatcher(
    private val sensorManager: SensorManager,
    private val now: () -> Long = System::currentTimeMillis,
    private val onConfirmed: () -> Unit,
) : SensorEventListener {

    private var placedSince: Long = 0
    private var done: Boolean = false
    private var accel: Sensor? = null

    /**
     * Begins watching. Returns false if the device has no accelerometer — the
     * caller then can't enforce placement and must decide how to proceed (we
     * confirm immediately, since blocking forever on a missing sensor would trap
     * the user in a session that can never start).
     */
    fun start(): Boolean {
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val sensor = accel ?: return false
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        return true
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (done || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values.getOrElse(0) { 0f }
        val y = event.values.getOrElse(1) { 0f }
        val z = event.values.getOrElse(2) { 0f }
        val t = now()

        if (BreachRules.isPlacedSample(x, y, z)) {
            if (placedSince == 0L) placedSince = t
            if (t - placedSince >= BreachRules.PLACEMENT_HOLD_MS) {
                done = true
                stop()
                android.util.Log.i("StackdBreach", "placement watcher confirmed: z=$z x=$x y=$y")
                onConfirmed()
            }
        } else {
            // Any break in the pose restarts the hold — a phone still in motion,
            // upright, or face-up never accrues toward the confirmation.
            placedSince = 0
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
