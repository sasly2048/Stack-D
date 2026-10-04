package app.stackd.feature.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
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

/** Focus minutes for each day (Mon..Sun) of [today]'s week. */
internal fun weekMinutes(history: List<FocusHistoryRow>, today: LocalDate, zone: ZoneId): List<Int> {
    val monday = today.minusDays(today.dayOfWeek.value - 1L)
    val byDay = history.groupBy(
        { row -> parseIsoMillis(row.createdAt)?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } },
        { it.durationSeconds / 60 },
    )
    return (0..6).map { byDay[monday.plusDays(it.toLong())]?.sum() ?: 0 }
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
    val minutes = remember(history, today) { weekMinutes(history, today, zone) }
    val total = minutes.sum()
    val max = maxOf(30, minutes.max())
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(700)) }
    // Scrub (Revolut-style): touch/drag across the bars to read a day.
    var picked by remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    val view = androidx.compose.ui.platform.LocalView.current
    fun fmt(m: Int) = app.stackd.core.formatMinutes(m)
    val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    Row(verticalAlignment = Alignment.Bottom) {
        androidx.compose.animation.AnimatedContent(
            if (picked >= 0) minutes[picked] else total,
            transitionSpec = {
                androidx.compose.animation.fadeIn(tween(140)) togetherWith androidx.compose.animation.fadeOut(tween(90))
            },
            label = "weekValue",
        ) { v ->
            Text(
                fmt(v),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = SerifFamily),
                color = colors.textPrimary,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                picked >= 0 -> if (picked == todayIdx) "today" else dayNames[picked].lowercase().let { "on $it" }
                total == 0 -> "A fresh week. Make today count."
                else -> "focused this week"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (picked >= 0) colors.accent else colors.textMuted,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
    Spacer(Modifier.height(14.dp))
    val accent = colors.accent
    val track = colors.textPrimary.copy(alpha = 0.05f)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .pointerInput(minutes) {
                fun indexAt(x: Float) = ((x / size.width) * 7).toInt().coerceIn(0, 6)
                fun pick(i: Int) {
                    if (i != picked) {
                        picked = i
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        app.stackd.core.feedback.Sfx.play(app.stackd.core.feedback.Sfx.Kind.TAP)
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pick(indexAt(down.position.x))
                    do {
                        val event = awaitPointerEvent()
                        val c = event.changes.first()
                        if (c.pressed) {
                            pick(indexAt(c.position.x))
                            // Horizontal scrub owns the gesture; vertical still scrolls the page.
                            if (kotlin.math.abs(c.position.x - c.previousPosition.x) > kotlin.math.abs(c.position.y - c.previousPosition.y)) c.consume()
                        }
                    } while (event.changes.any { it.pressed })
                    picked = -1
                }
            },
    ) {
        val gap = 10.dp.toPx()
        val w = (size.width - gap * 6) / 7
        val r = CornerRadius(6.dp.toPx())
        minutes.forEachIndexed { i, m ->
            val x = i * (w + gap)
            val on = picked == i
            drawRoundRect(if (on) accent.copy(alpha = 0.12f) else track, Offset(x, 0f), Size(w, size.height), r)
            if (m > 0) {
                val h = size.height * (m.toFloat() / max).coerceIn(0.08f, 1f) * grow.value
                val a = when {
                    picked >= 0 -> if (on) 1f else 0.3f
                    i == todayIdx -> 1f
                    else -> 0.55f
                }
                drawRoundRect(accent.copy(alpha = a), Offset(x, size.height - h), Size(w, h), r)
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth()) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, d ->
            Text(
                d,
                style = MaterialTheme.typography.bodySmall,
                color = if (i == todayIdx) colors.accent else colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
