package app.stackd.core.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import app.stackd.core.feedback.Sfx
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
private data class SprintGroupRow(
    val id: String,
    val name: String = "Circle",
    @SerialName("active_session_id") val sessionId: String? = null,
    @SerialName("active_session_code") val sessionCode: String? = null,
    @SerialName("active_session_expires_at") val expiresAt: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
)

/**
 * Focus Circle sprint invites — web invite-channel subscribeToGroupSprints +
 * the __root toast. Seeds from current rows (catches sprints opened while the
 * app was closed), then follows focus_groups UPDATEs; RLS scopes both to the
 * user's circles. Each live sprint raises one snackbar with a Join action.
 */
@Composable
fun SprintInvites(
    client: SupabaseClient,
    userId: String?,
    host: SnackbarHostState,
    onJoin: (String) -> Unit,
) {
    val join by rememberUpdatedState(onJoin)
    LaunchedEffect(userId) {
        val uid = userId ?: return@LaunchedEffect
        val seen = HashSet<String>()

        fun emit(g: SprintGroupRow) {
            val session = g.sessionId ?: return
            val code = g.sessionCode ?: return
            val exp = g.expiresAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
            if (exp != null && exp.isBefore(Instant.now())) return
            if (g.createdBy == uid) return // the host doesn't invite themself
            if (!seen.add(session)) return
            Sfx.play(Sfx.Kind.NOTIFY)
            launch {
                // Web keeps the toast 30s; Indefinite + timeout gives the same.
                val result = withTimeoutOrNull(30_000) {
                    host.showSnackbar(
                        message = "A circle leader opened a ${g.name} sprint — join if you're ready. · Room $code",
                        actionLabel = "Join",
                        withDismissAction = true,
                        duration = SnackbarDuration.Indefinite,
                    )
                }
                if (result == SnackbarResult.ActionPerformed) join(code)
            }
        }

        runCatching {
            client.postgrest.from("focus_groups")
                .select(
                    Columns.list(
                        "id", "name", "active_session_id", "active_session_code",
                        "active_session_expires_at", "created_by",
                    ),
                )
                .decodeList<SprintGroupRow>()
        }.getOrDefault(emptyList()).forEach(::emit)

        val channel = client.realtime.channel("group-sprints:$uid")
        val flow = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "focus_groups"
        }
        runCatching { channel.subscribe() }
        try {
            flow.collect { action ->
                runCatching { action.decodeRecord<SprintGroupRow>() }.getOrNull()?.let(::emit)
            }
        } finally {
            withContext(NonCancellable) { runCatching { client.realtime.removeChannel(channel) } }
        }
    }
}
