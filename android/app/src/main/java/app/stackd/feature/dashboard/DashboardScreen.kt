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
    vm: DashboardViewModel = viewModel(
        factory = stackdViewModel { DashboardViewModel(it.auth, it.profiles) },
    ),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    DashboardScreen(
        state = state,
        onStart = onStart,
        onOpenRoom = onOpenRoom,
        menuEntries = menuEntries,
        onRetry = vm::load,
        onClaimReward = vm::claimReward,
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
        Text(
            "ANALYTICS / ${state.name.uppercase()}",
            style = MonoLabel,
            color = colors.textMuted,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                "Your discipline.",
                style = MaterialTheme.typography.displaySmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Spacer(Modifier.height(24.dp))
        EmberButton(text = "New Session", onClick = onStart)
        Spacer(Modifier.height(12.dp))
        // The web's nav menu, folded into one sheet — the button stack was
        // four rows deep and still growing.
        var showMenu by remember { mutableStateOf(false) }
        GhostButton(text = "Menu", onClick = { showMenu = true })
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
                ErrorBanner("Couldn't load your analytics.", onRetry = onRetry)
            }

            state.isEmpty && state.live.isEmpty() -> {
                EmptyLedger(onStart = onStart)
            }

            else -> {
                if (!state.isEmpty) {
                    StatTiles(state)
                    Spacer(Modifier.height(20.dp))
                }
                if (state.live.isNotEmpty()) {
                    LiveNow(state.live, onOpenRoom)
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
            Text(
                "${state.streak} ${if (state.streak == 1) "Session" else "Sessions"}",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
            )
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
        history.forEach { h ->
            val tier = FocusScore.tierForScore(h.score.toDouble())
            val code = h.room?.code
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (code != null) Modifier.clickable { onOpenRoom(code) } else Modifier)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    code ?: "—",
                    style = MonoLabelSmall,
                    color = colors.textMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatDuration(h.durationSeconds),
                    style = MonoLabelSmall,
                    color = colors.textPrimary,
                )
                Text(
                    h.score.toString(),
                    style = MonoLabelSmall,
                    color = Color(tier.hex),
                )
                Text("+${h.xp}", style = MonoLabelSmall, color = colors.textPrimary)
                Text(
                    tier.label.uppercase(),
                    style = MonoLabelSmall,
                    color = Color(tier.hex),
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
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
