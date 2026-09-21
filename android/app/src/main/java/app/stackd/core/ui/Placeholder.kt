package app.stackd.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.stackd.core.theme.MonoLabel
import app.stackd.core.theme.Stackd

/**
 * Stand-in for a screen that exists in the navigation graph but hasn't been
 * built yet. Phase 0 ships the whole route table this way so navigation and
 * information architecture can be walked end to end before any screen is real.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    note: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = Stackd.colors
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PulseDot(animated = false)
        Text(
            text = title.uppercase(),
            style = MonoLabel,
            color = colors.accent,
            textAlign = TextAlign.Center,
        )
        Text(
            text = note ?: "Not built yet.",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}
