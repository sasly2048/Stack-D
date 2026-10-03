package app.stackd.core.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stackd.core.theme.SerifFamily
import app.stackd.core.theme.Stackd
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search

/** Groups in the order people look for them; unknown labels fall into "More". */
private val GROUPS: List<Pair<String, List<String>>> = listOf(
    "Focus" to listOf("Atlas", "Challenges", "Seasons", "Timeline", "Replay"),
    "Progress" to listOf("Achievements", "Leaderboard", "Focus DNA", "Wrapped"),
    "Social" to listOf("Friends", "Circles", "Groups", "Partners"),
    "Vault" to listOf("Memory Vault", "Time Capsule"),
    "Account" to listOf("Premium", "Trust & Safety", "Integrations"),
)

/** Hero cards at the top; falls back to the first two entries if these are absent. */
private val FEATURED = listOf("Atlas", "Leaderboard")

/** Already one tap away in the tab bar — listing them again is noise. */
private val ON_TAB_BAR = setOf("Feed", "Insights", "Profile")

private fun iconFor(label: String): ImageVector = when (label) {
    "Atlas" -> StackdIcons.Atlas
    "Challenges" -> StackdIcons.Target
    "Seasons" -> StackdIcons.CalendarRange
    "Timeline" -> StackdIcons.History
    "Replay" -> StackdIcons.Rewind
    "Achievements" -> StackdIcons.EmojiEvents
    "Leaderboard" -> StackdIcons.Leaderboard
    "Focus DNA" -> StackdIcons.Dna
    "Wrapped" -> StackdIcons.AutoAwesome
    "Friends" -> StackdIcons.UserRoundPlus
    "Circles" -> StackdIcons.Orbit
    "Groups" -> StackdIcons.UsersRound
    "Partners" -> StackdIcons.Handshake
    "Memory Vault" -> StackdIcons.Vault
    "Time Capsule" -> StackdIcons.HourglassTop
    "Premium" -> StackdIcons.WorkspacePremium
    "Trust & Safety" -> StackdIcons.VerifiedUser
    "Integrations" -> StackdIcons.Blocks
    else -> when {
        isExport(label) -> StackdIcons.FileDownload
        label.contains("Premium", ignoreCase = true) -> StackdIcons.Diamond
        else -> StackdIcons.MoreHoriz
    }
}

private fun isExport(label: String) =
    label.contains("CSV", ignoreCase = true) || label.contains("Export", ignoreCase = true)

private fun descriptionFor(label: String): String? = when (label) {
    "Atlas" -> "Your AI focus coach"
    "Challenges" -> "Weekly goals worth XP"
    "Seasons" -> "Compete each season"
    "Timeline" -> "Every session you've held"
    "Replay" -> "Relive a session"
    "Achievements" -> "Badges you've collected"
    "Leaderboard" -> "See where you stand"
    "Focus DNA" -> "What kind of focuser you are"
    "Wrapped" -> "Your period in review"
    "Friends" -> "Find and add people"
    "Circles" -> "Small crews that stack weekly"
    "Groups" -> "Bigger communities"
    "Partners" -> "One-to-one accountability"
    "Memory Vault" -> "Keep what mattered"
    "Time Capsule" -> "Notes to your future self"
    "Premium" -> "Plans and membership"
    "Trust & Safety" -> "Block, report, privacy"
    "Integrations" -> "Connect your tools"
    else -> if (isExport(label)) "Download your history as CSV" else null
}

/** "Export focus history (CSV)" reads as a title once the description carries the format. */
private fun titleFor(label: String) = if (isExport(label)) "Export history" else label

/**
 * "Explore" — everything beyond the four tabs, as a full-height page over the
 * app: serif header, two featured hero cards, then grouped soft lists with a
 * line of description per row (a grid of bare icons had to be decoded; a row
 * says what's behind it). Picking a row dismisses before navigating so Back
 * never returns to a stale open sheet.
 *
 * [statuses] swaps a row's static description for a live line about the user
 * ("1 of 3 done", "#2 of 10") once it lands; rows without one keep the static text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavMenuSheet(
    onDismiss: () -> Unit,
    entries: List<Pair<String, () -> Unit>>,
    statuses: Map<String, String> = emptyMap(),
) {
    val colors = Stackd.colors
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val byLabel = entries.filter { it.first !in ON_TAB_BAR }.toMap()
    val matches: (String) -> Boolean = { label ->
        q.isEmpty() || listOfNotNull(titleFor(label), descriptionFor(label), statuses[label])
            .any { it.contains(q, ignoreCase = true) }
    }
    // While searching, results are one flat set — the heroes fold back into their groups.
    val featured = if (q.isNotEmpty()) emptyList() else
        FEATURED.filter { it in byLabel }.ifEmpty { byLabel.keys.take(2) }.take(2)
    val known = GROUPS.flatMap { it.second }.toSet()
    val sections = GROUPS.map { (title, labels) -> title to labels.filter { it in byLabel && it !in featured } }
        .plus("More" to byLabel.keys.filter { it !in known && it !in featured })
        .map { (title, labels) -> title to labels.filter(matches) }
        .filter { it.second.isNotEmpty() }

    androidx.compose.runtime.DisposableEffect(Unit) {
        app.stackd.core.feedback.Sfx.play(app.stackd.core.feedback.Sfx.Kind.OPEN)
        onDispose { app.stackd.core.feedback.Sfx.play(app.stackd.core.feedback.Sfx.Kind.CLOSE) }
    }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val go: (String) -> Unit = { label ->
        onDismiss()
        byLabel[label]?.invoke()
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.background,
        shape = RectangleShape,
        // No handle: it sat under the status-bar clock. The close disc does its job.
        dragHandle = null,
        // Insets are applied inside the list so the glow can run under the status bar.
        contentWindowInsets = { WindowInsets(0) },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                // Page-wide ember light pooling from the top edge.
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            listOf(colors.accent.copy(alpha = 0.16f), Color.Transparent),
                            center = Offset(size.width * 0.5f, 0f),
                            radius = size.width * 1.1f,
                        ),
                    )
                },
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top + 12.dp, bottom = bottom + 32.dp),
        ) {
            item(key = "header") {
                Header(onDismiss, Modifier.reveal(0))
            }
            item(key = "search") {
                SearchField(query, { query = it }, Modifier.padding(top = 20.dp).reveal(1))
            }
            if (q.isNotEmpty() && sections.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Nothing matches ‘$q’",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    )
                }
            }
            if (featured.isNotEmpty()) {
                item(key = "featured") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp)
                            .reveal(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        featured.forEach { label ->
                            HeroCard(label, statuses[label], Modifier.weight(1f)) { go(label) }
                        }
                        if (featured.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            sections.forEachIndexed { i, (title, labels) ->
                item(key = "s-$title") {
                    Column(Modifier.padding(top = 28.dp).reveal(i + 3)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textMuted,
                            modifier = Modifier
                                .padding(start = 4.dp, bottom = 10.dp)
                                .semantics { heading() },
                        )
                        Column(Modifier.glassSurface(GroupShape)) {
                            labels.forEachIndexed { j, label ->
                                if (j > 0) {
                                    HorizontalDivider(
                                        // Inset past the icon chip: 16 pad + 40 chip + 14 gap.
                                        modifier = Modifier.padding(start = 70.dp),
                                        thickness = 0.5.dp,
                                        color = Color.White.copy(alpha = 0.06f),
                                    )
                                }
                                MenuRow(label, statuses[label]) { go(label) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val GroupShape = RoundedCornerShape(24.dp)
private val HeroShape = RoundedCornerShape(28.dp)
private val ChipShape = RoundedCornerShape(12.dp)

@Composable
private fun Header(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Stackd.colors
    val source = remember { MutableInteractionSource() }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).padding(top = 4.dp)) {
            Text(
                "Explore",
                fontFamily = SerifFamily,
                fontSize = 40.sp,
                lineHeight = 44.sp,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Everything beyond your four tabs.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
        }
        Box(
            Modifier
                .size(44.dp)
                .pressFeedback(source, pressedScale = 0.9f, sound = null)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClose)
                .semantics { contentDescription = "Close" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(StackdIcons.Close, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = Stackd.colors
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Search Explore" },
        decorationBox = { field ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text("Search", style = MaterialTheme.typography.bodyLarge, color = colors.textMuted)
                    }
                    field()
                }
                if (value.isNotEmpty()) {
                    Icon(
                        StackdIcons.Close,
                        contentDescription = "Clear search",
                        tint = colors.textMuted,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { onChange("") },
                    )
                }
            }
        },
    )
}

/** Static description until a live [status] lands, then a soft crossfade to it. */
@Composable
private fun RowSubtitle(label: String, status: String?, maxLines: Int) {
    val text = status ?: descriptionFor(label) ?: return
    Crossfade(targetState = text, animationSpec = tween(320), label = "menuStatus") { t ->
        Text(
            t,
            style = MaterialTheme.typography.bodySmall,
            color = Stackd.colors.textMuted,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun IconChip(icon: ImageVector, size: Int, iconSize: Int, shape: RoundedCornerShape) {
    val colors = Stackd.colors
    Box(
        Modifier
            .size(size.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(colors.accent.copy(alpha = 0.26f), colors.accent.copy(alpha = 0.10f))),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.accentGlow, modifier = Modifier.size(iconSize.dp))
    }
}

@Composable
private fun HeroCard(label: String, status: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = Stackd.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val lit by animateFloatAsState(if (pressed) 1f else 0f, label = "heroLit")
    Column(
        modifier
            .height(176.dp)
            .pressFeedback(source, pressedScale = 0.96f)
            .clip(HeroShape)
            .background(Color.White.copy(alpha = 0.06f))
            // Ember warmth rising from the bottom corner; brightens under the finger.
            .background(
                Brush.linearGradient(
                    listOf(Color.Transparent, colors.accent.copy(alpha = 0.16f + 0.10f * lit)),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
            )
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        IconChip(iconFor(label), size = 52, iconSize = 26, shape = RoundedCornerShape(16.dp))
        Column {
            Text(
                titleFor(label),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            RowSubtitle(label, status, maxLines = 2)
        }
    }
}

@Composable
private fun MenuRow(label: String, status: String?, onClick: () -> Unit) {
    val colors = Stackd.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val lit by animateFloatAsState(if (pressed) 1f else 0f, label = "rowLit")
    Row(
        Modifier
            .fillMaxWidth()
            .pressFeedback(source, pressedScale = 0.98f)
            .background(Color.White.copy(alpha = 0.05f * lit))
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconChip(iconFor(label), size = 40, iconSize = 20, shape = ChipShape)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 8.dp),
        ) {
            Text(
                titleFor(label),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            RowSubtitle(label, status, maxLines = 1)
        }
        Icon(
            StackdIcons.ChevronRight,
            contentDescription = null,
            tint = colors.textMuted.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp),
        )
    }
}
