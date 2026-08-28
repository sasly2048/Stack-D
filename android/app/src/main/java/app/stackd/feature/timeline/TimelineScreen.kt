package app.stackd.feature.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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
import app.stackd.core.theme.RadiusMd
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.SectionLabel
import app.stackd.data.timeline.REACTION_PICKER
import app.stackd.data.timeline.Reaction
import app.stackd.data.timeline.TimelineSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val PAGE = 20

data class TimelineUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val items: List<TimelineSession> = emptyList(),
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val insight: ProactiveInsight? = null,
)

/**
 * Session record — web's `timeline.tsx`.
 *
 * Cursor pagination on `created_at` rather than offset: a session finalized
 * while the user scrolls would shift every offset window and duplicate a row.
 */
class TimelineViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(TimelineUiState())
    val state: StateFlow<TimelineUiState> = _state

    init {
        load()
    }

    fun load() {
        val userId = container.auth.currentUserId ?: return
        _state.value = _state.value.copy(loading = true, error = false)
        viewModelScope.launch {
            runCatching {
                val rows = container.timeline.listTimeline(userId, PAGE)
                // Insights read the same table the timeline does, just a wider
                // window; a failure there must not take the record down with it.
                val since = Instant.now().minusSeconds(21 * 86_400L).toString()
                val history = runCatching {
                    container.profiles.historySince(userId, since, 200)
                }.getOrDefault(emptyList())
                rows to proactiveInsights(history)
            }.fold(
                onSuccess = { (rows, insight) ->
                    _state.value = TimelineUiState(
                        loading = false,
                        items = rows,
                        hasMore = rows.size == PAGE,
                        insight = insight,
                    )
                },
                onFailure = { _state.value = _state.value.copy(loading = false, error = true) },
            )
        }
    }

    fun loadMore() {
        val userId = container.auth.currentUserId ?: return
        val s = _state.value
        if (s.loadingMore || !s.hasMore || s.items.isEmpty()) return
        _state.value = s.copy(loadingMore = true)
        viewModelScope.launch {
            val before = _state.value.items.last().createdAt
            runCatching { container.timeline.listTimeline(userId, PAGE, before) }.fold(
                onSuccess = { more ->
                    _state.value = _state.value.copy(
                        items = _state.value.items + more,
                        hasMore = more.size == PAGE,
                        loadingMore = false,
                    )
                },
                onFailure = { _state.value = _state.value.copy(loadingMore = false) },
            )
        }
    }

    /**
     * Optimistic toggle. The server call is the source of truth, so a failure
     * puts the original bucket back rather than leaving a phantom reaction.
     */
    fun toggleReaction(sessionId: String, emoji: String) {
        val userId = container.auth.currentUserId ?: return
        val before = _state.value.items
        _state.value = _state.value.copy(items = before.map { s ->
            if (s.id != sessionId) s else s.copy(reactions = applyToggle(s.reactions, emoji))
        })
        viewModelScope.launch {
            runCatching { container.timeline.toggleReaction(userId, sessionId, emoji) }
                .onFailure { _state.value = _state.value.copy(items = before) }
        }
    }
}

/** Pure so the optimistic path and its rollback are both testable. */
internal fun applyToggle(reactions: List<Reaction>, emoji: String): List<Reaction> {
    val existing = reactions.firstOrNull { it.emoji == emoji }
        ?: return reactions + Reaction(emoji, 1, mine = true)
    val next = existing.copy(
        mine = !existing.mine,
        count = existing.count + if (existing.mine) -1 else 1,
    )
    return if (next.count <= 0) reactions - existing
    else reactions.map { if (it.emoji == emoji) next else it }
}

@Composable
fun TimelineRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: TimelineViewModel = viewModel(factory = stackdViewModel { TimelineViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    TimelineScreen(
        state = state,
        onRetry = vm::load,
        onLoadMore = vm::loadMore,
        onReact = vm::toggleReaction,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun TimelineScreen(
    state: TimelineUiState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onReact: (String, String) -> Unit,
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
            Text("RECORD / SESSIONS", style = MonoLabel, color = colors.textMuted)
            Spacer(Modifier.height(16.dp))
            SectionLabel("TIMELINE")
            Spacer(Modifier.height(16.dp))

            state.insight?.let {
                ProactiveCard(it)
                Spacer(Modifier.height(20.dp))
            }

            when {
                state.loading -> Text(
                    "Loading your timeline…",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                )
                state.error -> {
                    Text(
                        "Couldn't load your timeline.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Retry", onClick = onRetry)
                }
                state.items.isEmpty() -> Text(
                    "No sessions yet. Start one to write your first line.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                )
                else -> {
                    state.items.forEach { s -> SessionCard(s, onReact) }
                    if (state.hasMore) {
                        Spacer(Modifier.height(12.dp))
                        GhostButton(
                            text = if (state.loadingMore) "Loading…" else "Load older",
                            onClick = onLoadMore,
                            enabled = !state.loadingMore,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            GhostButton(text = "Back", onClick = onBack)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SessionCard(s: TimelineSession, onReact: (String, String) -> Unit) {
    val colors = Stackd.colors
    val tint = tierColor(s.tier)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(colors.textPrimary.copy(alpha = 0.02f), Radius2Xl)
            .border(1.dp, tint.copy(alpha = 0.35f), Radius2Xl)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(fmtDate(s.createdAt), style = MonoLabelSmall, color = colors.textMuted)
            Text(s.tier.uppercase(), style = MonoLabelSmall, color = tint)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "${s.score}/100",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                fmtDuration(s.durationSeconds),
                style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
            )
            Text(
                "+${s.xpEarned} XP",
                style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
            )
            if (s.breachesCount > 0) {
                Text(
                    "${s.breachesCount} breach${if (s.breachesCount > 1) "es" else ""}",
                    style = MaterialTheme.typography.bodyMedium, color = colors.breach,
                )
            }
        }
        s.notes?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                "“$it”",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                fontStyle = FontStyle.Italic,
            )
        }
        if (s.tags.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            TagRow(s.tags)
        }
        Spacer(Modifier.height(12.dp))
        ReactionBar(s.reactions) { emoji -> onReact(s.id, emoji) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagRow(tags: List<String>) {
    val colors = Stackd.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEach { t ->
            Text(
                "#$t",
                style = MonoLabelSmall,
                color = colors.textMuted,
                modifier = Modifier
                    .background(colors.textPrimary.copy(alpha = 0.05f), RadiusMd)
                    .border(1.dp, colors.border, RadiusMd)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** The web's `SessionReactionBar`: current buckets, then a `+` picker. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReactionBar(reactions: List<Reaction>, onReact: (String) -> Unit) {
    val colors = Stackd.colors
    var picking by remember { mutableStateOf(false) }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        reactions.forEach { r ->
            Text(
                "${r.emoji} ${r.count}",
                style = MonoLabelSmall,
                color = if (r.mine) colors.accent else colors.textMuted,
                modifier = Modifier
                    .clickable { onReact(r.emoji) }
                    .background(
                        if (r.mine) colors.accent.copy(alpha = 0.1f)
                        else colors.textPrimary.copy(alpha = 0.05f),
                        CircleShape,
                    )
                    .border(
                        1.dp,
                        if (r.mine) colors.accent.copy(alpha = 0.6f) else colors.border,
                        CircleShape,
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Text(
            if (picking) "×" else "+",
            style = MonoLabelSmall,
            color = colors.textMuted,
            modifier = Modifier
                .clickable { picking = !picking }
                .background(colors.textPrimary.copy(alpha = 0.05f), CircleShape)
                .border(1.dp, colors.border, CircleShape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
    if (picking) {
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            REACTION_PICKER.forEach { e ->
                Text(
                    e,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .clickable {
                            picking = false
                            onReact(e)
                        }
                        .background(colors.textPrimary.copy(alpha = 0.05f), RadiusMd)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ProactiveCard(ai: ProactiveInsight) {
    val colors = Stackd.colors
    val riskColor = when (ai.burnout.risk) {
        "high" -> colors.breach
        "medium" -> colors.accent
        else -> colors.textMuted
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.textPrimary.copy(alpha = 0.02f), Radius2Xl)
            .border(1.dp, colors.border, Radius2Xl)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text("SMART SCHEDULE", style = MonoLabelSmall, color = colors.textMuted)
            Spacer(Modifier.height(4.dp))
            if (ai.smartSchedule != null) {
                Text(
                    ai.smartSchedule.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.accent,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    ai.smartSchedule.rationale,
                    style = MaterialTheme.typography.bodySmall, color = colors.textMuted,
                )
            } else {
                Text(
                    "Need more sessions to pattern-match.",
                    style = MaterialTheme.typography.bodySmall, color = colors.textMuted,
                )
            }
        }
        Column {
            Text("FOCUS PREDICTION", style = MonoLabelSmall, color = colors.textMuted)
            Spacer(Modifier.height(4.dp))
            Text(
                "${ai.focusPrediction.nextScore}/100",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                ai.focusPrediction.note,
                style = MaterialTheme.typography.bodySmall, color = colors.textMuted,
            )
            Text(
                "CONFIDENCE · ${ai.focusPrediction.confidence.uppercase()}",
                style = MonoLabelSmall, color = colors.textMuted,
            )
        }
        Column {
            Text(
                "BURNOUT · ${ai.burnout.risk.uppercase()}",
                style = MonoLabelSmall, color = riskColor,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                ai.burnout.recommendation,
                style = MaterialTheme.typography.bodySmall, color = colors.textMuted,
            )
            ai.burnout.signals.forEach {
                Text("· $it", style = MonoLabelSmall, color = colors.textMuted)
            }
        }
    }
}

/* -------------------------------- helpers --------------------------------- */

@Composable
private fun tierColor(tier: String) = with(Stackd.colors) {
    when (tier) {
        "flow" -> accent
        "pristine" -> textPrimary
        "compromised" -> breach
        else -> textMuted
    }
}

private val DATE_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault())

private fun fmtDate(iso: String): String =
    parseIsoMillis(iso)?.let { DATE_FMT.format(Instant.ofEpochMilli(it)) } ?: ""

/** `Nm` under an hour, `Nh Nm` above — the web's `fmtDuration`. */
internal fun fmtDuration(seconds: Int): String {
    val m = seconds / 60
    return if (m < 60) "${m}m" else "${m / 60}h ${m % 60}m"
}
