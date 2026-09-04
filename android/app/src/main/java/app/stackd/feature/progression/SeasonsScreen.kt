package app.stackd.feature.progression

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.SectionLabel
import app.stackd.data.progression.Season
import app.stackd.data.progression.SeasonStanding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SeasonsUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val season: Season? = null,
    val standings: List<SeasonStanding> = emptyList(),
    val joining: Boolean = false,
    val meId: String? = null,
) {
    val mine: SeasonStanding? get() = standings.firstOrNull { it.userId == meId }
}

/** Seasonal competition — web's `seasons.tsx` over `getActiveSeason`. */
class SeasonsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(SeasonsUiState())
    val state: StateFlow<SeasonsUiState> = _state

    init {
        load()
    }

    // Plain key: the season + standings are global, and userId isn't read at the
    // top of load() (only meId inside onSuccess, for row highlighting).
    private val cacheKey = "seasons"

    fun load() {
        // Stale-while-revalidate: seed from the last cached state so re-entry
        // shows data instantly instead of a spinner, then revalidate below.
        val cached: SeasonsUiState? = container.cache.get(cacheKey)
        _state.value = (cached ?: _state.value).copy(loading = cached == null, error = false)
        viewModelScope.launch {
            runCatching {
                val season = container.progression.activeSeason()
                val standings = season?.let {
                    runCatching { container.progression.standings(it.id) }.getOrDefault(emptyList())
                } ?: emptyList()
                season to standings
            }.fold(
                onSuccess = { (season, standings) ->
                    val fresh = SeasonsUiState(
                        loading = false,
                        season = season,
                        standings = standings,
                        meId = container.auth.currentUserId,
                    )
                    _state.value = fresh
                    container.cache.put(cacheKey, fresh)
                },
                onFailure = { _state.value = _state.value.copy(loading = false, error = cached == null) },
            )
        }
    }

    fun join() {
        val season = _state.value.season ?: return
        if (_state.value.joining) return
        _state.value = _state.value.copy(joining = true)
        viewModelScope.launch {
            runCatching { container.progression.joinSeason(season.id) }
            _state.value = _state.value.copy(joining = false)
            load()
        }
    }
}

@Composable
fun SeasonsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: SeasonsViewModel = viewModel(factory = stackdViewModel { SeasonsViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    SeasonsScreen(
        state = state, onJoin = vm::join, onRetry = vm::load, onBack = onBack, modifier = modifier,
    )
}

@Composable
fun SeasonsScreen(
    state: SeasonsUiState,
    onJoin: () -> Unit,
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
            Text("STACK'D / SEASONS", style = MonoLabel, color = colors.textMuted)
            Spacer(Modifier.height(16.dp))
            SectionLabel("THE SEASON")
            Spacer(Modifier.height(16.dp))

            when {
                state.loading -> Text(
                    "Loading…",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                )
                state.error -> {
                    Text(
                        "Couldn't load the season.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Retry", onClick = onRetry)
                }
                state.season == null -> Text(
                    "No season is running right now.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                )
                else -> {
                    val s = state.season
                    Text(
                        s.name,
                        style = MaterialTheme.typography.displaySmall,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "ENDS ${s.endsAt.take(10)} · ×${s.xpMultiplier} XP",
                        style = MonoLabelSmall, color = colors.accent,
                    )
                    s.description?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                    }
                    Spacer(Modifier.height(16.dp))

                    if (state.mine == null) {
                        EmberButton(
                            text = if (state.joining) "Joining…" else "Join the season",
                            onClick = onJoin,
                            busy = state.joining,
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    Text("STANDINGS", style = MonoLabelSmall, color = colors.textMuted)
                    Spacer(Modifier.height(6.dp))
                    if (state.standings.isEmpty()) {
                        Text(
                            "No entries yet — be the first.",
                            style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                        )
                    }
                    state.standings.forEach { row ->
                        val isMe = row.userId == state.meId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .background(
                                    if (isMe) colors.accent.copy(alpha = 0.08f)
                                    else colors.textPrimary.copy(alpha = 0.02f),
                                    Radius2Xl,
                                )
                                .border(
                                    1.dp,
                                    if (isMe) colors.accent.copy(alpha = 0.5f) else colors.border,
                                    Radius2Xl,
                                )
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "#${row.rank}",
                                style = MonoLabelSmall,
                                color = if (row.rank <= 3) colors.accent else colors.textMuted,
                                modifier = Modifier.width(44.dp),
                            )
                            Text(
                                row.displayName?.takeIf { it.isNotBlank() } ?: "Anon",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary,
                                fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text("${row.xp} XP", style = MonoLabelSmall, color = colors.textPrimary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            GhostButton(text = "Back", onClick = onBack)
            Spacer(Modifier.height(32.dp))
        }
    }
}
