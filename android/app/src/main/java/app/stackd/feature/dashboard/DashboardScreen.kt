package app.stackd.feature.dashboard

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stackd.core.formatDuration
import app.stackd.core.formatHours
import app.stackd.core.parseIsoMillis
import app.stackd.core.stackdViewModel
import app.stackd.core.theme.MonoFamily
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.Radius2Xl
import app.stackd.core.theme.RadiusMd
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.NavMenuSheet
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.SkeletonBlock
import app.stackd.core.ui.SkeletonCard
import app.stackd.core.ui.WIDE_MAX_CONTENT_WIDTH
import app.stackd.core.ui.pressFeedback
import app.stackd.data.room.FocusHistoryRow
import app.stackd.data.room.RoomRow
import app.stackd.feature.room.session.FocusScore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// ponytail: fixed daily goal; make it a profile setting when users ask to tune it.
private const val DAILY_GOAL_MIN = 60

/** Home shows a taste of history; the Timeline screen owns the full list. */
private const val RECENT_SESSIONS = 5

/**
 * Home. Stateless in the render — the [DashboardViewModel] owns the loads, and
 * every navigation is a hoisted callback so this composable never touches the
 * nav graph directly.
 */
@Composable
fun DashboardRoute(
    onStart: () -> Unit,
    onOpenRoom: (String) -> Unit,
    menuEntries: List<Pair<String, () -> Unit>> = emptyList(),
    /** Opens the Premium screen from the upgrade card; StackdNavHost must wire it. */
    onOpenPremium: () -> Unit = {},
    vm: DashboardViewModel = viewModel(
        factory = stackdViewModel {
            DashboardViewModel(
                it.auth, it.profiles, it.rooms, it.ai, it.cache, it.premium,
                app.stackd.data.progression.PrestigeRepository(it.client), it.client,
                it.appContextForWork.getSharedPreferences("dashboard_prefs", 0),
                it.snapshots,
            )
        },
    ),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    DashboardScreen(
        state = state,
        onStart = onStart,
        onOpenRoom = onOpenRoom,
        menuEntries = menuEntries,
        onRetry = vm::load,
        onClaimReward = vm::claimReward,
        onOpenPremium = onOpenPremium,
        onRegenRec = { vm.fetchAiRecommendation(fresh = true) },
        onRetryRec = { vm.fetchAiRecommendation(fresh = true) },
        onRegenInsights = { vm.fetchAiInsights(fresh = true) },
        onDismissAtlas = vm::dismissAtlas,
        onDismissUpgrade = vm::dismissUpgrade,
        onAscend = vm::ascend,
        onMyRoomsPage = vm::myRoomsGoTo,
        onExportCsv = {
            scope.launch {
                val export = vm.buildCsv()
                if (export != null) {
                    val stamp = LocalDate.now().toString()
                    CsvShare.share(context, "stackd-focus-history-$stamp.csv", export.csv)
                }
            }
        },
    )
}

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onStart: () -> Unit,
    onOpenRoom: (String) -> Unit,
    onRetry: () -> Unit,
    /**
     * Every destination the menu sheet offers, in order. Passed as one list
     * rather than a callback per screen: the parameter stack had grown to
     * eleven `onOpenX` lambdas that the dashboard only ever forwarded
     * verbatim into [NavMenuSheet], so the nav graph now owns the list.
     */
    menuEntries: List<Pair<String, () -> Unit>> = emptyList(),
    onClaimReward: () -> Unit = {},
    onExportCsv: () -> Unit = {},
    onOpenPremium: () -> Unit = {},
    onRegenRec: () -> Unit = {},
    onRetryRec: () -> Unit = {},
    onRegenInsights: () -> Unit = {},
    onDismissAtlas: () -> Unit = {},
    onDismissUpgrade: () -> Unit = {},
    onAscend: () -> Unit = {},
    onMyRoomsPage: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = Stackd.colors
    var showMenu by remember { mutableStateOf(false) }
    // "See all" reuses the nav graph's own Timeline entry, so Home needs no new
    // nav parameter and the route stays defined in one place.
    val openTimeline = menuEntries.firstOrNull { it.first == "Timeline" }?.second
    // Scroll on the full-width outer box; content capped + centered inside so it
    // doesn't sprawl on tablets/foldables/landscape.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState()),
    ) {
        ResponsiveColumn(maxContentWidth = WIDE_MAX_CONTENT_WIDTH) {
            TodayHero(state = state, onStart = onStart, onMore = { showMenu = true })

            // Atlas, made actionable: the recommendation is only worth showing if
            // acting on it is one tap.
            if (!state.loading && !state.isEmpty && !state.atlasDismissed) {
                Spacer(Modifier.height(12.dp))
                SuggestedSession(
                    // Prefer the LLM recommendation once it lands; until then (or
                    // if the AI backend is unreachable) show the local heuristic.
                    rec = state.aiRecommendation
                        ?: app.stackd.feature.insights.recommendNextSession(state.history),
                    loading = state.aiRecLoading,
                    error = state.aiRecError,
                    onStart = onStart,
                    onRegenerate = onRegenRec,
                    onRetry = onRetryRec,
                    onDismiss = onDismissAtlas,
                )
            }

            TodayStrip(
                reward = state.reward,
                claiming = state.claiming,
                notice = state.claimNotice,
                challengeProgress = state.greeting.challengeProgress,
                onClaim = onClaimReward,
            )
            Spacer(Modifier.height(36.dp))

            when {
                state.loading -> LoadingSkeleton()

                state.error -> {
                    // Degrade intentionally: the user IS signed in, Start focus
                    // above still works, and the ledger just couldn't sync. A calm
                    // inline notice reads as "temporarily unavailable", not broken.
                    Tile {
                        Text(
                            "Your history couldn't load",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "You can still start a session — your stats will appear once it syncs.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                        )
                        Spacer(Modifier.height(16.dp))
                        GhostButton(text = "Retry", onClick = onRetry)
                    }
                    Spacer(Modifier.height(32.dp))
                }

                state.isEmpty && state.live.isEmpty() -> {
                    EmptyLedger()
                    Spacer(Modifier.height(32.dp))
                }

                else -> {
                    if (state.live.isNotEmpty()) {
                        Section("Live now") { LiveNow(state.live, onOpenRoom) }
                    }
                    if (!state.isEmpty) {
                        Section("Your stats") { StatTiles(state) }
                        Section("Insights") {
                            InsightsCard(state.aiInsights, state.aiInsLoading, state.aiInsError, onRegenInsights)
                        }
                    }
                    if (state.myRooms.isNotEmpty() || state.myRoomsPage > 0) {
                        Section("My rooms") { MyRooms(state, onOpenRoom, onMyRoomsPage) }
                    }
                    if (!state.isEmpty) {
                        Section(
                            "Recent sessions",
                            action = openTimeline?.let { "See all" to it },
                        ) { SessionHistory(state.history.take(RECENT_SESSIONS), onOpenRoom) }
                        Section("Activity") { Tile { ActivityHeatmap(state.history) } }
                    }
                }
            }

            // Quiet tail: upsell and prestige matter, but not more than the ledger.
            if (!state.loading) {
                if (state.isPremium == false && !state.upgradeDismissed) {
                    UpgradeRow(onOpenPremium = onOpenPremium, onDismiss = onDismissUpgrade)
                    Spacer(Modifier.height(12.dp))
                }
                state.prestige?.let { PrestigeCard(it, state.ascending, state.prestigeNotice, onAscend) }
            }
        }
    }
    if (showMenu) {
        // CSV export lives in the sheet: a rare action doesn't earn a
        // full-width button above the fold.
        NavMenuSheet(
            onDismiss = { showMenu = false },
            entries = menuEntries + ("Export focus history (CSV)" to onExportCsv),
        )
    }
}

// Today hero

/**
 * The one place Home asks for attention: who you are, how far today has got
 * (goal ring + streak), and the single filled "Start focus" action. Everything
 * below it is supporting detail.
 */
@Composable
private fun TodayHero(state: DashboardUiState, onStart: () -> Unit, onMore: () -> Unit) {
    val colors = Stackd.colors
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zone)
    val todayMin = remember(state.history, today) { focusSecondsOn(state.history, today, zone) / 60 }
    val yesterdaySec = remember(state.history, today) {
        focusSecondsOn(state.history, today.minusDays(1), zone)
    }
    val hour = LocalTime.now().hour
    val dayPart = when {
        hour < 5 -> "Late night"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        hour < 21 -> "Good evening"
        else -> "Good night"
    }
    val g = state.greeting
    val extras = buildList {
        if (g.friendsOnline > 0) {
            add("${g.friendsOnline} ${if (g.friendsOnline == 1) "friend" else "friends"} focusing now")
        }
        if (yesterdaySec > 0) add("${Math.round(yesterdaySec / 360.0) / 10.0}h yesterday")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(colors.accent.copy(alpha = 0.09f), colors.surface)),
                Radius2Xl,
            )
            .border(1.dp, colors.accent.copy(alpha = 0.18f), Radius2Xl)
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                val clock = remember(hour) {
                    LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
                }
                Text("${dayPart.uppercase()} · $clock", style = MonoLabelSmall, color = colors.textMuted)
                Spacer(Modifier.height(6.dp))
                // Web's greeting: "Afternoon, Raghavendra Sujith." — large, tight, bold.
                Text(
                    "${dayPart.removePrefix("Good ").replaceFirstChar { it.uppercase() }}, ${state.name.substringBefore(' ').ifBlank { state.name }}.",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (extras.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        extras.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
            }
            IconButton(onClick = onMore) {
                Icon(app.stackd.core.ui.StackdIcons.GridView, contentDescription = "More", tint = colors.textMuted)
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.loading) {
                SkeletonBlock(Modifier.size(124.dp), CircleShape)
            } else {
                GoalRing(todayMin, DAILY_GOAL_MIN, Modifier.size(124.dp))
            }
            Spacer(Modifier.width(20.dp))
            Column(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {},
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        app.stackd.core.ui.StackdIcons.LocalFireDepartment,
                        contentDescription = null,
                        tint = if (state.streak > 0) colors.accent else colors.textMuted,
                        modifier = Modifier.size(26.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${state.streak}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    "session streak",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
                Spacer(Modifier.height(12.dp))
                // Goal-gradient: name the remaining distance, not the total.
                val left = DAILY_GOAL_MIN - todayMin
                Text(
                    when {
                        state.loading -> " "
                        left <= 0 -> "Daily goal reached. Anything more is a bonus."
                        // Hook (loss aversion): a streak you can lose motivates
                        // more than minutes you could gain.
                        todayMin == 0 && state.streak > 0 ->
                            "Keep your ${state.streak}-session streak alive today."
                        todayMin == 0 -> "$DAILY_GOAL_MIN min goal today."
                        else -> "$left min to today's goal."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (left <= 0) colors.accent else colors.textPrimary,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.padding(end = 12.dp)) {
            EmberButton(text = "Start focus", onClick = onStart)
        }
    }
}

/** Today's focused minutes against the daily goal, as an animated arc. */
@Composable
private fun GoalRing(minutes: Int, goal: Int, modifier: Modifier = Modifier) {
    val colors = Stackd.colors
    val progress by animateFloatAsState(
        (minutes.toFloat() / goal).coerceIn(0f, 1f),
        tween(900, easing = FastOutSlowInEasing),
        label = "goal",
    )
    val track = colors.textPrimary.copy(alpha = 0.07f)
    val accent = colors.accent
    Box(
        modifier.clearAndSetSemantics { contentDescription = "$minutes of $goal minutes focused today" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
            if (progress > 0f) {
                drawArc(
                    accent, -90f, 360f * progress, false, topLeft, arc,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${app.stackd.core.ui.animatedCount(minutes.toFloat()).toInt()}",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text("of $goal min", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
    }
}

/** Sum of focused seconds on one local calendar day. */
private fun focusSecondsOn(history: List<FocusHistoryRow>, day: LocalDate, zone: ZoneId): Int =
    history.sumOf { row ->
        val onDay = parseIsoMillis(row.createdAt)
            ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == day } == true
        if (onDay) row.durationSeconds else 0
    }

/**
 * Atlas's next-session pick as a one-tap action. The whole card starts a
 * session; refresh and dismiss sit off to the side as small icon targets.
 */
@Composable
private fun SuggestedSession(
    rec: app.stackd.feature.insights.SessionRecommendation,
    loading: Boolean,
    error: Boolean,
    onStart: () -> Unit,
    onRegenerate: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Stackd.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tappable(onClick = onStart)
            .background(colors.surface, Radius2Xl)
            .border(1.dp, colors.accent.copy(alpha = 0.22f), Radius2Xl)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).background(colors.accent.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(app.stackd.core.ui.StackdIcons.PlayArrow, contentDescription = null, tint = colors.accent)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(
                if (error) "SUGGESTED" else "SUGGESTED BY ATLAS",
                style = MonoLabelSmall,
                color = colors.accent,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${rec.durationMinutes} min · ${rec.topic}",
                // Web sets Atlas titles in its editorial serif.
                style = MaterialTheme.typography.titleLarge,
                fontFamily = app.stackd.core.theme.SerifFamily,
                color = colors.textPrimary,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                rec.rationale,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column {
            IconButton(onClick = onDismiss) {
                Icon(
                    app.stackd.core.ui.StackdIcons.Close,
                    contentDescription = "Dismiss suggestion",
                    tint = colors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = if (error) onRetry else onRegenerate, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = colors.textMuted,
                    )
                } else {
                    Icon(
                        app.stackd.core.ui.StackdIcons.Refresh,
                        contentDescription = "New suggestion",
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

// Sections

/** Titled block with consistent breathing room; [action] is an optional trailing link. */
@Composable
private fun Section(
    title: String,
    action: Pair<String, () -> Unit>? = null,
    content: @Composable () -> Unit,
) {
    val colors = Stackd.colors
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        action?.let { (label, go) -> TextAction(label, enabled = true, onClick = go) }
    }
    Spacer(Modifier.height(8.dp))
    content()
    Spacer(Modifier.height(32.dp))
}

/** Skeletons in the shape of the final sections, so data landing doesn't jump the page. */
@Composable
private fun LoadingSkeleton() {
    repeat(3) { i ->
        SkeletonBlock(Modifier.width(120.dp).height(18.dp))
        Spacer(Modifier.height(16.dp))
        SkeletonCard(height = if (i == 0) 96.dp else 148.dp)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EmptyLedger() {
    val colors = Stackd.colors
    Tile {
        Text(
            "Your first session writes the first line",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        // No second button here: Start focus above is the one way in.
        Text(
            "Nothing has been measured yet. Tap Start focus, stack your phone and " +
                "your stats start filling in here.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
        )
    }
}

@Composable
private fun StatTiles(state: DashboardUiState) {
    val colors = Stackd.colors
    val tier = FocusScore.tierForScore(state.avgScore.toDouble())
    // Three equal tiles in one row: one glance, no scrolling past a giant
    // hours number. Streak lives in the hero, so it isn't repeated here.
    // IntrinsicSize.Min + fillMaxHeight: tiles share one height whatever the text.
    Row(
        modifier = Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Bento, as the web's LIFETIME_PRESENCE panel: one hero number in display
        // type carries the section; the supporting stats stack beside it.
        val colors = Stackd.colors
        Column(
            Modifier
                .weight(1.25f)
                .fillMaxHeight()
                .background(colors.textPrimary.copy(alpha = 0.04f), RadiusMd)
                .border(1.dp, colors.border, RadiusMd)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("LIFETIME PRESENCE", style = MonoLabelSmall, color = colors.textMuted)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    String.format(java.util.Locale.US, "%.1f", app.stackd.core.ui.animatedCount((state.totalSeconds / 3600.0).toFloat())),
                    style = MaterialTheme.typography.displayMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
                Text(
                    "HOURS",
                    style = MonoLabelSmall,
                    color = colors.textMuted,
                    modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(app.stackd.core.ui.animatedCount(state.lifetimeXp.toFloat()).toInt().toString(), "Lifetime XP", Modifier.fillMaxWidth())
            StatTile(
                state.avgScore.toString(),
                // Tier reads from the value colour; naming it truncated ("Protocol Co…").
                "Avg score",
                Modifier.fillMaxWidth(),
                valueColor = Color(tier.hex),
            )
        }
    }
}

@Composable
private fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Stackd.colors.textPrimary,
) {
    val colors = Stackd.colors
    Column(
        modifier = modifier
            .background(colors.textPrimary.copy(alpha = 0.04f), RadiusMd)
            .border(1.dp, colors.border, RadiusMd)
            .padding(horizontal = 14.dp, vertical = 16.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = valueColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LiveNow(live: List<RoomRow>, onOpenRoom: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        live.forEach { room -> LiveSessionRow(room, onOpenRoom) }
    }
}

/**
 * The caller's rooms, 8 per page with Prev/Next — web's MyRoomsPanel.
 */
@Composable
private fun MyRooms(
    state: DashboardUiState,
    onOpenRoom: (String) -> Unit,
    onPage: (Int) -> Unit,
) {
    val colors = Stackd.colors
    if (state.myRoomsError) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Couldn't load this page.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            TextAction("Retry", enabled = true, onClick = { onPage(state.myRoomsPage) })
        }
        Spacer(Modifier.height(8.dp))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.myRooms.forEach { room ->
            val elapsed = remember(room.startedAt, room.endedAt) {
                val s = parseIsoMillis(room.startedAt)
                val e = parseIsoMillis(room.endedAt)
                if (s != null && e != null) ((e - s) / 1000).coerceAtLeast(0).toInt() else null
            }
            val active = room.statusEnum == app.stackd.data.room.RoomStatus.ACTIVE
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .tappable { onOpenRoom(room.code) }
                    .background(colors.textPrimary.copy(alpha = 0.03f), RadiusMd)
                    .border(1.dp, colors.border, RadiusMd)
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    // Room codes are identifiers, so they keep the mono face.
                    Text(room.code, style = MaterialTheme.typography.bodyLarge, fontFamily = MonoFamily, color = colors.textPrimary)
                    Text(
                        "${if (room.isHost(state.meId)) "Host" else "Guest"} · " +
                            room.status.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (active) colors.live else colors.textMuted,
                    )
                }
                Text(
                    formatDuration(elapsed ?: room.targetDurationSeconds.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
            }
        }
    }
    if (state.myRoomsPage > 0 || state.myRoomsHasMore) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextAction("← Prev", enabled = state.myRoomsPage > 0, onClick = { onPage(state.myRoomsPage - 1) })
            Text(
                "Page ${state.myRoomsPage + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            TextAction("Next →", enabled = state.myRoomsHasMore, onClick = { onPage(state.myRoomsPage + 1) })
        }
    }
}

/**
 * One live row with its own per-second ticker, isolated so only this row
 * recomposes each second — the surrounding stats and history stay still.
 */
@Composable
private fun LiveSessionRow(room: RoomRow, onOpenRoom: (String) -> Unit) {
    val colors = Stackd.colors
    val startedMillis = remember(room.startedAt) { parseIsoMillis(room.startedAt) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(room.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsed = startedMillis?.let { ((now - it) / 1000).toInt().coerceAtLeast(0) } ?: 0
    val pct = if (room.targetDurationSeconds > 0) {
        (elapsed.toFloat() / room.targetDurationSeconds).coerceIn(0f, 1f)
    } else 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tappable { onOpenRoom(room.code) }
            .background(colors.live.copy(alpha = 0.05f), RadiusMd)
            .border(1.dp, colors.live.copy(alpha = 0.25f), RadiusMd)
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(8.dp).background(colors.live, CircleShape))
        Text(room.code, style = MaterialTheme.typography.bodyMedium, fontFamily = MonoFamily, color = colors.textPrimary)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .background(colors.textPrimary.copy(alpha = 0.06f), CircleShape),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(pct)
                    .height(4.dp)
                    .background(colors.live, CircleShape),
            )
        }
        Text(formatDuration(elapsed), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
    }
}

@Composable
private fun SessionHistory(history: List<FocusHistoryRow>, onOpenRoom: (String) -> Unit) {
    val colors = Stackd.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.textPrimary.copy(alpha = 0.03f), Radius2Xl)
            .border(1.dp, colors.border, Radius2Xl)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        history.forEachIndexed { i, h ->
            val tier = FocusScore.tierForScore(h.score.toDouble())
            val tint = Color(tier.hex)
            val code = h.room?.code
            if (i > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (code != null) Modifier.tappable { onOpenRoom(code) } else Modifier)
                    .heightIn(min = 64.dp)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            tier.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                        if (isNew(h.createdAt)) {
                            Text(
                                "NEW",
                                style = MonoLabelSmall,
                                color = colors.live,
                                modifier = Modifier
                                    .border(1.dp, colors.live.copy(alpha = 0.5f), RadiusMd)
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        listOfNotNull(shortDate(h.createdAt), formatDuration(h.durationSeconds), "+${h.xp} XP", code)
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    h.score.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = tint,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun Tile(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = Stackd.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.textPrimary.copy(alpha = 0.03f), Radius2Xl)
            .border(1.dp, colors.border, Radius2Xl)
            .padding(20.dp),
    ) { content() }
}

/**
 * LLM-written ledger insights. Mirrors the web dashboard's insights card:
 * a headline over a few short paragraphs, with Regenerate / Retry and a
 * skeleton instead of hiding while the model works.
 */
@Composable
private fun InsightsCard(
    insights: app.stackd.data.ai.DashboardInsights?,
    loading: Boolean,
    error: Boolean,
    onRegenerate: () -> Unit,
) {
    val colors = Stackd.colors
    Tile {
        Text("AI · LEDGER", style = MonoLabelSmall, color = colors.accent)
        when {
            loading && insights == null -> {
                Spacer(Modifier.height(12.dp))
                SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(20.dp))
                Spacer(Modifier.height(10.dp))
                SkeletonBlock(Modifier.fillMaxWidth().height(14.dp))
                Spacer(Modifier.height(6.dp))
                SkeletonBlock(Modifier.fillMaxWidth(0.85f).height(14.dp))
            }
            insights == null -> {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (error) "Insights are unavailable right now." else "No insights yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                        modifier = Modifier.weight(1f),
                    )
                    TextAction("Retry", enabled = !loading, onClick = onRegenerate)
                }
            }
            else -> {
                if (insights.headline.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        insights.headline,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                insights.paragraphs.forEach { para ->
                    Spacer(Modifier.height(8.dp))
                    Text(para, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (insights.basedOnSessions > 0) "Based on ${insights.basedOnSessions} sessions" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        modifier = Modifier.weight(1f),
                    )
                    TextAction(if (loading) "Reading…" else "Regenerate", enabled = !loading, onClick = onRegenerate)
                }
            }
        }
    }
}

/** Dismissible free-user nudge — web's UpgradeCard, slimmed to one quiet row. */
@Composable
private fun UpgradeRow(onOpenPremium: () -> Unit, onDismiss: () -> Unit) {
    val colors = Stackd.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.border, RadiusMd)
            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                "Premium",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Unlimited history and advanced analytics, from ₹75/mo on annual.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
        TextAction("See plans", enabled = true, onClick = onOpenPremium)
        IconButton(onClick = onDismiss) {
            Icon(
                app.stackd.core.ui.StackdIcons.Close,
                contentDescription = "Dismiss Premium offer",
                tint = colors.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Prestige progress; when eligible, "Prestige now" opens the ascend ceremony dialog. */
@Composable
private fun PrestigeCard(
    p: app.stackd.data.progression.PrestigeStatus,
    ascending: Boolean,
    notice: String?,
    onAscend: () -> Unit,
) {
    val colors = Stackd.colors
    var confirming by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.border, RadiusMd)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PRESTIGE", style = MonoLabelSmall, color = colors.accent, modifier = Modifier.padding(end = 10.dp))
            Text(
                "P${p.level}",
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = app.stackd.core.theme.SerifFamily,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text("${p.lifetimeXp} / ${p.neededXp} XP", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).background(colors.textPrimary.copy(alpha = 0.06f), CircleShape)) {
            Box(
                Modifier
                    .fillMaxWidth((p.lifetimeXp.toFloat() / p.neededXp).coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(colors.accentDeep, CircleShape),
            )
        }
        if (p.canPrestige) {
            Spacer(Modifier.height(12.dp))
            // Ghost, not ember: Start focus is the screen's only filled button.
            GhostButton(text = "Prestige now", onClick = { confirming = true })
        }
        notice?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.accent)
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Ascend to Prestige ${p.level + 1}?") },
            text = { Text("Your XP total remains. Your streak resets to zero. A new ring joins your frame.") },
            confirmButton = {
                TextButton(
                    enabled = !ascending,
                    onClick = { onAscend(); confirming = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(if (ascending) "ASCENDING…" else "ASCEND") }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("CANCEL") }
            },
        )
    }
}

/** 48dp-tall text action (Regenerate / Retry / See all). */
@Composable
private fun TextAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = Stackd.colors
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .tappable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) colors.accent else colors.textMuted,
        )
    }
}

/**
 * Clickable with the app's spring press feedback in place of a ripple. Placed
 * before background/border in a chain so the whole surface scales, not just
 * its content.
 */
@Composable
internal fun Modifier.tappable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .pressFeedback(source)
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )
}

private fun shortDate(iso: String?): String {
    val ms = parseIsoMillis(iso) ?: return "—"
    return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.SHORT))
}

private fun isNew(iso: String?): Boolean =
    parseIsoMillis(iso)?.let { System.currentTimeMillis() - it < 86_400_000L } == true
