package app.stackd.feature.insights

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stackd.core.AppContainer
import app.stackd.core.stackdViewModel
import app.stackd.core.theme.MonoLabel
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.Radius2Xl
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.SectionLabel
import app.stackd.data.room.FocusHistoryRow
import app.stackd.feature.dashboard.ActivityHeatmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant

private val WEEKDAY_NAMES = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

data class InsightsUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val rows: List<FocusHistoryRow> = emptyList(),
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val lifetimeXp: Long = 0,
) {
    val totals: AnalyticsEngine.Totals get() = AnalyticsEngine.totals(rows)
    val hourBuckets: List<AnalyticsEngine.HourBucket> get() = AnalyticsEngine.hourBuckets(rows)
    val dna: AnalyticsEngine.Dna get() = AnalyticsEngine.dna(rows)

    /** Best focus hour by seconds held — the web's "Signal" callout. */
    val bestHour: Int?
        get() = hourBuckets.filter { it.seconds > 0 }.maxByOrNull { it.seconds }?.hour

    /** Best weekday by seconds held (local zone), or null when empty. */
    val bestWeekday: String?
        get() {
            if (rows.isEmpty()) return null
            val byDay = LongArray(7)
            rows.forEach { r ->
                val millis = app.stackd.core.parseIsoMillis(r.createdAt) ?: return@forEach
                val dow = java.time.Instant.ofEpochMilli(millis)
                    .atZone(java.time.ZoneId.systemDefault()).dayOfWeek.value % 7
                byDay[dow] += r.durationSeconds.toLong()
            }
            val top = byDay.indices.maxByOrNull { byDay[it] } ?: return null
            return if (byDay[top] > 0) WEEKDAY_NAMES[top] else null
        }

    val forecast: Forecast get() = forecast(rows, lifetimeXp)
}

/** 120-day analytics — the web's `insights.tsx` over `getAnalytics`. */
class InsightsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(InsightsUiState())
    val state: StateFlow<InsightsUiState> = _state

    init {
        load()
    }

    fun load() {
        val userId = container.auth.currentUserId ?: return
        _state.value = _state.value.copy(loading = true, error = false)
        viewModelScope.launch {
            runCatching {
                val since = Instant.now().minusSeconds(120L * 24 * 3600).toString()
                val rows = container.profiles.historySince(userId, since, limit = 1000)
                val profile = container.profiles.getProfile(userId)
                Triple(rows, profile?.currentFocusStreak ?: 0, profile?.lifetimeXp ?: 0)
            }.fold(
                onSuccess = { (rows, streak, lifetimeXp) ->
                    _state.value = InsightsUiState(
                        loading = false, rows = rows, streak = streak, lifetimeXp = lifetimeXp,
                    )
                },
                onFailure = { _state.value = _state.value.copy(loading = false, error = true) },
            )
        }
    }
}

@Composable
fun InsightsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: InsightsViewModel = viewModel(factory = stackdViewModel { InsightsViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    InsightsScreen(state = state, onRetry = vm::load, onBack = onBack, modifier = modifier)
}

@Composable
fun InsightsScreen(
    state: InsightsUiState,
    onRetry: () -> Unit,
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
            Text("STACK'D / INSIGHTS", style = MonoLabel, color = colors.textMuted)
            Spacer(Modifier.height(16.dp))
            SectionLabel("120-DAY LEDGER")
            Spacer(Modifier.height(16.dp))

            when {
                state.loading -> Text(
                    "Crunching your history…",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                )
                state.error -> {
                    Text(
                        "Couldn't load your analytics.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Retry", onClick = onRetry)
                }
                state.rows.isEmpty() -> Text(
                    "No sessions yet — your patterns appear after the first stack.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                )
                else -> {
                    val t = state.totals
                    // Stat tiles, two per row like the web's grid.
                    val tiles = listOf(
                        "SESSIONS" to "${t.sessions}",
                        "HOURS" to "%.1f".format(t.hours),
                        "TOTAL XP" to "${t.xp}",
                        "AVG SCORE" to "${t.avgScore}",
                        "CLEAN RATE" to "${t.cleanRate}%",
                        "BREACHES/SESSION" to "%.1f".format(t.breachesPerSession),
                        "STREAK" to "${state.streak}d",
                        "BREACHES" to "${t.breaches}",
                    )
                    tiles.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { (label, value) ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(colors.textPrimary.copy(alpha = 0.03f), Radius2Xl)
                                        .border(1.dp, colors.border, Radius2Xl)
                                        .padding(14.dp),
                                ) {
                                    Text(label, style = MonoLabelSmall, color = colors.textMuted)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        value,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    Spacer(Modifier.height(16.dp))
                    SectionLabel("FOCUS RADAR")
                    Spacer(Modifier.height(8.dp))
                    FocusRadar(state.dna.traits)

                    Spacer(Modifier.height(16.dp))
                    SectionLabel("BY HOUR OF DAY")
                    Spacer(Modifier.height(8.dp))
                    HourBars(state.hourBuckets)

                    Spacer(Modifier.height(16.dp))
                    SectionLabel("HEATMAP · 120 DAYS")
                    Spacer(Modifier.height(8.dp))
                    ActivityHeatmap(state.rows, weeks = 17)

                    Spacer(Modifier.height(16.dp))
                    SectionLabel("SIGNAL")
                    Spacer(Modifier.height(8.dp))
                    SignalCallout(state.bestHour, state.bestWeekday)

                    Spacer(Modifier.height(16.dp))
                    SectionLabel("GOAL FORECAST")
                    Spacer(Modifier.height(8.dp))
                    ForecastCard(state.forecast)
                }
            }

            Spacer(Modifier.height(24.dp))
            GhostButton(text = "Back", onClick = onBack)
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** 24 vertical bars, focused seconds per hour — web's hour distribution. */
@Composable
private fun HourBars(buckets: List<AnalyticsEngine.HourBucket>) {
    val colors = Stackd.colors
    val accent = colors.accent
    val empty = colors.textPrimary.copy(alpha = 0.06f)
    val max = buckets.maxOf { it.seconds }.coerceAtLeast(1)
    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(72.dp)) {
            val gap = 2.dp.toPx()
            val barW = (size.width - gap * 23) / 24
            buckets.forEach { b ->
                val frac = b.seconds.toFloat() / max
                val h = (size.height * frac).coerceAtLeast(if (b.seconds > 0) 3.dp.toPx() else 1.5f)
                drawRect(
                    color = if (b.seconds > 0) accent.copy(alpha = 0.4f + frac * 0.6f) else empty,
                    topLeft = Offset(b.hour * (barW + gap), size.height - h),
                    size = Size(barW, h),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("00", "06", "12", "18", "23").forEach {
                Text(it, style = MonoLabelSmall, color = colors.textMuted)
            }
        }
    }
}

/** "You hold best around HH:00, on {weekday}s." — web's Signal card. */
@Composable
private fun SignalCallout(bestHour: Int?, bestWeekday: String?) {
    val colors = Stackd.colors
    val text = when {
        bestHour != null && bestWeekday != null ->
            "You hold best around ${bestHour.toString().padStart(2, '0')}:00, on ${bestWeekday}s."
        bestHour != null ->
            "You hold best around ${bestHour.toString().padStart(2, '0')}:00."
        else -> "Hold a few more sessions and a pattern will surface here."
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.textPrimary.copy(alpha = 0.03f), Radius2Xl)
            .border(1.dp, colors.border, Radius2Xl)
            .padding(16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
    }
}

/** 30-day pace and the next three XP milestones — web's GoalForecast. */
@Composable
private fun ForecastCard(f: Forecast) {
    val colors = Stackd.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.textPrimary.copy(alpha = 0.03f), Radius2Xl)
            .border(1.dp, colors.border, Radius2Xl)
            .padding(16.dp),
    ) {
        Text(
            "~${f.avgDailyMinutes} min/day · ~${f.avgDailyXp} XP/day",
            style = MonoLabelSmall, color = colors.accent,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "About ${f.weeklyForecastMinutes} min this week, ${f.monthlyForecastMinutes} this month.",
            style = MaterialTheme.typography.bodySmall, color = colors.textMuted,
        )
        if (f.projections.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Hold sessions and milestone ETAs appear here.",
                style = MaterialTheme.typography.bodySmall, color = colors.textMuted,
            )
        }
        f.projections.forEach { pr ->
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(pr.label, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                Text(
                    if (pr.daysNeeded >= 9999) "—" else "~${pr.daysNeeded}d",
                    style = MonoLabelSmall, color = colors.textMuted,
                )
            }
        }
    }
}
