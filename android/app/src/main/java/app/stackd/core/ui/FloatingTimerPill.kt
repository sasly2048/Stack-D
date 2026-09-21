package app.stackd.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.Stackd
import app.stackd.feature.room.session.FocusSessionService
import kotlinx.coroutines.delay

/**
 * The web's floating-timer pill: while a session runs and you're anywhere but
 * the room screen, a small countdown chip floats at the bottom; tapping it
 * returns to the room. Driven by [FocusSessionService.running], the same state
 * that backs the foreground notification, so the two can never disagree.
 */
@Composable
fun FloatingTimerPill(
    isOnRoomScreen: Boolean,
    onOpenRoom: (code: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val running by FocusSessionService.running.collectAsStateWithLifecycle()
    val session = running ?: return
    if (isOnRoomScreen || session.roomCode.isBlank()) return

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val remaining = ((session.endsAtMillis - now) / 1000).coerceAtLeast(0)
    val colors = Stackd.colors
    Text(
        "● ${session.roomCode} · %d:%02d".format(remaining / 60, remaining % 60),
        style = MonoLabelSmall,
        color = colors.textPrimary,
        modifier = modifier
            .background(colors.background.copy(alpha = 0.92f), CircleShape)
            .border(1.dp, colors.accent.copy(alpha = 0.6f), CircleShape)
            .clickable { onOpenRoom(session.roomCode) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
