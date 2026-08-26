package app.stackd.feature.leaderboard

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import app.stackd.core.theme.RadiusMd
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.SectionLabel
import app.stackd.data.social.LeaderboardGroup
import app.stackd.data.social.LeaderboardProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LeaderboardUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val individuals: List<LeaderboardProfile> = emptyList(),
    val groups: List<LeaderboardGroup> = emptyList(),
    val meId: String? = null,
)

class LeaderboardViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(LeaderboardUiState())
    val state: StateFlow<LeaderboardUiState> = _state

    init {
        load()
    }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = false)
        viewModelScope.launch {
            runCatching {
                container.leaderboard.topIndividuals() to container.leaderboard.topGroups()
            }.fold(
                onSuccess = { (people, groups) ->
                    _state.value = LeaderboardUiState(
                        loading = false,
                        individuals = people,
                        groups = groups,
                        meId = container.auth.currentUserId,
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(loading = false, error = true)
                },
            )
        }
    }
}

/**
 * XP rankings — web's `leaderboard.tsx`: individual and group tabs, top 100
 * each, with the caller's own row highlighted.
 */
@Composable
fun LeaderboardRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: LeaderboardViewModel = viewModel(factory = stackdViewModel { LeaderboardViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LeaderboardScreen(state = state, onRetry = vm::load, onBack = onBack, modifier = modifier)
}

@Composable
fun LeaderboardScreen(
    state: LeaderboardUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Stackd.colors
    var tab by remember { mutableStateOf("individual") }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState()),
    ) {
        ResponsiveColumn {
            Text("STACK'D / LEADERBOARD", style = MonoLabel, color = colors.textMuted)
            Spacer(Modifier.height(16.dp))
            SectionLabel("THE STANDINGS")
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("individual" to "INDIVIDUAL", "groups" to "GROUPS").forEach { (key, label) ->
                    val selected = tab == key
                    Text(
                        label,
                        style = MonoLabelSmall,
                        color = if (selected) colors.accent else colors.textMuted,
                        modifier = Modifier
                            .border(1.dp, if (selected) colors.accent else colors.border, RadiusMd)
                            .clickable { tab = key }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            when {
                state.loading -> Text(
                    "Loading the board…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
                state.error -> {
                    Text(
                        "Couldn't load the board.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Retry", onClick = onRetry)
                }
                tab == "individual" -> state.individuals.forEachIndexed { i, p ->
                    BoardRow(
                        rank = i + 1,
                        title = p.displayName?.takeIf { it.isNotBlank() } ?: "Anon",
                        subtitle = "${p.currentFocusStreak}d streak",
                        xp = p.lifetimeXp,
                        isMe = p.id == state.meId,
                    )
                }
                else -> state.groups.forEachIndexed { i, g ->
                    BoardRow(
                        rank = i + 1,
                        title = g.name,
                        subtitle = "${g.memberCount} members",
                        xp = g.totalGroupXp,
                        isMe = false,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            GhostButton(text = "Back", onClick = onBack)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun BoardRow(rank: Int, title: String, subtitle: String, xp: Long, isMe: Boolean) {
    val colors = Stackd.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(
                if (isMe) colors.accent.copy(alpha = 0.08f) else colors.textPrimary.copy(alpha = 0.02f),
                Radius2Xl,
            )
            .border(1.dp, if (isMe) colors.accent.copy(alpha = 0.5f) else colors.border, Radius2Xl)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "#$rank",
            style = MonoLabelSmall,
            color = if (rank <= 3) colors.accent else colors.textMuted,
            modifier = Modifier.width(44.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, style = MonoLabelSmall, color = colors.textMuted)
        }
        Text(
            "$xp XP",
            style = MonoLabelSmall,
            color = colors.textPrimary,
        )
    }
}
