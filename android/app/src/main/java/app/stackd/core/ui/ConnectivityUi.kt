package app.stackd.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stackd.core.net.onlineStatus
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.Stackd

/**
 * A fixed top banner shown while the device is offline — the Android counterpart
 * to the web's `OfflineBanner`. Without it, a dropped connection presents as an
 * app that has silently stopped responding; one honest line removes a whole
 * class of "the app is broken" confusion. Uses the breach palette already
 * carrying degraded states, and animates in/out so it doesn't jar.
 */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // remember the flow so it isn't rebuilt (and re-subscribed, re-seeded) on
    // every recomposition — a fresh cold flow each frame churns the network
    // callback and keeps re-emitting the initial online=true.
    val flow = remember(context) { context.onlineStatus() }
    val online by flow.collectAsStateWithLifecycle(initialValue = true)
    val colors = Stackd.colors

    AnimatedVisibility(
        visible = !online,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.breach.copy(alpha = 0.12f))
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "OFFLINE — PROGRESS HELD UNTIL YOU RECONNECT",
                style = MonoLabelSmall,
                color = colors.breach,
            )
        }
    }
}

/**
 * Floating pill showing N session results still waiting to reach the server,
 * with a tap to retry now — the Android counterpart to the web's `QueueBadge`.
 *
 * The count comes straight from the finalize queue's DataStore Flow, so the
 * pill appears the moment a result is parked and clears the moment it drains.
 * WorkManager already retries on its own; this is the *visibility* + manual
 * kick the web surfaces, not new sync logic. Hidden entirely when the queue is
 * empty or no one is signed in.
 */
@Composable
fun QueueBadge(
    ownerId: String?,
    count: Int,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Stackd.colors
    AnimatedVisibility(
        visible = ownerId != null && count > 0,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.surface.copy(alpha = 0.9f))
                .border(1.dp, colors.border, RoundedCornerShape(50))
                .clickable(onClick = onRetry)
                .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colors.accent),
            )
            Text(
                "$count PENDING · RETRY",
                style = MonoLabelSmall,
                color = colors.textMuted,
            )
        }
    }
}
