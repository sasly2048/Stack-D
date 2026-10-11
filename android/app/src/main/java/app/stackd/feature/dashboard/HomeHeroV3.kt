package app.stackd.feature.dashboard

import app.stackd.core.ui.breathing
import app.stackd.core.ui.sessionShared

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

    val streakLine = when {
        // Evening, alive, nothing clean today: say what's at stake.
        state.streakAtRisk && now.hour >= 17 -> "${state.streak}-day streak ends at midnight"
        state.streak > 0 && state.streakFreezes > 0 -> "${state.streak}-day streak · 1 freeze"
        state.streak > 0 -> "${state.streak}-day streak"
        else -> "Day one"
    }
    val ringPct by androidx.compose.animation.core.animateFloatAsState(
        pct, androidx.compose.animation.core.tween(1200, easing = app.stackd.core.theme.EaseRitual), label = "today",
    )
    Column(Modifier.fillMaxWidth()) {
        // A launcher, not a letter: one short greeting line, then today.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "$clock · ${today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "$dayPart, $first.",
                    style = MaterialTheme.typography.headlineLarge.copy(fontFamily = SerifFamily, fontWeight = FontWeight.Normal),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            app.stackd.core.ui.MenuButton(onClick = onMore)
        }
        Spacer(Modifier.height(20.dp))
        // Centrepiece: today's goal as a ring around the stack.
        Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(250.dp).background(
                    androidx.compose.ui.graphics.Brush.radialGradient(listOf(colors.accent.copy(alpha = 0.16f), androidx.compose.ui.graphics.Color.Transparent)),
                ),
            )
            androidx.compose.foundation.Canvas(Modifier.size(230.dp)) {
                val stroke = 5.dp.toPx()
                val inset = stroke / 2
                val arc = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                drawArc(
                    colors.textPrimary.copy(alpha = 0.08f), -90f, 360f, false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arc,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                )
                drawArc(
                    colors.accent, -90f, 360f * ringPct, false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arc,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
            // Real count: you, plus friends stacking right now.
            Box(Modifier.sessionShared("stack").breathing(enabled = true)) {
                StackArt(size = 150.dp, phones = (1 + state.friendsFocusing.size).coerceAtMost(6))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                "$todayMin",
                style = MaterialTheme.typography.displayMedium.copy(fontFamily = SerifFamily, fontWeight = FontWeight.Normal),
                color = colors.textPrimary,
            )
            Text(
                " of $DAILY_GOAL_MIN min today",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textMuted,
                modifier = Modifier.padding(bottom = 8.dp).weight(1f),
            )
            Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    app.stackd.core.ui.StackdIcons.LocalFireDepartment,
                    contentDescription = null,
                    tint = when {
                        state.streakAtRisk && now.hour >= 17 -> colors.breach
                        state.streak > 0 -> colors.accent
                        else -> colors.accent.copy(alpha = 0.45f)
                    },
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    streakLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.streakAtRisk && now.hour >= 17) colors.breach else colors.textMuted,
                )
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
