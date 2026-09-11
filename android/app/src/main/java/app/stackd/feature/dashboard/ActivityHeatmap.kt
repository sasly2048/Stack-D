package app.stackd.feature.dashboard

import androidx.compose.foundation.Canvas
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
import app.stackd.core.theme.MonoLabelSmall
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

    Text("LAST ${weeks} WEEKS", style = MonoLabelSmall, color = colors.textMuted)
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
