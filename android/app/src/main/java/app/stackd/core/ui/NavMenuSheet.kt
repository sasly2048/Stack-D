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
    "Atlas" -> app.stackd.core.ui.StackdIcons.AutoAwesome
    "Challenges" -> app.stackd.core.ui.StackdIcons.Flag
    "Seasons" -> app.stackd.core.ui.StackdIcons.CalendarMonth
    "Timeline" -> app.stackd.core.ui.StackdIcons.History
    "Replay" -> app.stackd.core.ui.StackdIcons.Replay
    "Achievements" -> app.stackd.core.ui.StackdIcons.EmojiEvents
    "Leaderboard" -> app.stackd.core.ui.StackdIcons.Leaderboard
    "Focus DNA" -> app.stackd.core.ui.StackdIcons.Fingerprint
    "Wrapped" -> app.stackd.core.ui.StackdIcons.Bolt
    "Friends" -> app.stackd.core.ui.StackdIcons.PersonAdd
    "Circles" -> app.stackd.core.ui.StackdIcons.Groups
    "Groups" -> app.stackd.core.ui.StackdIcons.AccountTree
    "Partners" -> app.stackd.core.ui.StackdIcons.Handshake
    "Memory Vault" -> app.stackd.core.ui.StackdIcons.Inventory2
    "Time Capsule" -> app.stackd.core.ui.StackdIcons.HourglassTop
    "Premium" -> app.stackd.core.ui.StackdIcons.WorkspacePremium
    "Trust & Safety" -> app.stackd.core.ui.StackdIcons.Shield
    "Integrations" -> app.stackd.core.ui.StackdIcons.Extension
    else -> if (label.contains("CSV", ignoreCase = true) || label.contains("Export", ignoreCase = true)) {
        app.stackd.core.ui.StackdIcons.FileDownload
    } else if (label.contains("Premium", ignoreCase = true)) {
        app.stackd.core.ui.StackdIcons.Diamond
    } else {
        app.stackd.core.ui.StackdIcons.MoreHoriz
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

    // Open fully: 18 tiles never fit the half-height peek, which cut the grid
    // mid-row and made Back collapse instead of close.
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.surface) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
            ),
        ) {
            var n = 0
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
                    val order = n++
                    item(key = label) {
                        Tile(label, iconFor(label), Modifier.reveal(order)) {
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
private fun Tile(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = Stackd.colors
    val source = remember { MutableInteractionSource() }
    Column(
        modifier
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
