package app.stackd.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.stackd.core.theme.TechLabel
import app.stackd.core.theme.Stackd

/**
 * Sibling screens merged under one Explore entry ("People", "Your story"): the
 * header draws them as a tab row under the title so switching is one tap.
 */
class HubTabs(val labels: List<String>, val selected: Int, val onSelect: (Int) -> Unit)

val LocalHubTabs = androidx.compose.runtime.compositionLocalOf<HubTabs?> { null }

/**
 * Top-of-screen breadcrumb + back affordance shared by every sub-screen.
 *
 * Back sits top-left in a 48dp target (where both Android and iOS users reach
 * for it); screens used to end with a BACK button you had to scroll to. The
 * chevron is pulled left by its inner padding so its stroke lines up with the
 * content edge instead of indenting the whole header.
 */
@Composable
fun ScreenHeader(
    path: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** Large serif page title under the breadcrumb (web pages' ont-serif h1). */
    title: String? = null,
    /** Callers with a [trailing] slot pass false so the row always renders. */
    hideEmptyRow: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = Stackd.colors
    // A tab's root has nowhere to go back to — the tab bar is the navigation.
    val back = if (LocalIsTabRoot.current) null else onBack
    // Fixed 48dp row: the room screen shows/hides back by phase, and a header
    // that changed height made the content below jump.
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth()) {
    // "STACK'D / LEADERBOARD" above a "Leaderboard" title said the same thing
    // twice in system-path voice: drop the brand prefix, and the whole crumb
    // when a title follows. Room codes stay; they're real, shareable data.
    val label = when {
        path.startsWith("ROOM") -> path
        title != null -> ""
        else -> path.substringAfter("STACK'D / ")
    }
    // A tab root with no crumb has nothing for this row to hold; skipping it
    // lets the title start at the same height as Home's greeting.
    if (back != null || label.isNotEmpty() || !hideEmptyRow) {    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) {
            Box(
                Modifier
                    .offset(x = (-20).dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = back)
                    .semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(20.dp)) {
                    val w = size.width
                    val chevron = Path().apply {
                        moveTo(w * 0.62f, w * 0.18f)
                        lineTo(w * 0.30f, w * 0.50f)
                        lineTo(w * 0.62f, w * 0.82f)
                    }
                    drawPath(
                        chevron,
                        colors.textPrimary,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
        }

        Text(
            label,
            style = TechLabel,
            color = colors.textMuted,
            // Pulled over the 48dp target's padding: chevron stroke on the content
            // edge, ~10dp to the label.
            modifier = Modifier
                .weight(1f)
                .offset(x = if (back != null) (-32).dp else 0.dp),
        )
        trailing()
    }
    }
    // Large title: one clear 'you are here' per screen, in the web's serif, so a
    // page reads as a destination rather than a stack of small labels.
    if (title != null) {
        androidx.compose.foundation.layout.Spacer(Modifier.heightIn(min = 6.dp))
        Text(
            title,
            style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
            fontFamily = app.stackd.core.theme.SerifFamily,
            color = colors.textPrimary,
            maxLines = 2,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.heightIn(min = 4.dp))
    }
    LocalHubTabs.current?.let { hub -> HubTabRow(hub) }
    }
}

@Composable
private fun HubTabRow(hub: HubTabs) {
    val colors = Stackd.colors
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(top = 12.dp, bottom = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        hub.labels.forEachIndexed { i, label ->
            val on = i == hub.selected
            Text(
                label,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                color = if (on) colors.background else colors.textPrimary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (on) colors.textPrimary else colors.textPrimary.copy(alpha = 0.06f))
                    .clickable(role = Role.Tab) { if (!on) hub.onSelect(i) }
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

