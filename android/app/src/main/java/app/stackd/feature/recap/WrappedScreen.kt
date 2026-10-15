package app.stackd.feature.recap

import app.stackd.core.ui.glassSurface

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import app.stackd.core.theme.SerifFamily
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.SectionLabel
import app.stackd.core.ui.SkeletonBlock
import androidx.compose.foundation.layout.Row
import app.stackd.data.recap.WrappedStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class WrappedUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val stats: WrappedStats? = null,
)

/** Stack Wrapped — web's `wrapped.tsx`. */
class WrappedViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(WrappedUiState())
    val state: StateFlow<WrappedUiState> = _state

    init {
        load()
    }

    private fun cacheKey(userId: String) = "wrapped:$userId"

    fun load() {
        val userId = container.auth.currentUserId ?: return
        // Stale-while-revalidate: seed from the last cached state so re-entry
        // shows data instantly instead of a spinner, then revalidate below.
        val cached: WrappedUiState? = container.cache.get(cacheKey(userId))
        _state.value = (cached ?: _state.value).copy(loading = cached == null, error = false)
        viewModelScope.launch {
            runCatching { container.recap.getWrapped(userId) }.fold(
                onSuccess = {
                    val fresh = WrappedUiState(loading = false, stats = it)
                    _state.value = fresh
                    container.cache.put(cacheKey(userId), fresh)
                },
                onFailure = { _state.value = _state.value.copy(loading = false, error = cached == null) },
            )
        }
    }
}

@Composable
fun WrappedRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: WrappedViewModel = viewModel(factory = stackdViewModel { WrappedViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    WrappedScreen(state = state, onRetry = vm::load, onBack = onBack, modifier = modifier)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WrappedScreen(
    state: WrappedUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Stackd.colors
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState()),
    ) {
        ResponsiveColumn {
            val s = state.stats
            app.stackd.core.ui.ScreenHeader(
                "STACK WRAPPED" + when {
                    s == null -> ""
                    s.rolling -> " · LAST 12 MONTHS"
                    else -> " · ${s.year}"
                },
                onBack,
            )
            Spacer(Modifier.height(16.dp))

            when {
                state.loading -> {
                    // Headline, sentence, then the stat-tile grid.
                    SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(52.dp))
                    Spacer(Modifier.height(8.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(0.3f).height(36.dp))
                    Spacer(Modifier.height(16.dp))
                    SkeletonBlock(Modifier.fillMaxWidth().height(14.dp))
                    Spacer(Modifier.height(8.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(0.8f).height(14.dp))
                    Spacer(Modifier.height(24.dp))
                    repeat(3) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(3) { SkeletonBlock(Modifier.weight(1f).height(64.dp), Radius2Xl) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                state.error || s == null -> {
                    Text(
                        "Couldn't load your Wrapped.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Retry", onClick = onRetry)
                }
                else -> {
                    Text(
                        "${s.totalHours} hours",
                        style = MaterialTheme.typography.displayMedium,
                        fontFamily = SerifFamily,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Normal,
                    )
                    Text(
                        "held.",
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = SerifFamily,
                        color = colors.accent,
                        fontWeight = FontWeight.Normal,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "You stacked ${s.totalSessions} ${if (s.totalSessions == 1) "session" else "sessions"}, earned ${s.totalXp} XP, and held " +
                            "the line best on ${s.topWeekday}s around " +
                            "${s.peakHour.toString().padStart(2, '0')}:00.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )

                    Spacer(Modifier.height(24.dp))
                    // Bento: XP at display scale beside three compact tiles, then pairs.
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(
                            modifier = Modifier
                                .weight(1.15f)
                                .fillMaxHeight()
                                .glassSurface(Radius2Xl)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("EARNED", style = MonoLabelSmall, color = colors.accent)
                            Column {
                                Text(
                                    s.totalXp.toString(),
                                    // Long values step down a size so they never clip.
                                    style = if (s.totalXp >= 100_000) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayMedium,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                )
                                Text("XP", style = MonoLabelSmall, color = colors.textMuted)
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile("SESSIONS", s.totalSessions.toString(), Modifier.fillMaxWidth())
                            StatTile("LONGEST SESSION", "${s.longestSessionMinutes} min", Modifier.fillMaxWidth())
                            StatTile(
                                "BEST STREAK",
                                "${s.bestStreak} ${if (s.bestStreak == 1) "day" else "days"}",
                                Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    listOf(
                        "UNBROKEN" to s.perfectSessions.toString(),
                        "FLOW STATES" to s.flowSessions.toString(),
                        "PEAK DAY" to s.topWeekday,
                        "TOP ALLY" to (s.topCollaborator?.name ?: "—"),
                        "PERCENTILE" to "Top ${maxOf(1, 100 - s.percentile)}%",
                    ).chunked(2).forEach { pair ->
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { (label, value) -> StatTile(label, value, Modifier.weight(1f).fillMaxHeight()) }
                        }
                    }

                    s.personality?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = SerifFamily,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Normal,
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                    SectionLabel("SHARE CARD")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Render your year as a 1080×1350 card and send it anywhere.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(16.dp))
                    EmberButton(
                        text = "Share Wrapped",
                        onClick = { WrappedCard.share(context, s) },
                    )
                }
            }

            Spacer(Modifier.height(56.dp))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = Stackd.colors
    Column(
        modifier = modifier
            .glassSurface(Radius2Xl)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, style = MonoLabelSmall, color = colors.textMuted, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}
