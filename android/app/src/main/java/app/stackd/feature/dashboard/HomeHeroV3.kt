package app.stackd.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stackd.core.theme.SerifFamily
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.StackArt
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Home hero: a full-bleed scene instead of
 * a boxed card. The brand stack art sits large and cropped off the right edge
 * as a backdrop; an asymmetric, left-aligned serif greeting reads over it; the
 * goal ring becomes one calm line with a thin meter; one primary action.
 */
@Composable
internal fun HomeHeroV3(state: DashboardUiState, onStart: () -> Unit, onMore: () -> Unit) {
    val colors = Stackd.colors
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zone)
    val todayMin = remember(state.history, today) { focusSecondsOn(state.history, today, zone) / 60 }
    val now by androidx.compose.runtime.produceState(LocalTime.now()) {
        while (true) {
            kotlinx.coroutines.delay(15_000)
            value = LocalTime.now()
        }
    }
    val hour = now.hour
    val dayPart = when {
        hour < 5 -> "Late night"
        hour < 12 -> "Morning"
        hour < 17 -> "Afternoon"
        hour < 21 -> "Evening"
        else -> "Night"
    }
    val clock = now.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
    val first = state.name.substringBefore(' ').ifBlank { state.name }
    val pct = (todayMin.toFloat() / DAILY_GOAL_MIN).coerceIn(0f, 1f)

    Box(
        Modifier
            .fillMaxWidth()
            .bleed(20.dp)
            .height(330.dp),
    ) {
        // Backdrop: the stack, big and cropped by the screen edge.
        StackArt(
            // ~15% smaller than the first cut so the greeting and today's state lead.
            size = 200.dp,
            phones = 4,
            // Whole stack visible beside the greeting (no edge crop), below the menu button.
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 28.dp, y = 122.dp),
        )
        app.stackd.core.ui.MenuButton(onClick = onMore, modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp))
        Column(
            Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("$dayPart · $clock", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                Spacer(Modifier.height(14.dp))
                Text(
                    "$dayPart,\n$first.",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = SerifFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 52.sp,
                        lineHeight = 56.sp,
                    ),
                    color = colors.textPrimary,
                )
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$todayMin of $DAILY_GOAL_MIN min today",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        app.stackd.core.ui.StackdIcons.LocalFireDepartment,
                        contentDescription = null,
                        tint = if (state.streak > 0) colors.accent else colors.accent.copy(alpha = 0.45f),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (state.streak > 0) "${state.streak}-day streak" else "Day one",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textMuted,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                        .background(colors.textPrimary.copy(alpha = 0.08f)),
                ) {
                    Box(Modifier.fillMaxWidth(pct).height(4.dp).clip(CircleShape).background(colors.accent))
                }
            }
        }
    }
}

/** Escape the parent's horizontal padding so a scene can run edge to edge. */
private fun Modifier.bleed(by: androidx.compose.ui.unit.Dp) = layout { m, c ->
    val e = by.roundToPx()
    val w = c.maxWidth + 2 * e
    val p = m.measure(c.copy(minWidth = w, maxWidth = w))
    layout(c.maxWidth, p.height) { p.place(-e, 0) }
}
