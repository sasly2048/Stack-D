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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import app.stackd.data.progression.Challenge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ChallengesUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val rows: List<Challenge> = emptyList(),
)

/** Daily/weekly challenges — web's `challenges.tsx` over `listChallenges`. */
class ChallengesViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ChallengesUiState())
    val state: StateFlow<ChallengesUiState> = _state

    init {
        load()
    }

    fun load() {
        val userId = container.auth.currentUserId ?: return
        _state.value = _state.value.copy(loading = true, error = false)
        viewModelScope.launch {
            runCatching { container.progression.listChallenges(userId) }.fold(
                onSuccess = { _state.value = ChallengesUiState(loading = false, rows = it) },
                onFailure = { _state.value = _state.value.copy(loading = false, error = true) },
            )
        }
    }
}

@Composable
fun ChallengesRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ChallengesViewModel = viewModel(factory = stackdViewModel { ChallengesViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    ChallengesScreen(state = state, onRetry = vm::load, onBack = onBack, modifier = modifier)
}

@Composable
fun ChallengesScreen(
    state: ChallengesUiState,
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
            Text("STACK'D / CHALLENGES", style = MonoLabel, color = colors.textMuted)
            Spacer(Modifier.height(16.dp))
            SectionLabel("PROVE IT")
            Spacer(Modifier.height(16.dp))

            when {
                state.loading -> Text(
                    "Loading…",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                )
                state.error -> {
                    Text(
                        "Couldn't load challenges.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Retry", onClick = onRetry)
                }
                else -> listOf(
                    "daily" to "TODAY",
                    "weekly" to "THIS WEEK",
                ).forEach { (cadence, heading) ->
                    val rows = state.rows.filter { it.cadence == cadence }
                    if (rows.isEmpty()) return@forEach
                    Text(heading, style = MonoLabelSmall, color = colors.accent)
                    Spacer(Modifier.height(6.dp))
                    rows.forEach { c ->
                        val done = c.completedAt != null
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .background(colors.textPrimary.copy(alpha = 0.02f), Radius2Xl)
                                .border(
                                    1.dp,
                                    if (done) colors.accent.copy(alpha = 0.5f) else colors.border,
                                    Radius2Xl,
                                )
                                .padding(14.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        c.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(c.description, style = MonoLabelSmall, color = colors.textMuted)
                                }
                                Text(
                                    if (done) "DONE · +${c.xpReward} XP" else "+${c.xpReward} XP",
                                    style = MonoLabelSmall,
                                    color = if (done) colors.accent else colors.textMuted,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            val frac = (c.progress.toFloat() / c.target.coerceAtLeast(1)).coerceIn(0f, 1f)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .background(colors.textPrimary.copy(alpha = 0.05f), CircleShape),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(frac)
                                        .height(5.dp)
                                        .background(colors.accent, CircleShape),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${c.progress} / ${c.target}",
                                style = MonoLabelSmall,
                                color = colors.textMuted,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
            GhostButton(text = "Back", onClick = onBack)
            Spacer(Modifier.height(32.dp))
        }
    }
}
