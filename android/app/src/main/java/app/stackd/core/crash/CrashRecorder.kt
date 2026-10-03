package app.stackd.core.crash

import android.content.Context

/**
 * Records the last uncaught exception so the next launch can show a calm
 * "Session interrupted" screen (web route-error-boundary equivalent).
 * SharedPreferences with commit() because the process is dying.
 */
object CrashRecorder {
    private const val PREFS = "stackd_crash"
    private const val MAX_AGE_MS = 10 * 60 * 1000L

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val head = e.stackTrace.take(5).joinToString("\n") { "  at $it" }
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putLong("at", System.currentTimeMillis())
                    .putString("msg", "${e.javaClass.simpleName}: ${e.message}\n$head")
                    .commit()
            }
            previous?.uncaughtException(t, e)
        }
    }

    /** Returns the recorded crash message if recent, and clears the record. */
    fun consume(context: Context): String? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = p.getLong("at", 0L)
        val msg = p.getString("msg", null)
        if (at == 0L && msg == null) return null
        p.edit().clear().apply()
        return if (msg != null && System.currentTimeMillis() - at < MAX_AGE_MS) msg else null
    }
}
