package app.stackd.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.RadiusMd
import app.stackd.core.theme.Stackd

/** Groups in the order people look for them; unknown labels fall into "More". */
private val GROUPS: List<Pair<String, List<String>>> = listOf(
    "Focus" to listOf("Atlas", "Challenges", "Seasons", "Timeline", "Replay"),
    "Progress" to listOf("Achievements", "Leaderboard", "Focus DNA", "Wrapped"),
    "Social" to listOf("Friends", "Circles", "Groups", "Partners"),
    "Vault" to listOf("Memory Vault", "Time Capsule"),
    "Account" to listOf("Premium", "Trust & Safety", "Integrations"),
)

/** Already one tap away in the tab bar — listing them again is noise. */
private val ON_TAB_BAR = setOf("Feed", "Insights", "Profile")

private fun iconFor(label: String): ImageVector = when (label) {
    "Atlas" -> Icons.Outlined.AutoAwesome
    "Challenges" -> Icons.Outlined.Flag
    "Seasons" -> Icons.Outlined.CalendarMonth
    "Timeline" -> Icons.Outlined.History
    "Replay" -> Icons.Outlined.Replay
    "Achievements" -> Icons.Outlined.EmojiEvents
    "Leaderboard" -> Icons.Outlined.Leaderboard
    "Focus DNA" -> Icons.Outlined.Fingerprint
    "Wrapped" -> Icons.Outlined.Bolt
    "Friends" -> Icons.Outlined.PersonAdd
    "Circles" -> Icons.Outlined.Groups
    "Groups" -> Icons.Outlined.AccountTree
    "Partners" -> Icons.Outlined.Handshake
    "Memory Vault" -> Icons.Outlined.Inventory2
    "Time Capsule" -> Icons.Outlined.HourglassTop
    "Premium" -> Icons.Outlined.WorkspacePremium
    "Trust & Safety" -> Icons.Outlined.Shield
    "Integrations" -> Icons.Outlined.Extension
    else -> if (label.contains("CSV", ignoreCase = true) || label.contains("Export", ignoreCase = true)) {
        Icons.Outlined.FileDownload
    } else if (label.contains("Premium", ignoreCase = true)) {
        Icons.Outlined.Diamond
    } else {
        Icons.Outlined.MoreHoriz
    }
}

/**
 * "More" — everything that isn't on the tab bar, as a grouped icon grid.
 * Icons + groups are recognised at a glance; the old 21-row text list had to
 * be read top to bottom. Picking a tile dismisses the sheet before navigating
 * so Back never returns to a stale open sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavMenuSheet(
    onDismiss: () -> Unit,
    entries: List<Pair<String, () -> Unit>>,
) {
    val colors = Stackd.colors
    val byLabel = entries.filter { it.first !in ON_TAB_BAR }.toMap()
    val known = GROUPS.flatMap { it.second }.toSet()
    val sections = GROUPS.map { (title, labels) -> title to labels.filter { it in byLabel } }
        .plus("More" to byLabel.keys.filter { it !in known })
        .filter { it.second.isNotEmpty() }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
            ),
        ) {
            sections.forEach { (title, labels) ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "h-$title") {
                    Text(
                        title.uppercase(),
                        style = MonoLabelSmall,
                        color = colors.textMuted,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
                    )
                }
                labels.forEach { label ->
                    item(key = label) {
                        Tile(label, iconFor(label)) {
                            onDismiss()
                            byLabel[label]?.invoke()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(label: String, icon: ImageVector, onClick: () -> Unit) {
    val colors = Stackd.colors
    val source = remember { MutableInteractionSource() }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 92.dp)
            .pressFeedback(source, pressedScale = 0.95f)
            .clip(RadiusMd)
            .background(colors.textPrimary.copy(alpha = 0.04f))
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(40.dp).clip(RadiusMd).background(colors.accent.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}
