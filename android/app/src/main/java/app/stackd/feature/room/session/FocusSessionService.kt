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
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import app.stackd.MainActivity
import app.stackd.R
import app.stackd.StackdApplication
import app.stackd.core.workmanager.BreachOutbox
import app.stackd.core.workmanager.FinalizePayload
import app.stackd.core.workmanager.FinalizeQueue
import app.stackd.core.workmanager.FinalizeQueueWorker
import app.stackd.core.workmanager.PendingBreach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Keeps a live session visible AND guarded while the app is not on screen.
 *
 * The notification counts down on its own: [NotificationCompat.setWhen] anchors
 * it to the session's real end time and `setUsesChronometer` lets the system
 * render the ticking text, so there are no per-second wakeups from us.
 *
 * The service owns the whole guard, independent of the room screen:
 *  - the [BreachDetector] (so "lift the phone = breach" works with the screen off),
 *  - **recording** each breach — through the durable [BreachOutbox], so a breach
 *    detected offline, or after the room screen was closed or the task swiped
 *    away, still reaches the server,
 *  - a partial wake lock for the session, so a suspended CPU can't sit on a lift,
 *  - the end of the session: the host's copy completes the room at the end time,
 *    and if no room screen is attached to finalize, it parks a finalize request.
 *
 * Breach and calibration events are also published on process-wide flows so an
 * attached room screen can update its UI instantly.
 */
class FocusSessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var detector: BreachDetector? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var endJob: Job? = null
    private var session: Session? = null

    /** Identity of the running session — who to record breaches as, and when it ends. */
    private data class Session(
        val roomId: String,
        val participantId: String,
        val ownerId: String,
        val isHost: Boolean,
        val startedAtMillis: Long,
        val endsAtMillis: Long,
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val endsAtMillis = intent?.getLongExtra(EXTRA_ENDS_AT, 0L) ?: 0L
        val roomCode = intent?.getStringExtra(EXTRA_ROOM_CODE).orEmpty()
        val modeWire = intent?.getStringExtra(EXTRA_MODE).orEmpty()
        running.value = RunningSession(roomCode, endsAtMillis)

        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification(roomCode, endsAtMillis))

        // A re-entered room screen re-issues start(); keep the session we already
        // have rather than rebuilding the guard (and its calibration) mid-stack.
        if (session == null) {
            session = intent?.let { sessionFrom(it, endsAtMillis) }
            acquireWakeLock(endsAtMillis)
            startDetector(modeWire)
            scheduleEnd()
        }

        // Don't recreate on kill: a session resurrected without its identity
        // extras would guard nothing and could not record what it saw.
        return START_NOT_STICKY
    }

    private fun sessionFrom(intent: Intent, endsAtMillis: Long): Session? {
        val roomId = intent.getStringExtra(EXTRA_ROOM_ID) ?: return null
        val participantId = intent.getStringExtra(EXTRA_PARTICIPANT_ID) ?: return null
        val ownerId = intent.getStringExtra(EXTRA_OWNER_ID) ?: return null
        return Session(
            roomId = roomId,
            participantId = participantId,
            ownerId = ownerId,
            isHost = intent.getBooleanExtra(EXTRA_IS_HOST, false),
            startedAtMillis = intent.getLongExtra(EXTRA_STARTED_AT, 0L),
            endsAtMillis = endsAtMillis,
        )
    }

    /** Builds and starts the breach detector once per service lifetime. */
    private fun startDetector(modeWire: String) {
        if (detector != null) return
        val manager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
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
                recordBreach(reason, severity)
                // A severe breach disarms this participant (web parity). Stop the
                // sensors — nothing further can change the outcome — but keep the
                // service for the end-of-session duties below.
                if (severity == BreachSeverity.SEVERE) detector?.stop()
            }
            onCalibrated = { calibrated.tryEmit(Unit) }
            onCapability = { cap -> capabilities.tryEmit(cap) }
            start()
        }
        if (app.stackd.BuildConfig.DEBUG) {
            android.util.Log.i("StackdBreach", "service detector started (mode=$modeWire)")
        }
    }

    /** Parks the breach durably, then tries to send it straight away. */
    private fun recordBreach(reason: BreachReason, severity: BreachSeverity) {
        val s = session ?: return
        val target = (s.endsAtMillis - s.startedAtMillis).coerceAtLeast(1L)
        val elapsed = (System.currentTimeMillis() - s.startedAtMillis).coerceIn(0L, target)
        val breach = PendingBreach(
            id = java.util.UUID.randomUUID().toString(),
            roomId = s.roomId,
            participantId = s.participantId,
            reason = reason.wire,
            severity = severity.wire,
            integrity = ((elapsed * 100) / target).toInt(),
            owner = s.ownerId,
            at = System.currentTimeMillis(),
        )
        scope.launch { enqueueAndSend(applicationContext, breach) }
    }

    /**
     * Ends the session on schedule even with no room screen alive: the host's
     * device completes the room (idempotent server-side), and if nobody is
     * attached to compute and submit this user's result, a finalize request is
     * parked for the worker. An attached room screen does all of this itself and
     * stops the service first, which cancels this job.
     */
    private fun scheduleEnd() {
        val s = session ?: return
        endJob = scope.launch {
            delay((s.endsAtMillis - System.currentTimeMillis()).coerceAtLeast(0L))
            if (s.isHost) {
                runCatching { container().rooms.completeSession(s.roomId) }
            }
            delay(ATTACHED_FINALIZE_GRACE_MS)
            if (breachEvents.subscriptionCount.value == 0) parkFinalize(s)
            stopSelf()
        }
    }

    /**
     * The server derives score, XP, duration and tier from its own timestamps
     * and recorded breaks (finalize_server_authoritative_score); the payload's
     * figures are placeholders that satisfy the RPC's types. Abandonment (the
     * one client-only input) is unknown here, so it stays 0.
     */
    private suspend fun parkFinalize(s: Session) {
        val targetSeconds = ((s.endsAtMillis - s.startedAtMillis) / 1000).toDouble()
        val placeholder = FocusScore.compute(
            targetSeconds = targetSeconds,
            focusSeconds = targetSeconds,
            severeBreaches = 0,
            minorBreaches = 0,
            abandonmentSeconds = 0.0,
        )
        FinalizeQueue(applicationContext).enqueue(
            FinalizePayload(
                roomId = s.roomId,
                score = placeholder.score,
                xp = placeholder.xp,
                durationSeconds = placeholder.focusSecondsInt,
                breachesCount = 0,
                tier = placeholder.tier.key,
                scoringVersion = placeholder.scoringVersion,
                owner = s.ownerId,
                queuedAt = System.currentTimeMillis(),
            ),
        )
        FinalizeQueueWorker.flush(applicationContext, s.ownerId)
    }

    private fun acquireWakeLock(endsAtMillis: Long) {
        if (wakeLock != null) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        // Bounded by the session itself plus the end-of-session grace, so a lost
        // stop can never pin the CPU awake indefinitely.
        val timeout = (endsAtMillis - System.currentTimeMillis())
            .coerceAtLeast(0L) + ATTACHED_FINALIZE_GRACE_MS + WAKE_LOCK_SLACK_MS
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "stackd:focus-session").apply {
            setReferenceCounted(false)
            acquire(timeout)
        }
    }

    private fun container() = (application as StackdApplication).container

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
            // Counting down to the end time reads as "time left"; counting up
            // from it would read as "time over".
            .setChronometerCountDown(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createChannel() {
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
        vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onDestroy() {
        detector?.stop()
        detector = null
        endJob = null
        session = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        running.value = null
        // Pending breach sends are already durable in the outbox; cancelling
        // here only abandons the in-flight attempt, which the worker retries.
        scope.cancel()
        super.onDestroy()
    }

    /** What the in-app floating pill shows while a session runs. */
    data class RunningSession(val roomCode: String, val endsAtMillis: Long)

    /** A breach detected by the service's sensor loop, for the room screen's UI. */
    data class BreachEvent(val reason: BreachReason, val severity: BreachSeverity)

    companion object {
        private const val CHANNEL_ID = "focus_session"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_ENDS_AT = "ends_at"
        private const val EXTRA_STARTED_AT = "started_at"
        private const val EXTRA_ROOM_CODE = "room_code"
        private const val EXTRA_ROOM_ID = "room_id"
        private const val EXTRA_PARTICIPANT_ID = "participant_id"
        private const val EXTRA_OWNER_ID = "owner_id"
        private const val EXTRA_IS_HOST = "is_host"
        private const val EXTRA_MODE = "mode"

        /** An attached room screen finalizes within this window after the end. */
        private const val ATTACHED_FINALIZE_GRACE_MS = 90_000L
        private const val WAKE_LOCK_SLACK_MS = 60_000L

        /**
         * Process-wide mirror of the foreground timer, for the in-app floating
         * pill (web's floating-timer). Null whenever no session is running.
         */
        val running = kotlinx.coroutines.flow.MutableStateFlow<RunningSession?>(null)

        /**
         * UI signals from the service's detector. Recording no longer depends on
         * anyone collecting these — the service records each breach itself — so
         * a dropped event here only costs an instant UI flip, never the breach.
         * The replay buffers cover the gap between the service emitting and a
         * room screen's collector attaching.
         */
        val breachEvents = MutableSharedFlow<BreachEvent>(replay = 0, extraBufferCapacity = 8)
        val calibrated = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
        val capabilities = MutableSharedFlow<SensorCapability>(replay = 1, extraBufferCapacity = 1)

        /**
         * Parks [breach] durably and tries to send it now; if that can't finish,
         * the network-constrained worker picks it up (and holds this user's
         * result until it does). Shared by the service's sensor breaches and the
         * room screen's touch breaches so there is one recording path.
         */
        suspend fun enqueueAndSend(context: Context, breach: PendingBreach) {
            val outbox = BreachOutbox(context)
            outbox.enqueue(breach)
            val send = FinalizeQueueWorker.breachSubmitter
            val clear = send != null && outbox.drain(breach.owner) { send(it) }
            if (!clear) FinalizeQueueWorker.flush(context, breach.owner)
        }

        fun start(
            context: Context,
            roomCode: String,
            roomId: String,
            participantId: String,
            ownerId: String,
            isHost: Boolean,
            startedAtMillis: Long,
            endsAtMillis: Long,
            modeWire: String,
        ) {
            // Only a NEW session clears the replay caches. Re-entering a running
            // room must still see its calibration, or the screen would wait on a
            // calibration event that already fired.
            if (running.value?.roomCode != roomCode) {
                calibrated.resetReplayCache()
                capabilities.resetReplayCache()
            }
            val intent = Intent(context, FocusSessionService::class.java)
                .putExtra(EXTRA_ENDS_AT, endsAtMillis)
                .putExtra(EXTRA_STARTED_AT, startedAtMillis)
                .putExtra(EXTRA_ROOM_CODE, roomCode)
                .putExtra(EXTRA_ROOM_ID, roomId)
                .putExtra(EXTRA_PARTICIPANT_ID, participantId)
                .putExtra(EXTRA_OWNER_ID, ownerId)
                .putExtra(EXTRA_IS_HOST, isHost)
                .putExtra(EXTRA_MODE, modeWire)
            context.startForegroundService(intent)
        }

        /** stopService never throws, unlike startService from the background. */
        fun stop(context: Context) {
            context.stopService(Intent(context, FocusSessionService::class.java))
        }
    }
}
