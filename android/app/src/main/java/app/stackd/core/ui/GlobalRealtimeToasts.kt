package app.stackd.core.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    LaunchedEffect(host) {
        CeremonyGate.showing.collect { if (it) host.currentSnackbarData?.dismiss() }
    }
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
                // The ceremony already shows this session's own results; a toast on
                // top of it repeats them under a different name.
                // The event can land a moment before the ceremony opens, so own
                // results wait briefly and re-check.
                if (action.record["kind"]?.jsonPrimitive?.content in CeremonyKinds) {
                    kotlinx.coroutines.delay(2500)
                    if (CeremonyGate.showing.value) return@collect
                }
                val message = messageFor(action.record) ?: return@collect
                app.stackd.core.feedback.Sfx.play(
                    when (action.record["kind"]?.jsonPrimitive?.content) {
                        "friend_add" -> app.stackd.core.feedback.Sfx.Kind.NOTIFY
                        "session_complete" -> app.stackd.core.feedback.Sfx.Kind.SUCCESS
                        else -> app.stackd.core.feedback.Sfx.Kind.ACHIEVEMENT
                    },
                )
                vibrate(context)
                host.showSnackbar(message)
            }
        } finally {
            // This block runs because the effect was cancelled (sign-out, user
            // switch). A plain suspend call here throws at its first suspension
            // point and the channel stayed joined; NonCancellable lets the removal
            // actually complete.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                runCatching { client.realtime.removeChannel(channel) }
            }
        }
    }
}

/** Maps an activity_events row to a display line, or null for kinds we ignore. */
private fun messageFor(record: JsonObject): String? {
    val kind = record["kind"]?.jsonPrimitive?.content ?: return null
    val payload = record["payload"] as? JsonObject
    fun p(key: String): String? = payload?.get(key)?.jsonPrimitive?.content
    return when (kind) {
        "achievement_unlock" -> "Achievement unlocked" + ((p("name") ?: p("id")?.let { app.stackd.core.humanizeKey(it) })?.let { " · $it" } ?: "")
        "challenge_complete" ->
            "Challenge complete" +
                (p("name")?.let { " · $it" } ?: "") +
                (p("xp")?.let { " · +$it XP" } ?: "")
        "friend_add" -> "New connection"
        "session_complete" -> "Session complete" + (p("xp")?.let { " · +$it XP" } ?: "")
        else -> null
    }
}

/** True while a post-session ceremony is on screen. */
object CeremonyGate {
    val showing = kotlinx.coroutines.flow.MutableStateFlow(false)
}

private val CeremonyKinds = setOf("achievement_unlock", "challenge_complete", "session_complete")

/** App snackbar on the Stack'd surface tokens (Material's default is a pale inverse surface). */
@Composable
fun StackdSnackbarHost(hostState: SnackbarHostState, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    val colors = app.stackd.core.theme.Stackd.colors
    androidx.compose.material3.SnackbarHost(hostState, modifier) { data ->
        androidx.compose.material3.Snackbar(
            snackbarData = data,
            modifier = androidx.compose.ui.Modifier
                .padding(horizontal = 16.dp)
                .border(1.dp, colors.border, app.stackd.core.theme.RadiusLg),
            shape = app.stackd.core.theme.RadiusLg,
            containerColor = colors.surfaceRaised,
            contentColor = colors.textPrimary,
            actionColor = colors.accent,
            dismissActionContentColor = colors.textMuted,
        )
    }
}

internal fun vibrate(context: Context, ms: Long = 20) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
}
