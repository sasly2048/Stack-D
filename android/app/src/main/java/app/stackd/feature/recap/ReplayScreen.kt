package app.stackd.feature.recap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stackd.core.AppContainer
import app.stackd.core.parseIsoMillis
import app.stackd.core.stackdViewModel
import app.stackd.core.theme.MonoLabel
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.Radius2Xl
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.SectionLabel
import app.stackd.data.recap.ReplayEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class ReplayUiState(
    val date: String = LocalDate.now(ZoneOffset.UTC).toString(),
    val loading: Boolean = false,
    val events: List<ReplayEvent> = emptyList(),
    val playing: Boolean = false,
    /** How many events are revealed; events.size means fully played. */
    val cursor: Int = 0,
) {
    val visible: List<ReplayEvent>
        get() = events.take(if (cursor == 0) events.size else cursor)
    val progress: Float
        get() = if (events.isEmpty()) 0f else cursor.toFloat() / events.size
}

/**
 * Focus Replay — web's `replay.tsx`.
 *
 * The play loop reveals events one at a time on a 900ms tick (the web's
 * interval), driven from [viewModelScope] so leaving the screen cancels it.
 */
class ReplayViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ReplayUiState())
    val state: StateFlow<ReplayUiState> = _state

    private var playLoop: Job? = null

    init {
        load()
    }

    fun load() {
        val userId = container.auth.currentUserId ?: return
        playLoop?.cancel()
        _state.value = _state.value.copy(loading = true, cursor = 0, playing = false)
        viewModelScope.launch {
            val events = runCatching {
                container.recap.getDayReplay(userId, _state.value.date)
            }.getOrDefault(emptyList())
            _state.value = _state.value.copy(loading = false, events = events)
        }
    }

    fun shiftDay(days: Long) {
        val next = LocalDate.parse(_state.value.date).plusDays(days).toString()
        _state.value = _state.value.copy(date = next)
        load()
    }

    fun togglePlay() {
        if (_state.value.playing) {
            playLoop?.cancel()
            _state.value = _state.value.copy(playing = false)
            return
        }
        if (_state.value.events.isEmpty()) return
        // Replaying from the end restarts; otherwise it resumes.
        val start = if (_state.value.cursor >= _state.value.events.size) 0 else _state.value.cursor
        _state.value = _state.value.copy(playing = true, cursor = start)
        playLoop = viewModelScope.launch {
            while (isActive) {
                delay(900)
                val s = _state.value
                if (s.cursor >= s.events.size) {
                    _state.value = s.copy(playing = false)
                    break
                }
                _state.value = s.copy(cursor = s.cursor + 1)
            }
        }
    }
}

@Composable
fun ReplayRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ReplayViewModel = viewModel(factory = stackdViewModel { ReplayViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    ReplayScreen(
        state = state,
        onShiftDay = vm::shiftDay,
        onTogglePlay = vm::togglePlay,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun ReplayScreen(
    state: ReplayUiState,
    onShiftDay: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Stackd.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState()),
    ) {
        ResponsiveColumn {
            Text("STACK'D / REPLAY", style = MonoLabel, color = colors.textMuted)
            Spacer(Modifier.height(16.dp))
            SectionLabel("FOCUS REPLAY")
            Spacer(Modifier.height(8.dp))
            Text(
                "Scrub through any day.",
                style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
            )
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GhostButton(text = "← Prev", onClick = { onShiftDay(-1) })
                Text(
                    prettyDate(state.date),
                    style = MonoLabelSmall,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                GhostButton(text = "Next →", onClick = { onShiftDay(1) })
            }
            Spacer(Modifier.height(8.dp))
            EmberButton(
                text = when {
                    state.playing -> "Pause"
                    state.cursor >= state.events.size && state.events.isNotEmpty() -> "Replay"
                    else -> "Play"
                },
                onClick = onTogglePlay,
                enabled = state.events.isNotEmpty(),
            )

            Spacer(Modifier.height(16.dp))
            ProgressBar(state.progress)

            Spacer(Modifier.height(20.dp))
            Text("HOURLY HEAT", style = MonoLabelSmall, color = colors.textMuted)
            Spacer(Modifier.height(6.dp))
            HourHeat(state.events)

            Spacer(Modifier.height(20.dp))
            when {
                state.loading -> Text(
                    "Loading…",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                )
                state.events.isEmpty() -> Text(
                    "No focus activity on this day.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                )
                else -> state.visible.forEach { TimelineRow(it) }
            }

            Spacer(Modifier.height(24.dp))
            GhostButton(text = "Back", onClick = onBack)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    val colors = Stackd.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(colors.textPrimary.copy(alpha = 0.05f), CircleShape),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(4.dp)
                .background(colors.accent, CircleShape),
        )
    }
}

/** 24 cells, one per UTC hour, tinted by focus seconds in that hour. */
@Composable
private fun HourHeat(events: List<ReplayEvent>) {
    val colors = Stackd.colors
    val perHour = LongArray(24)
    events.filter { it.kind == "session" }.forEach { e ->
        val h = parseIsoMillis(e.at)?.let {
            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).hour
        } ?: return@forEach
        perHour[h] += e.durationSeconds
    }
    val empty = colors.textPrimary.copy(alpha = 0.03f)
    val accent = colors.accent
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
    ) {
        val gap = 2.dp.toPx()
        val cellW = (size.width - gap * 23) / 24f
        for (h in 0 until 24) {
            val intensity = (perHour[h] / 3600f).coerceIn(0f, 1f)
            val color = if (intensity == 0f) empty else accent.copy(alpha = 0.15f + intensity * 0.75f)
            drawRoundRect(
                color = color,
                topLeft = Offset(h * (cellW + gap), 0f),
                size = Size(cellW, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        listOf("00", "06", "12", "18", "24").forEach {
            Text(it, style = MonoLabelSmall, color = colors.textMuted)
        }
    }
}

@Composable
private fun TimelineRow(ev: ReplayEvent) {
    val colors = Stackd.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(10.dp)
                .background(
                    when (ev.kind) {
                        "session" -> colors.accent
                        "achievement" -> colors.live
                        else -> colors.textPrimary.copy(alpha = 0.4f)
                    },
                    CircleShape,
                ),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .background(colors.textPrimary.copy(alpha = 0.02f), Radius2Xl)
                .border(1.dp, colors.border, Radius2Xl)
                .padding(12.dp),
        ) {
            Text(
                "${clockTime(ev.at)} · ${ev.kind}",
                style = MonoLabelSmall, color = colors.textMuted,
            )
            Text(ev.label, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
        }
    }
}

/* -------------------------------- helpers --------------------------------- */

private val PRETTY: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d").withZone(ZoneOffset.UTC)

private fun prettyDate(iso: String): String =
    runCatching { PRETTY.format(LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC)) }.getOrDefault(iso)

private val CLOCK: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

private fun clockTime(iso: String): String =
    parseIsoMillis(iso)?.let { CLOCK.format(Instant.ofEpochMilli(it)) } ?: ""
