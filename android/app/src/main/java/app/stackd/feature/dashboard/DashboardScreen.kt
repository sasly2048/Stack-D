package app.stackd.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stackd.core.formatDuration
import app.stackd.core.formatHours
import app.stackd.core.parseIsoMillis
import app.stackd.core.stackdViewModel
import app.stackd.core.theme.MonoLabel
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.Radius2Xl
import app.stackd.core.theme.RadiusMd
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.ErrorBanner
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.SectionLabel
import androidx.compose.runtime.mutableStateOf
import app.stackd.core.ui.NavMenuSheet
import kotlinx.coroutines.launch
import app.stackd.data.room.FocusHistoryRow
import app.stackd.data.room.RoomRow
import app.stackd.feature.room.session.FocusScore
import kotlinx.coroutines.delay

/**
 * Analytics dashboard. Stateless in the render — the [DashboardViewModel] owns
 * the loads, and every navigation is a hoisted callback so this composable
 * never touches the nav graph directly.
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
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
                    val stamp = java.time.LocalDate.now().toString()
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
    // Scroll on the full-width outer box; content capped + centered inside so it
    // doesn't sprawl on tablets/foldables/landscape. Analytics uses the wider
    // measure since its tiles legitimately want more room.
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState()),
    ) {
      app.stackd.core.ui.ResponsiveColumn(
        maxContentWidth = app.stackd.core.ui.WIDE_MAX_CONTENT_WIDTH,
      ) {
        Greeting(state)
        Spacer(Modifier.height(24.dp))
        EmberButton(text = "New Session", onClick = onStart)
        Spacer(Modifier.height(12.dp))
        // The web's nav menu, folded into one sheet — the button stack was
        // four rows deep and still growing.
        var showMenu by remember { mutableStateOf(false) }
        GhostButton(text = "Menu", onClick = { showMenu = true })
        Spacer(Modifier.height(8.dp))
        GhostButton(text = "Export focus history (CSV)", onClick = onExportCsv)
        if (showMenu) {
            NavMenuSheet(onDismiss = { showMenu = false }, entries = menuEntries)
        }
        Spacer(Modifier.height(12.dp))
        state.reward?.let { reward ->
            DailyRewardCard(
                reward = reward,
                claiming = state.claiming,
                notice = state.claimNotice,
                onClaim = onClaimReward,
            )
            Spacer(Modifier.height(16.dp))
        }

        if (state.isPremium == false && !state.upgradeDismissed) {
            UpgradeCard(onOpenPremium = onOpenPremium, onDismiss = onDismissUpgrade)
            Spacer(Modifier.height(16.dp))
        }
        state.prestige?.let { PrestigeCard(it, state.ascending, state.prestigeNotice, onAscend) }

        // Atlas — the ambient companion. Shows a next-session recommendation
        // derived from the same history the ledger already loaded. Dismissible.
        if (!state.loading && !state.isEmpty) {
            if (!state.atlasDismissed) {
                AtlasCard(
                    // Prefer the LLM recommendation once it lands; until then (or
                    // if the AI backend is unreachable) show the local heuristic.
                    rec = state.aiRecommendation
                        ?: app.stackd.feature.insights.recommendNextSession(state.history),
                    onDismiss = onDismissAtlas,
                    loading = state.aiRecLoading,
                    error = state.aiRecError,
                    onRegenerate = onRegenRec,
                    onRetry = onRetryRec,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
        Spacer(Modifier.height(12.dp))

        when {
            state.loading -> {
                SectionLabel("LOADING")
                Spacer(Modifier.height(12.dp))
                Text(
                    "Reading your ledger…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
            }

            state.error -> {
                // Degrade intentionally: the user IS signed in (the header
                // greets them by name from the token), New Session above still
                // works, and the ledger just couldn't sync. A calm inline notice
                // reads as "temporarily unavailable", not "the app is broken".
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.textPrimary.copy(alpha = 0.03f), Radius2Xl)
                        .border(1.dp, colors.border, Radius2Xl)
                        .padding(20.dp),
                ) {
                    SectionLabel("LEDGER UNAVAILABLE")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your history couldn't load right now. You can still start " +
                            "a session — your stats will appear once it syncs.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(16.dp))
                    GhostButton(text = "Retry", onClick = onRetry)
                }
            }

            state.isEmpty && state.live.isEmpty() -> {
                EmptyLedger(onStart = onStart)
            }

            else -> {
                if (!state.isEmpty) {
                    StatTiles(state)
                    Spacer(Modifier.height(20.dp))
                }
                if (!state.isEmpty) {
                    InsightsCard(state.aiInsights, state.aiInsLoading, state.aiInsError, onRegenInsights)
                    Spacer(Modifier.height(20.dp))
                }
                if (state.live.isNotEmpty()) {
                    LiveNow(state.live, onOpenRoom)
                    Spacer(Modifier.height(20.dp))
                }
                if (state.myRooms.isNotEmpty() || state.myRoomsPage > 0) {
                    MyRooms(state, onOpenRoom, onMyRoomsPage)
                    Spacer(Modifier.height(20.dp))
                }
                if (!state.isEmpty) {
                    ActivityHeatmap(state.history)
                    Spacer(Modifier.height(20.dp))
                    SessionHistory(state.history, onOpenRoom)
                }
            }
        }
      }
    }
}

@Composable
private fun EmptyLedger(onStart: () -> Unit) {
    val colors = Stackd.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.textPrimary.copy(alpha = 0.03f), Radius2Xl)
            .border(1.dp, colors.border, Radius2Xl)
            .padding(24.dp),
    ) {
        SectionLabel("NO SESSIONS YET")
        Spacer(Modifier.height(12.dp))
        Text(
            "Your first session writes the first line",
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Nothing has been measured yet. Open a room, stack your phone and the " +
                "ledger starts filling itself.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
        )
        Spacer(Modifier.height(20.dp))
        EmberButton(text = "Start your first session", onClick = onStart)
    }
}

@Composable
private fun StatTiles(state: DashboardUiState) {
    val colors = Stackd.colors
    // Lifetime presence — the headline number, hours only.
    Tile {
        SectionLabel("LIFETIME_PRESENCE", color = colors.textMuted)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                formatHours(state.totalSeconds).replace("h", ""),
                style = MaterialTheme.typography.displayLarge,
                color = colors.textPrimary,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.size(8.dp))
            Text("HOURS", style = MonoLabel, color = colors.textMuted)
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Tile(modifier = Modifier.weight(1f)) {
            SectionLabel("LIFETIME_XP", color = colors.textMuted)
            Spacer(Modifier.height(8.dp))
            Text(
                state.lifetimeXp.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
        Tile(modifier = Modifier.weight(1f)) {
            SectionLabel("CURRENT_STREAK", color = colors.textMuted)
            Spacer(Modifier.height(8.dp))
            // Big number + small mono unit, as on web (the unit at headline size
            // out-weighed the XP tile beside it).
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${state.streak}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (state.streak == 1) "SESSION" else "SESSIONS",
                    style = MonoLabelSmall,
                    color = colors.textMuted,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    val tier = FocusScore.tierForScore(state.avgScore.toDouble())
    Tile {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                SectionLabel("AVG_SCORE", color = colors.textMuted)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        state.avgScore.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("/100", style = MonoLabelSmall, color = colors.textMuted)
                }
            }
            val grade = when {
                state.avgScore >= 95 -> "A+"
                state.avgScore >= 80 -> "A"
                state.avgScore >= 60 -> "B"
                else -> "C"
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .border(3.dp, Color(tier.hex), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(grade, style = MonoLabelSmall, color = Color(tier.hex))
            }
        }
    }
}

@Composable
private fun LiveNow(live: List<RoomRow>, onOpenRoom: (String) -> Unit) {
    val colors = Stackd.colors
    Tile {
        SectionLabel("LIVE_NOW", color = colors.textMuted)
        Spacer(Modifier.height(12.dp))
        live.forEach { room ->
            LiveSessionRow(room, onOpenRoom)
            Spacer(Modifier.height(8.dp))
        }
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
    Tile {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionLabel("MY_ROOMS", color = colors.textMuted)
            Text("PAGE ${state.myRoomsPage + 1}", style = MonoLabelSmall, color = colors.textMuted)
        }
        Spacer(Modifier.height(12.dp))
        if (state.myRoomsError) {
            Text("SIGNAL LOST", style = MonoLabelSmall, color = colors.textMuted)
            TextAction("Retry", enabled = true, onClick = { onPage(state.myRoomsPage) })
            Spacer(Modifier.height(8.dp))
        }
        state.myRooms.forEach { room ->
            val elapsed = remember(room.startedAt, room.endedAt) {
                val s = parseIsoMillis(room.startedAt)
                val e = parseIsoMillis(room.endedAt)
                if (s != null && e != null) ((e - s) / 1000).coerceAtLeast(0).toInt() else null
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { onOpenRoom(room.code) }
                    .background(colors.textPrimary.copy(alpha = 0.03f), RadiusMd)
                    .border(1.dp, colors.border, RadiusMd)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(room.code, style = MonoLabelSmall, color = colors.textPrimary)
                Text(
                    "${if (room.isHost(state.meId)) "HOST" else "GUEST"} · ${room.status.uppercase()}",
                    style = MonoLabelSmall,
                    color = if (room.statusEnum == app.stackd.data.room.RoomStatus.ACTIVE) {
                        colors.live
                    } else colors.textMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatDuration(elapsed ?: room.targetDurationSeconds.toInt()),
                    style = MonoLabelSmall,
                    color = colors.textMuted,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        if (state.myRoomsPage > 0 || state.myRoomsHasMore) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextAction("← Prev", enabled = state.myRoomsPage > 0, onClick = { onPage(state.myRoomsPage - 1) })
                TextAction("Next →", enabled = state.myRoomsHasMore, onClick = { onPage(state.myRoomsPage + 1) })
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
            .clickable { onOpenRoom(room.code) }
            .background(colors.textPrimary.copy(alpha = 0.03f), RadiusMd)
            .border(1.dp, colors.border, RadiusMd)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).background(colors.live, CircleShape))
        Text(room.code, style = MonoLabelSmall, color = colors.textMuted)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .background(colors.textPrimary.copy(alpha = 0.05f), RadiusMd),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(pct)
                    .height(4.dp)
                    .background(colors.live, RadiusMd),
            )
        }
        Text(formatDuration(elapsed), style = MonoLabelSmall, color = colors.textPrimary)
        Text("LIVE", style = MonoLabelSmall, color = colors.live)
    }
}

@Composable
private fun SessionHistory(history: List<FocusHistoryRow>, onOpenRoom: (String) -> Unit) {
    val colors = Stackd.colors
    Tile {
        SectionLabel("SESSION_HISTORY", color = colors.textMuted)
        Spacer(Modifier.height(12.dp))
        // Two-line rows: web's 6-column grid can't fit a phone at label size —
        // it wrapped tier names mid-word and let columns drift row to row.
        history.forEachIndexed { i, h ->
            val tier = FocusScore.tierForScore(h.score.toDouble())
            val tint = Color(tier.hex)
            val code = h.room?.code
            if (i > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = 0.5f)))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .then(
                        if (code != null) {
                            Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button) { onOpenRoom(code) }
                        } else Modifier,
                    )
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(code ?: "—", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, fontFamily = app.stackd.core.theme.MonoFamily)
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
                    Text(tier.label.uppercase(), style = MonoLabelSmall, color = tint, maxLines = 1, softWrap = false)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${shortDate(h.createdAt)} · ${formatDuration(h.durationSeconds)} · +${h.xp} XP",
                        style = MonoLabelSmall,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Text(
                    h.score.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = tint,
                    fontWeight = FontWeight.Bold,
                    fontFamily = app.stackd.core.theme.MonoFamily,
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
            .background(colors.textPrimary.copy(alpha = 0.04f), Radius2Xl)
            .border(1.dp, colors.border, Radius2Xl)
            .padding(20.dp),
    ) { content() }
}

/**
 * Atlas companion card — the Android counterpart to the web's `AtlasWhisper`.
 * A dismissible presence surfacing the next-session recommendation. Ember-
 * bordered to read as ambient guidance, not a hard control.
 */
@Composable
private fun AtlasCard(
    rec: app.stackd.feature.insights.SessionRecommendation,
    onDismiss: () -> Unit,
    loading: Boolean,
    error: Boolean,
    onRegenerate: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = Stackd.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.accent.copy(alpha = 0.06f), Radius2Xl)
            .border(1.dp, colors.accent.copy(alpha = 0.25f), Radius2Xl)
            .padding(20.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .heightIn(min = 48.dp)
                .widthIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) { Text("✕", style = MonoLabelSmall, color = colors.textMuted) }
        Column {
            Text("ATLAS", style = MonoLabelSmall, color = colors.accent)
            Spacer(Modifier.height(8.dp))
            Text(
                "Atlas here.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                rec.topic,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                rec.rationale,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "${rec.durationMinutes} MIN · CONFIDENCE ${rec.confidence.uppercase()}",
                style = MonoLabelSmall,
                color = colors.accent,
            )
            Spacer(Modifier.height(4.dp))
            if (error) {
                Text("SIGNAL LOST", style = MonoLabelSmall, color = colors.textMuted)
                TextAction("Retry", enabled = !loading, onClick = onRetry)
            } else {
                TextAction(if (loading) "Thinking…" else "Regenerate →", enabled = !loading, onClick = onRegenerate)
            }
        }
    }
}

/**
 * LLM-written ledger insights. Mirrors the web dashboard's insights card:
 * a headline over a few short paragraphs, with Regenerate / Retry and a
 * loading state instead of hiding while the model works.
 */
@Composable
private fun InsightsCard(
    insights: app.stackd.data.ai.DashboardInsights?,
    loading: Boolean,
    error: Boolean,
    onRegenerate: () -> Unit,
) {
    val colors = Stackd.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.textPrimary.copy(alpha = 0.03f), Radius2Xl)
            .border(1.dp, colors.border, Radius2Xl)
            .padding(20.dp),
    ) {
        Text("LEDGER INSIGHTS", style = MonoLabelSmall, color = colors.accent)
        when {
            loading && insights == null -> {
                Spacer(Modifier.height(10.dp))
                Text("Reading…", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            }
            insights == null -> {
                Spacer(Modifier.height(10.dp))
                Text("SIGNAL LOST", style = MonoLabelSmall, color = colors.textMuted)
                TextAction("Retry", enabled = !loading, onClick = onRegenerate)
            }
            else -> {
                if (insights.headline.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        insights.headline,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                insights.paragraphs.forEach { para ->
                    Spacer(Modifier.height(10.dp))
                    Text(para, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                }
                if (insights.basedOnSessions > 0) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "BASED ON ${insights.basedOnSessions} SESSIONS",
                        style = MonoLabelSmall,
                        color = colors.accent,
                    )
                }
                TextAction(if (loading) "Reading…" else "Regenerate →", enabled = !loading, onClick = onRegenerate)
            }
        }
    }
}

/** 48dp-tall mono text action (Regenerate / Retry). */
@Composable
private fun TextAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = Stackd.colors
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text.uppercase(),
            style = MonoLabelSmall,
            color = if (enabled) colors.textPrimary else colors.textMuted,
        )
    }
}

private fun shortDate(iso: String?): String {
    val ms = parseIsoMillis(iso) ?: return "—"
    return java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
        .format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.SHORT))
}

private fun isNew(iso: String?): Boolean =
    parseIsoMillis(iso)?.let { System.currentTimeMillis() - it < 86_400_000L } == true

/** Time-of-day greeting — web's DynamicGreeting: glyph, "Good morning, {name}", stat lines. */
@Composable
private fun Greeting(state: DashboardUiState) {
    val colors = Stackd.colors
    val now = java.time.LocalTime.now()
    val (glyph, label) = when {
        now.hour < 5 -> "🌙" to "Late night"
        now.hour < 12 -> "☀️" to "Good morning"
        now.hour < 17 -> "🌤️" to "Good afternoon"
        now.hour < 21 -> "🌇" to "Good evening"
        else -> "🌌" to "Good night"
    }
    val yesterday = remember(state.history) {
        val zone = java.time.ZoneId.systemDefault()
        val day = java.time.LocalDate.now(zone).minusDays(1)
        state.history.filter {
            parseIsoMillis(it.createdAt)?.let { ms ->
                java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() == day
            } == true
        }.sumOf { it.durationSeconds }
    }
    Text("$glyph ${label.uppercase()} · ANALYTICS", style = MonoLabel, color = colors.textMuted)
    Spacer(Modifier.height(8.dp))
    Text(
        "$label, ${state.name}.",
        style = MaterialTheme.typography.displaySmall,
        color = colors.textPrimary,
        fontWeight = FontWeight.ExtraBold,
    )
    val g = state.greeting
    val lines = buildList {
        if (yesterday > 0) add("You studied ${Math.round(yesterday / 360.0) / 10.0}h yesterday.")
        if (g.friendsOnline > 0) {
            add("${g.friendsOnline} ${if (g.friendsOnline == 1) "friend is" else "friends are"} already focusing.")
        }
        if (g.challengeProgress > 0f && g.challengeProgress < 1f) {
            add("Today's challenge is ${Math.round(g.challengeProgress * 100)}% done.")
        }
        if (state.streak > 0) add("Current streak: 🔥 ${state.streak}")
    }
    if (lines.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        lines.forEach {
            Text("· $it", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
        }
    }
}

/** Dismissible free-user nudge — web's UpgradeCard. */
@Composable
private fun UpgradeCard(onOpenPremium: () -> Unit, onDismiss: () -> Unit) {
    val colors = Stackd.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.accent.copy(alpha = 0.04f), Radius2Xl)
            .border(1.dp, colors.accent.copy(alpha = 0.25f), Radius2Xl)
            .padding(20.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .heightIn(min = 48.dp)
                .widthIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) { Text("✕", style = MonoLabelSmall, color = colors.textMuted) }
        Column {
            Text("PREMIUM", style = MonoLabelSmall, color = colors.accent)
            Spacer(Modifier.height(8.dp))
            Text(
                "See the full picture",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Unlimited history, advanced analytics, and more — from ₹75/mo on annual.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
            Spacer(Modifier.height(12.dp))
            GhostButton(text = "See plans", onClick = onOpenPremium)
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
    Tile {
        SectionLabel("PRESTIGE", color = colors.accent)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("P${p.level}", style = MaterialTheme.typography.headlineLarge, color = colors.textPrimary, fontWeight = FontWeight.Bold)
            Text("${p.lifetimeXp} / ${p.neededXp} XP", style = MonoLabelSmall, color = colors.textMuted)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).background(colors.textPrimary.copy(alpha = 0.06f), RadiusMd)) {
            Box(
                Modifier
                    .fillMaxWidth((p.lifetimeXp.toFloat() / p.neededXp).coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(colors.accent, RadiusMd),
            )
        }
        Spacer(Modifier.height(10.dp))
        if (p.canPrestige) {
            EmberButton(text = "Prestige now", onClick = { confirming = true })
        } else {
            Text("Reach ${p.neededXp} XP to ascend.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        notice?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MonoLabelSmall, color = colors.accent)
        }
    }
    Spacer(Modifier.height(16.dp))
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
