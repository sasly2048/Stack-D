package app.stackd.feature.room.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import app.stackd.MainActivity
import app.stackd.R
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Keeps a live session visible AND guarded while the app is not on screen.
 *
 * The notification counts down on its own: [NotificationCompat.setWhen] anchors
 * it to the session's real end time and `setUsesChronometer` lets the system
 * render the ticking text. That means no per-second wakeups from us, and the
 * countdown stays anchored to wall-clock time instead of drifting with a
 * handler the OS is free to throttle.
 *
 * Crucially, the service also *owns the breach detection*. The web guards a
 * session with page-visibility + wake-lock; on Android the equivalent
 * "phone-stacking" model is: lock the phone face-down, and the sensors keep
 * watching with the screen off. A [BreachDetector] bound to a Composable dies
 * the instant the screen locks — exactly when the stack most needs watching.
 * Running it here, in a foreground service, is what makes "lift the phone =
 * breach" work with the screen off. Breach and calibration events are published
 * on process-wide flows the ViewModel collects.
 */
class FocusSessionService : Service() {

    private var sensorManager: SensorManager? = null
    private var detector: BreachDetector? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val endsAtMillis = intent?.getLongExtra(EXTRA_ENDS_AT, 0L) ?: 0L
        val roomCode = intent?.getStringExtra(EXTRA_ROOM_CODE).orEmpty()
        val modeWire = intent?.getStringExtra(EXTRA_MODE).orEmpty()
        running.value = RunningSession(roomCode, endsAtMillis)

        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification(roomCode, endsAtMillis))

        startDetector(modeWire)

        // Don't recreate on kill: a session resurrected without its room state
        // would show a countdown for something the user is no longer in.
        return START_NOT_STICKY
    }

    /**
     * Builds and starts the breach detector once per service start. Idempotent:
     * a repeated ACTIVE row re-issues start() with the same extras and must not
     * spin up a second sensor listener over the first.
     */
    private fun startDetector(modeWire: String) {
        if (detector != null) return
        val manager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        sensorManager = manager
        detector = BreachDetector(
            sensorManager = manager,
            vibrate = { ms -> vibrate(ms) },
        ).apply {
            mode = if (modeWire == EnforcementMode.GENTLE.wire) {
                EnforcementMode.GENTLE
            } else {
                EnforcementMode.ABSOLUTE
            }
            onBreach = { reason, severity ->
                breachEvents.tryEmit(BreachEvent(reason, severity))
            }
            onCalibrated = { calibrated.tryEmit(Unit) }
            onCapability = { cap -> capabilities.tryEmit(cap) }
            start()
        }
    }

    private fun buildNotification(roomCode: String, endsAtMillis: Long): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_session)
            .setContentTitle(
                if (roomCode.isBlank()) "Session in progress" else "Room $roomCode",
            )
            .setContentText("Stack is holding.")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(true)
            .setWhen(endsAtMillis)
            .setUsesChronometer(true)
            .apply {
                // Counting down to the end time reads as "time left"; counting
                // up from it would read as "time over".
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setChronometerCountDown(true)
                }
            }
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Focus session",
                // The point is a quiet, glanceable timer — not an alert.
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows the remaining time while a session is running."
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            },
        )
    }

    /** Fires a haptic pulse, matching the web's `navigator.vibrate`. */
    private fun vibrate(ms: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(ms)
        }
    }

    override fun onDestroy() {
        detector?.stop()
        detector = null
        sensorManager = null
        running.value = null
        super.onDestroy()
    }

    /** What the in-app floating pill shows while a session runs. */
    data class RunningSession(val roomCode: String, val endsAtMillis: Long)

    /** A breach detected by the service's sensor loop, for the ViewModel to record. */
    data class BreachEvent(val reason: BreachReason, val severity: BreachSeverity)

    companion object {
        private const val CHANNEL_ID = "focus_session"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "app.stackd.action.STOP_SESSION"
        private const val EXTRA_ENDS_AT = "ends_at"
        private const val EXTRA_ROOM_CODE = "room_code"
        private const val EXTRA_MODE = "mode"

        /**
         * Process-wide mirror of the foreground timer, for the in-app floating
         * pill (web's floating-timer). Null whenever no session is running.
         */
        val running = kotlinx.coroutines.flow.MutableStateFlow<RunningSession?>(null)

        /**
         * Breach + calibration + capability signals from the service's detector.
         * A small replay buffer covers the moment between the service emitting
         * and the ViewModel's collector attaching — the room subscribes as it
         * enters ACTIVE, and the calibration event lands right after start().
         * extraBufferCapacity keeps tryEmit non-suspending from the sensor loop.
         */
        val breachEvents = MutableSharedFlow<BreachEvent>(replay = 0, extraBufferCapacity = 8)
        val calibrated = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
        val capabilities = MutableSharedFlow<SensorCapability>(replay = 1, extraBufferCapacity = 1)

        /**
         * @param endsAtMillis wall-clock epoch time the session is due to end.
         * @param modeWire the enforcement mode ("gentle"/"absolute") the detector arms at.
         */
        fun start(context: Context, roomCode: String, endsAtMillis: Long, modeWire: String) {
            // Reset the replay caches so a new session never delivers the prior
            // session's calibration/capability to a fresh collector.
            calibrated.resetReplayCache()
            capabilities.resetReplayCache()
            val intent = Intent(context, FocusSessionService::class.java)
                .putExtra(EXTRA_ENDS_AT, endsAtMillis)
                .putExtra(EXTRA_ROOM_CODE, roomCode)
                .putExtra(EXTRA_MODE, modeWire)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, FocusSessionService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
