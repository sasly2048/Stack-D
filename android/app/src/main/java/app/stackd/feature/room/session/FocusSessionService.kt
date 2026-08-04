package app.stackd.feature.room.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.stackd.MainActivity
import app.stackd.R

/**
 * Keeps a live session visible while the app is not on screen.
 *
 * The notification counts down on its own: [NotificationCompat.setWhen] anchors
 * it to the session's real end time and `setUsesChronometer` lets the system
 * render the ticking text. That means no per-second wakeups from us, and the
 * countdown stays anchored to wall-clock time instead of drifting with a
 * handler the OS is free to throttle.
 *
 * Showing the time is all this does. It deliberately does not suppress breach
 * detection — leaving the app during a session still breaks the stack, exactly
 * as it does on the web.
 */
class FocusSessionService : Service() {

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

        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification(roomCode, endsAtMillis))

        // Don't recreate on kill: a session resurrected without its room state
        // would show a countdown for something the user is no longer in.
        return START_NOT_STICKY
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

    companion object {
        private const val CHANNEL_ID = "focus_session"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "app.stackd.action.STOP_SESSION"
        private const val EXTRA_ENDS_AT = "ends_at"
        private const val EXTRA_ROOM_CODE = "room_code"

        /** @param endsAtMillis wall-clock epoch time the session is due to end. */
        fun start(context: Context, roomCode: String, endsAtMillis: Long) {
            val intent = Intent(context, FocusSessionService::class.java)
                .putExtra(EXTRA_ENDS_AT, endsAtMillis)
                .putExtra(EXTRA_ROOM_CODE, roomCode)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, FocusSessionService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
