package app.stackd.feature.dashboard

import app.stackd.core.ui.pageGlow

import app.stackd.core.ui.glassSurface

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
import app.stackd.core.theme.Radius2Xl
import app.stackd.core.theme.RadiusMd
import app.stackd.core.theme.Stackd
import app.stackd.core.feedback.Sfx
import app.stackd.core.ui.Avatar
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
internal const val DAILY_GOAL_MIN = 60

/** One-tap rituals under the hero: label (also the prefilled title) to minutes. */
private val RITUALS = listOf("Quick focus" to 25, "Deep work" to 90, "Dinner" to 60)

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
    /** Opens Start pre-filled with a ritual's length and name. */
    onQuickStart: (minutes: Int, title: String) -> Unit = { _, _ -> },
    /** Opens the Premium screen from the upgrade card; StackdNavHost must wire it. */
    onOpenPremium: () -> Unit = {},
    vm: DashboardViewModel = viewModel(
        factory = stackdViewModel {
            DashboardViewModel(
                it.auth, it.profiles, it.rooms, it.ai, it.cache, it.premium,
                app.stackd.data.progression.PrestigeRepository(it.client), it.client,
                it.appContextForWork.getSharedPreferences("dashboard_prefs", 0),
                it.snapshots,
                it.feed,
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
        onQuickStart = onQuickStart,
        onOpenRoom = onOpenRoom,
        menuEntries = menuEntries,
        onRetry = vm::load,
        onClaimReward = vm::claimReward,
        onOpenPremium = onOpenPremium,
        onRegenRec = { vm.fetchAiRecommendation(fresh = true) },
        onRetryRec = { vm.fetchAiRecommendation(fresh = true) },
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
    onDismissAtlas: () -> Unit = {},
    onDismissUpgrade: () -> Unit = {},
    onAscend: () -> Unit = {},
    onMyRoomsPage: (Int) -> Unit = {},
    onQuickStart: (minutes: Int, title: String) -> Unit = { _, _ -> },
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
            // Landing-page light: one ember glow washing the whole screen from
            // the top, fading slowly to black. Fixed behind the scroll.
            .pageGlow()
            .verticalScroll(rememberScrollState()),
    ) {
        ResponsiveColumn(maxContentWidth = WIDE_MAX_CONTENT_WIDTH) {
            HomeHeroV3(state = state, onStart = onStart, onMore = { showMenu = true })
            Spacer(Modifier.height(16.dp))
            RitualChips(onQuickStart)

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

            // Home reads in time order: now (above) -> recent -> long-term (tail).
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
                    YourPeople(state.friendsFocusing)
                }

                else -> {
                    if (state.live.isNotEmpty()) {
                        Section("Live now") { LiveNow(state.live, onOpenRoom) }
                    }
                    // Stats and insights live on the Progress tab; Home is a launcher.
                    YourPeople(state.friendsFocusing)
                    // Home shows only rooms you can still walk into; finished
                    // ones are history and live under "See all".
                    val openRooms = remember(state.myRooms, state.live) {
                        val liveCodes = state.live.map { it.code }.toSet()
                        state.myRooms.filter {
                            it.statusEnum == app.stackd.data.room.RoomStatus.LOBBY ||
                                it.statusEnum == app.stackd.data.room.RoomStatus.ACTIVE
                        }.filterNot { it.code in liveCodes }
                    }
                    if (openRooms.isNotEmpty() || state.myRoomsError) {
                        Section("Open rooms") { MyRooms(state, openRooms, onOpenRoom, onMyRoomsPage) }
                    }
                    if (!state.isEmpty) {
                        val weekEmpty = remember(state.history) {
                            weekMinutes(state.history, LocalDate.now(), ZoneId.systemDefault()).sum() == 0
                        }
                        Section("This week") {
                            if (weekEmpty) {
                                // Seven empty bars read as failure; one calm line reads as a start.
                                Text(
                                    "Your week is empty. Your first Stack starts it.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textMuted,
                                )
                            } else {
                                Tile { WeekBars(state.history) }
                            }
                        }
                        // Sub-minute test sessions are noise on Home; Timeline keeps them.
                        val last = remember(state.history) {
                            state.history.firstOrNull { it.durationSeconds >= 60 }
                        }
                        if (last != null) {
                            Section(
                                "Last stack",
                                action = openTimeline?.let { "See all" to it },
                            ) { LastStack(last, onOpenRoom) }
                        }
                    }
                }
            }

            // Quiet long-term tail: reward cycle, prestige, then the upsell.
            if (!state.loading) {
                TodayStrip(
                    reward = state.reward,
                    claiming = state.claiming,
                    notice = state.claimNotice,
                    challengeProgress = state.greeting.challengeProgress,
                    onClaim = onClaimReward,
                )
                Spacer(Modifier.height(12.dp))
                state.prestige?.let { PrestigeCard(it, state.ascending, state.prestigeNotice, onAscend) }
                if (state.isPremium == false && !state.upgradeDismissed) {
                    Spacer(Modifier.height(12.dp))
                    UpgradeRow(onOpenPremium = onOpenPremium, onDismiss = onDismissUpgrade)
                }
            }
        }
    }
    if (showMenu) {
        // CSV export lives in the sheet: a rare action doesn't earn a
        // full-width button above the fold.
        NavMenuSheet(
            onDismiss = { showMenu = false },
            entries = menuEntries + ("Export focus history (CSV)" to onExportCsv),
            statuses = rememberMenuStatuses(state),
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
    // Ticks so the greeting clock never goes stale while Home stays open.
    val now by androidx.compose.runtime.produceState(LocalTime.now()) {
        while (true) {
            kotlinx.coroutines.delay(15_000)
            value = LocalTime.now()
        }
    }
    val hour = now.hour
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
        // Only a meaningful amount; seconds of focus read as "0.0h" (a failure).
        if (yesterdaySec >= 300) {
            val m = (yesterdaySec / 60).toInt()
            add(if (m >= 60) "${m / 60}h ${m % 60}m yesterday" else "${m}m yesterday")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(colors.accent.copy(alpha = 0.09f), colors.surface)),
                Radius2Xl,
            )
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                val clock = now.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
                Text("$dayPart · ${clock.lowercase()}", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
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
                        tint = if (state.streak > 0) colors.accent else colors.accent.copy(alpha = 0.45f),
                        modifier = Modifier.size(26.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    // A zero is a failure signal; a 0-streak is really an
                    // invitation, so it gets words instead of a number.
                    Text(
                        if (state.streak > 0) "${state.streak}" else "Day one",
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    if (state.streak > 0) "session streak" else "one session starts a streak",
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
            EmberButton(text = "Start a Stack", onClick = onStart)
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
internal fun focusSecondsOn(history: List<FocusHistoryRow>, day: LocalDate, zone: ZoneId): Int =
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
                if (error) "Suggested" else "Suggested by Atlas",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
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
                humanRationale(rec.rationale),
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
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            app.stackd.core.ui.StackArt(size = 160.dp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Your first session writes the first line",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        // No second button here: Start focus above is the one way in.
        Text(
            "Nothing has been measured yet. Tap Start a Stack, stack your phone and " +
                "your stats start filling in here.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
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
    rooms: List<app.stackd.data.room.RoomListItem>,
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
        rooms.forEach { room ->
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
                    .glassSurface(RadiusMd)
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

/** The most recent stack as one compact row; Timeline ("See all") owns the list. */
@Composable
private fun LastStack(h: FocusHistoryRow, onOpenRoom: (String) -> Unit) {
    val colors = Stackd.colors
    val code = h.room?.code
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (code != null) Modifier.tappable { onOpenRoom(code) } else Modifier)
            .glassSurface(RadiusMd)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                FocusScore.tierForScore(h.score.toDouble()).label,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${minutesLabel(h.durationSeconds / 60)} · ${h.score}",
                style = MaterialTheme.typography.bodyMedium,
                color = scoreColor(h.score.toDouble()),
            )
        }
        Text(shortDate(h.createdAt), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
    }
}

/**
 * Horizontally scrolling ritual tiles (name over duration); each opens Start
 * pre-filled. The row fades out at its right edge so a clipped tile reads as
 * "more this way", not as a layout bug.
 */
@Composable
private fun RitualChips(onQuickStart: (minutes: Int, title: String) -> Unit) {
    val colors = Stackd.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Offscreen + DstOut: a true alpha fade, so the page glow shows through.
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                val fade = 48.dp.toPx()
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.Black),
                        startX = size.width - fade,
                        endX = size.width,
                    ),
                    topLeft = Offset(size.width - fade, 0f),
                    size = Size(fade, size.height),
                    blendMode = BlendMode.DstOut,
                )
            }
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RITUALS.forEach { (label, minutes) ->
            val source = remember { MutableInteractionSource() }
            Column(
                modifier = Modifier
                    .pressFeedback(source, sound = Sfx.Kind.SELECT)
                    .clickable(
                        interactionSource = source,
                        indication = null,
                        role = Role.Button,
                        onClick = { onQuickStart(minutes, label) },
                    )
                    .background(colors.textPrimary.copy(alpha = 0.05f), RadiusMd)
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                Text(minutesLabel(minutes), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
        }
        // Lets the last tile scroll clear of the fade.
        Spacer(Modifier.width(32.dp))
    }
}

/** Friends focusing right now; renders nothing when nobody is. */
@Composable
private fun YourPeople(friends: List<app.stackd.data.social.FriendPresence>) {
    if (friends.isEmpty()) return
    val colors = Stackd.colors
    val names = friends.mapNotNull { f -> f.displayName?.substringBefore(' ')?.takeIf { it.isNotBlank() } }
    Section("Your people") {
        Row(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Overlapped stack; the background ring separates neighbours.
            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                friends.take(5).forEach { f ->
                    Avatar(
                        url = f.avatarUrl,
                        name = f.displayName,
                        size = 36.dp,
                        modifier = Modifier
                            .border(2.dp, colors.accent, CircleShape)
                            .background(colors.background, CircleShape)
                            .padding(2.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (friends.size == 1) "1 friend is stacking now" else "${friends.size} friends are stacking now",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                if (names.isNotEmpty()) {
                    Text(
                        names.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
            .glassSurface(Radius2Xl)
            .padding(20.dp),
    ) { content() }
}

/** Dismissible free-user nudge — web's UpgradeCard, slimmed to one quiet row. */
@Composable
private fun UpgradeRow(onOpenPremium: () -> Unit, onDismiss: () -> Unit) {
    val colors = Stackd.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Prestige", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = colors.accent, modifier = Modifier.padding(end = 10.dp))
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
                ) { Text(if (ascending) "Ascending…" else "Ascend", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
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
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) colors.accent else colors.textMuted,
        )
    }
}

/** Score tint: ember for strong, primary for middling, muted for low. No red, no cyan. */
@Composable
private fun scoreColor(score: Double): Color {
    val colors = Stackd.colors
    return when {
        score >= 80 -> colors.accent
        score >= 40 -> colors.textPrimary
        else -> colors.textMuted
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

/** Human date: "Sep 9", with the year only when it isn't this year. */
private fun shortDate(iso: String?): String {
    val ms = parseIsoMillis(iso) ?: return "—"
    val day = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    val pattern = if (day.year == LocalDate.now().year) "MMM d" else "MMM d, yyyy"
    return day.format(java.time.format.DateTimeFormatter.ofPattern(pattern))
}

/** Focus time in Home's units: "18m" under an hour, "1h 20m" above. */
private fun minutesLabel(m: Int): String = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"

/**
 * Atlas's local heuristic explains itself in numbers ("Averaging 18/100 across
 * 13 sessions at 1 min. Shorter, cleaner runs first."). Say it like a person;
 * anything else (e.g. the LLM's own sentence) passes through untouched.
 */
internal fun humanRationale(text: String): String {
    val score = Regex("""Averaging (\d+)/100""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: return text
    return if (score >= 80) {
        "You've been holding focus well. You're ready to go a little longer."
    } else {
        "Your recent sessions hold better when they're short and clean."
    }
}
