package app.stackd.core

/**
 * Time formatting ported 1:1 from the web's `src/lib/room.ts`. Kept identical so
 * a duration reads the same on the dashboard, the room screen, and the results
 * card as it does on the web — these strings are what users compare across
 * devices.
 */

/** `HH:MM:SS` past an hour, `MM:SS` under. Negative clamps to zero. */
fun formatDuration(totalSeconds: Int): String {
    val t = totalSeconds.coerceAtLeast(0)
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) {
        "%02d:%02d:%02d".format(h, m, s)
    } else {
        "%02d:%02d".format(m, s)
    }
}

/** "45m", "1h", "2h 10m": whole hours drop the "0m" ("1h 0m" read as a glitch). */
fun formatMinutes(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

/** One decimal under 10 hours, whole hours at or above — matches `formatHours`. */
fun formatHours(totalSeconds: Int): String {
    val h = totalSeconds / 3600.0
    return if (h >= 10) "${Math.round(h)}h" else "%.1fh".format(h)
}

/**
 * Supabase timestamps to epoch millis. Returns null on a malformed string
 * rather than throwing — a timer that can't parse its start simply shows no
 * elapsed time, which is a far better failure than a crashed screen.
 * minSdk 26, so `java.time` is available without desugaring.
 */
fun parseIsoMillis(iso: String?): Long? =
    iso?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }


/**
 * A server key as display text: "no_breach" -> "No Breach". Activity payloads
 * carry achievement/challenge ids, which were leaking into the feed verbatim.
 */
fun humanizeKey(key: String): String =
    key.split('_', '-').filter { it.isNotBlank() }
        .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
