package app.stackd.feature.insights

import app.stackd.core.ui.pageGlow

import app.stackd.core.ui.glassSurface

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.Role
import app.stackd.core.ui.pressFeedback
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stackd.core.AppContainer
import app.stackd.core.stackdViewModel
import androidx.compose.foundation.shape.CircleShape
import app.stackd.core.theme.Radius2Xl
import app.stackd.core.theme.SerifFamily
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.SkeletonBlock
import app.stackd.core.ui.SkeletonCard
import app.stackd.core.ui.animatedCount
import app.stackd.core.ui.reveal
import app.stackd.data.room.FocusHistoryRow
import app.stackd.feature.dashboard.ActivityHeatmap
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

private val WEEKDAY_NAMES = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

// Mirrors src/lib/proactive-ai.functions.ts: 21-day window, "medium" confidence at 8+ sessions.
private const val PREDICTION_WINDOW_DAYS = 21
private const val PREDICTION_MIN_SESSIONS = 8

data class InsightsUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val rows: List<FocusHistoryRow> = emptyList(),
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val lifetimeXp: Long = 0,
    /**
     * Server-computed proactive insights (smart schedule, focus prediction,
     * burnout). Deterministic on the server with optional LLM copy polish; the
     * panel renders only once it lands and stays hidden if the backend is
     * unreachable. No local fallback — the math needs the server's key.
     */
    val proactive: app.stackd.data.ai.ProactiveInsight? = null,
    /** LLM-written weekly narrative. Renders only when the AI backend answers. */
    val weeklyStory: String? = null,
    /** Discovered patterns shown under the story — web weekly-narrative-card. */
    val patterns: List<String> = emptyList(),
    /** True until the weekly story request settles — shows "Composing…". */
    val aiLoading: Boolean = true,
    /** Monthly AI action meter — web ai-usage-meter on insights. */
    val aiUsage: app.stackd.data.premium.AiUsage? = null,
) {
    // Computed once per state instance (lazy), not on every read during
    // recomposition — each of these walks up to 1000 history rows.
    val totals: AnalyticsEngine.Totals by lazy { AnalyticsEngine.totals(rows) }
    val hourBuckets: List<AnalyticsEngine.HourBucket> by lazy { AnalyticsEngine.hourBuckets(rows) }
    val dna: AnalyticsEngine.Dna by lazy { AnalyticsEngine.dna(rows) }

    /** Best focus hour by seconds held — the web's "Signal" callout. */
    val bestHour: Int? by lazy {
        hourBuckets.filter { it.seconds > 0 }.maxByOrNull { it.seconds }?.hour
    }

    /** Best weekday by seconds held (local zone), or null when empty. */
    val bestWeekday: String? by lazy {
        if (rows.isEmpty()) return@lazy null
        val byDay = LongArray(7)
        rows.forEach { r ->
            val millis = app.stackd.core.parseIsoMillis(r.createdAt) ?: return@forEach
            val dow = java.time.Instant.ofEpochMilli(millis)
                .atZone(java.time.ZoneId.systemDefault()).dayOfWeek.value % 7
            byDay[dow] += r.durationSeconds.toLong()
        }
        val top = byDay.indices.maxByOrNull { byDay[it] } ?: return@lazy null
        if (byDay[top] > 0) WEEKDAY_NAMES[top] else null
    }

    val forecast: Forecast by lazy { forecast(rows, lifetimeXp) }

    /** Sessions in the proactive model's window — feeds the "learning" progress. */
    val recentSessions: Int by lazy {
        val cutoff = System.currentTimeMillis() - PREDICTION_WINDOW_DAYS * 86_400_000L
        rows.count { (app.stackd.core.parseIsoMillis(it.createdAt) ?: 0L) >= cutoff }
    }

    /** Top session tags by frequency — web's tag-distribution bars. */
    val tagDistribution: List<Pair<String, Int>> by lazy {
        rows.flatMap { it.tags.orEmpty() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(8)
            .map { it.key to it.value }
    }
}

/** 120-day analytics — the web's `insights.tsx` over `getAnalytics`. */
class InsightsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(InsightsUiState())
    val state: StateFlow<InsightsUiState> = _state

    init {
        load()
        fetchAi()
    }

    /**
     * Pulls the two AI panels (proactive insights, weekly narrative) best-effort.
     * Each stays hidden if its route returns null. Kept separate from [load] so a
     * slow AI backend never delays the deterministic 120-day charts.
     */
    private fun fetchAi() {
        viewModelScope.launch {
            container.ai.proactiveInsights()?.let {
                _state.value = _state.value.copy(proactive = it)
            }
        }
        viewModelScope.launch {
            // Story and patterns load together, as on the web's narrative card.
            val story = async { container.ai.weeklyStory() }
            val patterns = async { container.ai.discoverPatterns() }
            // Await BEFORE reading state: `_state.value.copy(x = await())` reads
            // the receiver first, so a load() finishing meanwhile was overwritten.
            val s = story.await()?.story
            val p = patterns.await()?.patterns.orEmpty()
            _state.update { it.copy(weeklyStory = s, patterns = p, aiLoading = false) }
        }
        viewModelScope.launch {
            runCatching { container.premium.aiUsage() }.getOrNull()?.let {
                _state.value = _state.value.copy(aiUsage = it)
            }
        }
    }

    private fun cacheKey(userId: String) = "insights:$userId"

    fun load() {
        val userId = container.auth.currentUserId ?: return
        // Stale-while-revalidate: seed from the last cached state so re-entry
        // shows data instantly instead of a spinner, then revalidate below.
        val cached: InsightsUiState? = container.cache.get(cacheKey(userId))
        _state.value = (cached ?: _state.value).copy(loading = cached == null, error = false)
        viewModelScope.launch {
            runCatching {
                val since = Instant.now().minusSeconds(120L * 24 * 3600).toString()
                val rows = container.profiles.historySince(userId, since, limit = 1000)
                val profile = container.profiles.getProfile(userId)
                Triple(rows, profile?.currentFocusStreak ?: 0, profile?.lifetimeXp ?: 0)
            }.fold(
                onSuccess = { (rows, streak, lifetimeXp) ->
                    // Preserve any AI panels fetchAi() may have already landed —
                    // they run concurrently with this load and shouldn't be wiped.
                    val fresh = _state.value.copy(
                        loading = false, error = false,
                        rows = rows, streak = streak, lifetimeXp = lifetimeXp,
                    )
                    _state.value = fresh
                    // Cache the ledger only (AI panels are cheap to refetch and
                    // shouldn't persist a stale narrative across sessions).
                    container.cache.put(
                        cacheKey(userId),
                        fresh.copy(proactive = null, weeklyStory = null),
                    )
                },
                onFailure = { _state.value = _state.value.copy(loading = false, error = cached == null) },
            )
        }
    }
}

@Composable
fun InsightsRoute(
    onBack: () -> Unit,
    onStart: () -> Unit = {},
    modifier: Modifier = Modifier,
    vm: InsightsViewModel = viewModel(factory = stackdViewModel { InsightsViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    InsightsScreen(
        state = state, onRetry = vm::load, onBack = onBack, onStart = onStart, modifier = modifier,
    )
}

@Composable
fun InsightsScreen(
    state: InsightsUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = Stackd.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .pageGlow()
            .verticalScroll(rememberScrollState()),
    ) {
        ResponsiveColumn {
            app.stackd.core.ui.ScreenHeader("STACK'D / INSIGHTS", onBack, title = "Your progress")
            Spacer(Modifier.height(24.dp))

            when {
                state.loading -> {
                    // Mirrors the layout below: hero, 3-up stats, then chart cards.
                    SkeletonBlock(Modifier.fillMaxWidth().height(120.dp), Radius2Xl)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(3) { SkeletonBlock(Modifier.weight(1f).height(72.dp), Radius2Xl) }
                    }
                    Spacer(Modifier.height(24.dp))
                    SkeletonCard(height = 120.dp)
                    SkeletonCard(height = 200.dp)
                }
                state.error -> {
                    Text(
                        "Couldn't load your analytics.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(16.dp))
                    GhostButton(text = "Retry", onClick = onRetry)
                }
                state.rows.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassSurface(Radius2Xl)
                        .padding(24.dp),
                ) {
                    Text(
                        "Nothing to chart yet",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your focus radar, hourly rhythm and heatmap",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "They draw themselves from your sessions. Hold your first " +
                            "stack and the patterns start appearing here.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(24.dp))
                    EmberButton(text = "Start your first session", onClick = onStart)
                }
                else -> {
                    val t = state.totals
                    var i = 0

                    // 1. Hero — one number, one sentence. Summary before detail.
                    Column(Modifier.fillMaxWidth().reveal(i++)) {
                        val secs = animatedCount((t.hours * 3600).toFloat())
                        Text(
                            formatFocus(secs.toLong()),
                            style = MaterialTheme.typography.displayMedium,
                            fontFamily = SerifFamily,
                            color = colors.accent,
                            maxLines = 1,
                        )
                        Text(
                            "focused in the last 120 days",
                            style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                        )
                        Text(
                            "${"%,d".format(t.xp)} XP · last 120 days",
                            style = MaterialTheme.typography.bodySmall, color = colors.textMuted,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(takeaway(t), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                    }

                    // 2. Three supporting stats.
                    Spacer(Modifier.height(24.dp))
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min).reveal(i++),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val scoreColor = when {
                            t.avgScore >= 80 -> colors.accent
                            t.avgScore < 40 -> colors.textMuted
                            else -> colors.textPrimary
                        }
                        val tile = Modifier.weight(1f).fillMaxHeight()
                        StatTile("Sessions", "${t.sessions}", tile)
                        StatTile("Avg score", "${t.avgScore}", tile, scoreColor)
                        // Zero state stays honest ("0 days") with an invitation under it.
                        StatTile(
                            "Streak", "${state.streak} ${if (state.streak == 1) "day" else "days"}", tile,
                            helper = if (state.streak > 0) null else "Your first Stack starts your run",
                        )
                    }

                    // 3. Deep dive.
                    Section("Discipline", i++) { DisciplineCard(t) }
                    Section("Focus shape", i++) {
                        FocusRadar(state.dna.traits)
                        Spacer(Modifier.height(8.dp))
                        FocusRadarReading(state.dna.traits)
                    }
                    Section("When you focus", i++) {
                        HourBars(state.hourBuckets)
                        strongestWindow(state.hourBuckets)?.let { w ->
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Strongest window: $w",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            PlanAction(onStart)
                        }
                    }
                    Section("Activity", i++) { ActivityHeatmap(state.rows, weeks = 17) }
                    Section("Your signal", i++) { SignalCallout(state.bestHour, state.bestWeekday) }
                    if (state.tagDistribution.isNotEmpty()) {
                        Section("By tag", i++) { TagBars(state.tagDistribution) }
                    }
                    Section("Goal forecast", i++) { ForecastCard(state.forecast) }

                    // AI panels — render only when the backend answered.
                    state.proactive?.let { p ->
                        Section("Looking ahead", i++) { ProactiveCard(p, state.recentSessions, onStart) }
                    }
                    val story = state.weeklyStory?.takeIf { it.isNotBlank() }
                    if (story != null || state.aiLoading) {
                        Section("This week", i++) {
                            if (story != null) {
                                WeeklyStoryCard(story, state.patterns)
                            } else {
                                SkeletonCard(height = 96.dp)
                            }
                        }
                    }
                    state.aiUsage?.takeIf { it.unlimited || it.allowance > 0 }?.let { u ->
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "AI actions: " + if (u.unlimited) "unlimited" else "${u.used} of ${u.allowance} used this billing period",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                }
            }

            Spacer(Modifier.height(56.dp))
        }
    }
}

/** One plain-language takeaway derived from the totals — no AI call. */
private fun takeaway(t: AnalyticsEngine.Totals): String = when {
    t.sessions < 3 -> "A few more sessions and your patterns come into focus."
    t.cleanRate < 50 ->
        "Your clean rate is ${t.cleanRate}%. You're still finding your rhythm — try a 15–20m Stack next."
    t.avgScore >= 80 -> "Averaging ${t.avgScore} with ${t.cleanRate}% clean — elite focus."
    t.avgScore < 50 -> "${t.cleanRate}% of sessions stay clean. Longer holds will lift your score."
    else -> "${t.cleanRate}% clean, averaging ${t.avgScore}. Keep stacking."
}

/** Sentence-case section title over its content, revealed in order. */
@Composable
private fun Section(title: String, index: Int, content: @Composable () -> Unit) {
    Spacer(Modifier.height(32.dp))
    Column(Modifier.fillMaxWidth().reveal(index)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Stackd.colors.textPrimary,
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** Clean-rate bar + breach figures — one card instead of three boxes. */
@Composable
private fun DisciplineCard(t: AnalyticsEngine.Totals) {
    val colors = Stackd.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(Radius2Xl)
            .padding(16.dp),
    ) {
        DisciplineRow("Clean sessions", "${t.cleanRate}%", colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(colors.textPrimary.copy(alpha = 0.06f), CircleShape),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(t.cleanRate.coerceIn(0, 100) / 100f)
                    .height(6.dp)
                    .background(colors.accent, CircleShape),
            )
        }
        Spacer(Modifier.height(16.dp))
        DisciplineRow("Breaches per session", "%.1f".format(t.breachesPerSession))
        Spacer(Modifier.height(8.dp))
        DisciplineRow("Total breaches", "${t.breaches}")
    }
}

@Composable
private fun DisciplineRow(label: String, value: String, labelColor: androidx.compose.ui.graphics.Color = Stackd.colors.textMuted) {
    val colors = Stackd.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = labelColor)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
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
            listOf("12am", "6am", "12pm", "6pm", "11pm").forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
        }
    }
}

/** "You hold best around 1 PM on Tuesdays." — web's Signal card. */
@Composable
private fun SignalCallout(bestHour: Int?, bestWeekday: String?) {
    val colors = Stackd.colors
    val text = when {
        bestHour != null && bestWeekday != null ->
            "You hold best around ${hour12(bestHour)} on ${bestWeekday}s."
        bestHour != null ->
            "You hold best around ${hour12(bestHour)}."
        else -> "Hold a few more sessions and a pattern will surface here."
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(Radius2Xl)
            .padding(16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
    }
}

/** Horizontal bars, one per top tag, scaled to the most-used tag. */
@Composable
private fun TagBars(dist: List<Pair<String, Int>>) {
    val colors = Stackd.colors
    val max = dist.maxOf { it.second }.coerceAtLeast(1)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(Radius2Xl)
            .padding(16.dp),
    ) {
        dist.forEachIndexed { i, (tag, n) ->
            if (i > 0) Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("#$tag", style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
                Text("$n", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(colors.textPrimary.copy(alpha = 0.05f), androidx.compose.foundation.shape.CircleShape),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(n.toFloat() / max)
                        .height(5.dp)
                        .background(colors.accent, androidx.compose.foundation.shape.CircleShape),
                )
            }
        }
    }
}

/**
 * One concept: the next XP milestone and its ETA at the 30-day pace, plus one
 * lever ("add 5 min/day") — web's GoalForecast, same data.
 */
@Composable
private fun ForecastCard(f: Forecast) {
    val colors = Stackd.colors
    val next = f.projections.firstOrNull()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(Radius2Xl)
            .padding(16.dp),
    ) {
        if (next == null) {
            Text(
                "Hold a few Stacks and your next milestone's ETA appears here.",
                style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
            )
        } else {
            Text(
                "Next milestone · ${"%,d".format(next.targetXp)} XP",
                style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
            )
            Spacer(Modifier.height(4.dp))
            if (next.daysNeeded >= 9999) {
                Text(
                    "Hold a few Stacks this month and an ETA appears.",
                    style = MaterialTheme.typography.titleMedium, color = colors.textPrimary,
                )
            } else {
                Text(
                    "~${next.daysNeeded} days",
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = SerifFamily,
                    color = colors.accent,
                )
                Text(
                    "at your current pace · ~${f.avgDailyMinutes} min/day",
                    style = MaterialTheme.typography.bodySmall, color = colors.textMuted,
                )
            }
            leverDays(f, next)?.let { days ->
                Spacer(Modifier.height(16.dp))
                Text(
                    "Add 5 min/day → ~$days days",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Days to [next] if the user held [extraMin] more minutes a day at their XP-per-minute rate. */
internal fun leverDays(f: Forecast, next: Projection, extraMin: Int = 5): Int? {
    if (f.avgDailyMinutes <= 0 || f.avgDailyXp <= 0) return null
    val xpPerMin = f.avgDailyXp.toDouble() / f.avgDailyMinutes
    val remaining = (next.targetXp - f.currentXp).coerceAtLeast(0)
    return Math.ceil(remaining / ((f.avgDailyMinutes + extraMin) * xpPerMin)).toInt()
}

/** Under an hour as minutes ("18m"), otherwise hours and minutes ("2h 10m"). */
internal fun formatFocus(totalSeconds: Long): String {
    val m = totalSeconds.coerceAtLeast(0) / 60
    return when {
        m < 60 -> "${m}m"
        m % 60 == 0L -> "${m / 60}h"
        else -> "${m / 60}h ${m % 60}m"
    }
}

/** 13 -> "1 PM", 0 -> "12 AM". */
internal fun hour12(h: Int): String = "${h12(h)} ${if (h % 24 < 12) "AM" else "PM"}"

private fun h12(h: Int) = (h % 12).let { if (it == 0) 12 else it }

/**
 * Best contiguous 2–3h window by focused seconds, e.g. "1–3 PM" (end exclusive,
 * wraps midnight). Stretches to 3h when the stronger neighbour hour holds at
 * least half the window's average hour. Null when nothing is held.
 */
internal fun strongestWindow(buckets: List<AnalyticsEngine.HourBucket>): String? {
    val secs = IntArray(24).also { a -> buckets.forEach { a[it.hour] += it.seconds } }
    if (secs.all { it == 0 }) return null
    val start = (0 until 24).maxBy { secs[it] + secs[(it + 1) % 24] }
    val pair = secs[start] + secs[(start + 1) % 24]
    val before = secs[(start + 23) % 24]
    val after = secs[(start + 2) % 24]
    val (s, len) = when {
        maxOf(before, after) * 4 < pair -> start to 2
        after >= before -> start to 3
        else -> (start + 23) % 24 to 3
    }
    val e = (s + len) % 24
    val sameHalf = (s < 12) == (e < 12) && e != 0
    return if (sameHalf) "${h12(s)}–${hour12(e)}" else "${hour12(s)}–${hour12(e)}"
}

/** Quiet text action under a chart — opens the plan screen. */
@Composable
private fun PlanAction(onStart: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .pressFeedback(source)
            .clickable(source, indication = null, role = Role.Button, onClick = onStart),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            "Plan a Stack →",
            style = MaterialTheme.typography.bodyMedium,
            color = Stackd.colors.accent,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Proactive insights — web's proactive panel: best focus window, next-score
 * prediction, and burnout risk with signals. Server-computed; renders only when
 * the AI backend answered.
 */
@Composable
private fun ProactiveCard(
    p: app.stackd.data.ai.ProactiveInsight,
    recentSessions: Int,
    onStart: () -> Unit,
) {
    val colors = Stackd.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(Radius2Xl)
            .padding(16.dp),
    ) {
        p.smartSchedule?.let { s ->
            Text(s.label, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(s.rationale, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            Spacer(Modifier.height(8.dp))
            GhostButton(text = "Start a Stack", onClick = onStart)
            Spacer(Modifier.height(16.dp))
        }
        val fp = p.focusPrediction
        if (fp.confidence == "medium" || fp.confidence == "high") {
            // Enough data: the number, with confidence in words rather than a label.
            val sure = if (fp.confidence == "high") "confident" else "fairly confident"
            Text(
                "Next session around ${fp.nextScore} out of 100 · $sure",
                style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(fp.note, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        } else {
            // Low confidence: no number. Show how far the model is from a real read.
            val have = recentSessions.coerceIn(0, PREDICTION_MIN_SESSIONS)
            Text(
                "Learning your pattern",
                style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(colors.textPrimary.copy(alpha = 0.06f), androidx.compose.foundation.shape.CircleShape),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(have / PREDICTION_MIN_SESSIONS.toFloat())
                        .height(4.dp)
                        .background(colors.accent, androidx.compose.foundation.shape.CircleShape),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "$have of $PREDICTION_MIN_SESSIONS sessions in the last 3 weeks before a score prediction.",
                style = MaterialTheme.typography.bodySmall, color = colors.textMuted,
            )
        }

        Spacer(Modifier.height(16.dp))
        val riskColor = if (p.burnout.risk == "high") colors.breach else colors.textPrimary
        Text(
            "Burnout risk: ${p.burnout.risk}",
            style = MaterialTheme.typography.bodyMedium, color = riskColor, fontWeight = FontWeight.SemiBold,
        )
        p.burnout.signals.forEach { sig ->
            Spacer(Modifier.height(4.dp))
            Text("· $sig", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            p.burnout.recommendation,
            style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary,
        )
    }
}

/** Compact neutral stat tile: sans label over a semibold value. */
@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = Stackd.colors.textPrimary,
    helper: String? = null,
) {
    val colors = Stackd.colors
    Column(
        modifier = modifier
            .glassSurface(Radius2Xl)
            .padding(16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Text(
            value, style = MaterialTheme.typography.titleLarge, color = valueColor,
            fontWeight = FontWeight.SemiBold, maxLines = 1,
        )
        helper?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
    }
}

/** LLM-written weekly narrative — web's weekly-story card. */
@Composable
private fun WeeklyStoryCard(story: String, patterns: List<String> = emptyList()) {
    val colors = Stackd.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(colors.accent.copy(alpha = 0.09f), colors.surface)), Radius2Xl)
            .padding(20.dp),
    ) {
        // Editorial serif, as the web sets its featured AI copy.
        Text(
            story,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = SerifFamily,
            fontWeight = FontWeight.Normal,
            color = colors.textPrimary,
        )
        // Patterns belong to the story card (web narrative card), not below it.
        if (patterns.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.accent.copy(alpha = 0.15f)))
            patterns.forEach { p ->
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("◆", style = MaterialTheme.typography.bodySmall, color = colors.accent)
                    Text(p, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
            }
        }
    }
}
