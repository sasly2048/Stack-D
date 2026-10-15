package app.stackd.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.GhostButton

/**
 * Shared empty state for the feature screens: muted icon, short title, one
 * helpful line and an optional next step. A bare "No X yet." reads like a
 * failure; a composed empty state reads like an invitation.
 */
@Composable
internal fun FeatureEmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = Stackd.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Brand illustration instead of a lone grey glyph: empty is a moment,
        // not an error.
        app.stackd.core.ui.EmptyIllustration(icon)
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = app.stackd.core.theme.SerifFamily,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            GhostButton(text = actionText, onClick = onAction)
        }
    }
}

/** Opens the Android share sheet with a plain-text invite to Stack'd. */
internal fun shareStackdInvite(context: android.content.Context) {
    val url = app.stackd.BuildConfig.WEB_BASE_URL
    val text = if (url.isBlank()) "Stack with me on Stack'd." else "Stack with me on Stack'd — $url"
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(
            android.content.Intent.createChooser(send, "Invite a friend")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
