package app.stackd.feature.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.SerifFamily
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.dp
import app.stackd.core.parseIsoMillis
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import app.stackd.core.theme.Stackd
import app.stackd.data.room.FocusHistoryRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * GitHub-style contribution heatmap — web's `animated-heatmap.tsx`. One cell
 * per day, intensity = focused minutes that day, drawn on one Canvas rather
 * than 182 composables. Sized to the available width; 26 weeks like the web.
 */
@Composable
fun ActivityHeatmap(history: List<FocusHistoryRow>, weeks: Int = 26) {
    val colors = Stackd.colors
    val zone = remember { ZoneId.systemDefault() }
    val minutesByDay = remember(history) {
        history.groupBy(
            { row ->
                parseIsoMillis(row.createdAt)?.let {
                    Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
                }
            },
            { it.durationSeconds / 60 },
        ).filterKeys { it != null }.mapValues { (_, v) -> v.sum() }
    }
    val today = remember { LocalDate.now() }
    val days = weeks * 7
    val max = remember(minutesByDay) { maxOf(60, minutesByDay.values.maxOrNull() ?: 0) }
    val accent = colors.accent
    val empty = colors.textPrimary.copy(alpha = 0.04f)

    Text("Last $weeks weeks", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
    Spacer(Modifier.height(10.dp))
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp),
    ) {
        val gapPx = 2.dp.toPx()
        val cell = (size.width - gapPx * (weeks - 1)) / weeks
        val cellH = (size.height - gapPx * 6) / 7
        val start = today.minusDays((days - 1).toLong())
        for (i in 0 until days) {
            val date = start.plusDays(i.toLong())
            val minutes = minutesByDay[date] ?: 0
            val col = i / 7
            val row = i % 7
            val intensity = if (minutes == 0) 0f else (minutes.toFloat() / max).coerceAtMost(1f)
            drawRoundRect(
                color = if (minutes == 0) empty else accent.copy(alpha = 0.25f + intensity * 0.65f),
                topLeft = Offset(col * (cell + gapPx), row * (cellH + gapPx)),
                size = Size(cell, cellH),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }
}

/**
 * Home's at-a-glance week: seven bars, Monday–Sunday, today highlighted.
 * Replaces the 26-week heatmap on Home — a mostly-empty half-year grid reads
 * as "you haven't done anything"; a week is a horizon you can still fill.
 */
@Composable
fun WeekBars(history: List<FocusHistoryRow>) {
    val colors = Stackd.colors
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val todayIdx = today.dayOfWeek.value - 1
    val minutes = remember(history, today) {
        val monday = today.minusDays(todayIdx.toLong())
        val byDay = history.groupBy(
            { row -> parseIsoMillis(row.createdAt)?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } },
            { it.durationSeconds / 60 },
        )
        (0..6).map { byDay[monday.plusDays(it.toLong())]?.sum() ?: 0 }
    }
    val total = minutes.sum()
    val max = maxOf(30, minutes.max())
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(700)) }

    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            if (total >= 60) "${total / 60}h ${total % 60}m" else "${total}m",
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = SerifFamily),
            color = colors.textPrimary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (total == 0) "A fresh week. Make today count." else "focused this week",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
    Spacer(Modifier.height(14.dp))
    val accent = colors.accent
    val track = colors.textPrimary.copy(alpha = 0.05f)
    Canvas(Modifier.fillMaxWidth().height(72.dp)) {
        val gap = 10.dp.toPx()
        val w = (size.width - gap * 6) / 7
        val r = CornerRadius(6.dp.toPx())
        minutes.forEachIndexed { i, m ->
            val x = i * (w + gap)
            drawRoundRect(track, Offset(x, 0f), Size(w, size.height), r)
            if (m > 0) {
                val h = size.height * (m.toFloat() / max).coerceIn(0.08f, 1f) * grow.value
                drawRoundRect(
                    accent.copy(alpha = if (i == todayIdx) 1f else 0.55f),
                    Offset(x, size.height - h), Size(w, h), r,
                )
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth()) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, d ->
            Text(
                d,
                style = MonoLabelSmall,
                color = if (i == todayIdx) colors.accent else colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
