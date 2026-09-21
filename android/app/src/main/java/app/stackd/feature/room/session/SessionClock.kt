package app.stackd.feature.room.session

/**
 * Derives session timing from the server's `started_at` rather than from a
 * local tick.
 *
 * A ticking counter is only ever an approximation here: the OS throttles timers
 * in the background, and a session that spends ten minutes there would come
 * back short. Every value below is recomputed from wall-clock time against the
 * server's start, so resuming reconciles automatically instead of accumulating
 * whatever the tick missed.
 */
object SessionClock {

    /** Seconds since the session started, floored at zero. */
    fun elapsedSeconds(startedAtMillis: Long, nowMillis: Long): Long {
        if (startedAtMillis <= 0L) return 0
        return ((nowMillis - startedAtMillis).coerceAtLeast(0L)) / 1000
    }

    /** Seconds left before the session is due to end, floored at zero. */
    fun remainingSeconds(
        startedAtMillis: Long,
        targetDurationSeconds: Long,
        nowMillis: Long,
    ): Long {
        if (startedAtMillis <= 0L) return targetDurationSeconds.coerceAtLeast(0L)
        val elapsed = elapsedSeconds(startedAtMillis, nowMillis)
        return (targetDurationSeconds - elapsed).coerceAtLeast(0L)
    }

    /** Wall-clock instant the session is due to end — what the notification counts down to. */
    fun endsAtMillis(startedAtMillis: Long, targetDurationSeconds: Long): Long =
        startedAtMillis + targetDurationSeconds * 1000

    fun isExpired(
        startedAtMillis: Long,
        targetDurationSeconds: Long,
        nowMillis: Long,
    ): Boolean =
        startedAtMillis > 0L &&
            remainingSeconds(startedAtMillis, targetDurationSeconds, nowMillis) == 0L

    /** Fraction of the session completed, 0f..1f, for progress rings. */
    fun progress(
        startedAtMillis: Long,
        targetDurationSeconds: Long,
        nowMillis: Long,
    ): Float {
        if (startedAtMillis <= 0L || targetDurationSeconds <= 0L) return 0f
        val elapsed = elapsedSeconds(startedAtMillis, nowMillis).toFloat()
        return (elapsed / targetDurationSeconds).coerceIn(0f, 1f)
    }

    /** `MM:SS`, or `H:MM:SS` once a session runs past an hour. */
    fun format(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val seconds = safe % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }
}
