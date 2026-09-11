package app.stackd.core.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * App-scope realtime notifier: subscribes to the signed-in user's
 * `activity_events` stream and raises a short snackbar for socially meaningful
 * events, with a haptic pulse. The Android counterpart to the web's
 * root-mounted `GlobalRealtimeToasts`.
 *
 * Driven by a [SnackbarHostState] the caller also hands to a `SnackbarHost`, so
 * the message renders wherever the app scaffold places its host. Keyed on
 * [userId]: it re-subscribes when the account changes and cancels the channel
 * on sign-out, mirroring the web effect's cleanup.
 */
@Composable
fun GlobalRealtimeToasts(
    client: SupabaseClient,
    userId: String?,
    host: SnackbarHostState,
) {
    val context = LocalContext.current
    LaunchedEffect(userId) {
        val uid = userId ?: return@LaunchedEffect
        val channel = client.realtime.channel("activity:$uid")
        val flow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "activity_events"
            filter("user_id", FilterOperator.EQ, uid)
        }
        runCatching { channel.subscribe() }
        try {
            flow.collect { action ->
                val message = messageFor(action.record) ?: return@collect
                vibrate(context)
                host.showSnackbar(message)
            }
        } finally {
            runCatching { channel.unsubscribe() }
        }
    }
}

/** Maps an activity_events row to a display line, or null for kinds we ignore. */
private fun messageFor(record: JsonObject): String? {
    val kind = record["kind"]?.jsonPrimitive?.content ?: return null
    val payload = record["payload"] as? JsonObject
    fun p(key: String): String? = payload?.get(key)?.jsonPrimitive?.content
    return when (kind) {
        "achievement_unlock" -> "🏅 Achievement unlocked" + (p("id")?.let { " · $it" } ?: "")
        "challenge_complete" ->
            "🎯 Challenge complete" +
                (p("name")?.let { " · $it" } ?: "") +
                (p("xp")?.let { " · +$it XP" } ?: "")
        "friend_add" -> "🤝 New connection"
        "session_complete" -> "✅ Session complete" + (p("xp")?.let { " · +$it XP" } ?: "")
        else -> null
    }
}

private fun vibrate(context: Context, ms: Long = 20) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
}
