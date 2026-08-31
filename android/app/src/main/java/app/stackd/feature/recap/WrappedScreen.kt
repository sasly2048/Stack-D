package app.stackd.feature.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.SectionLabel
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

    fun load() {
        val userId = container.auth.currentUserId ?: return
        _state.value = _state.value.copy(loading = true, error = false)
        viewModelScope.launch {
            runCatching { container.recap.getWrapped(userId) }.fold(
                onSuccess = { _state.value = WrappedUiState(loading = false, stats = it) },
                onFailure = { _state.value = _state.value.copy(loading = false, error = true) },
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
            Text(
                "STACK WRAPPED" + when {
                    s == null -> ""
                    s.rolling -> " · LAST 12 MONTHS"
                    else -> " · ${s.year}"
                },
                style = MonoLabel, color = colors.accent,
            )
            Spacer(Modifier.height(16.dp))

            when {
                state.loading -> Text(
                    "Gathering your year…",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                )
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
                        color = colors.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "held.",
                        style = MaterialTheme.typography.displaySmall,
                        color = colors.accent,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "You stacked ${s.totalSessions} sessions, earned ${s.totalXp} XP, and held " +
                            "the line best on ${s.topWeekday}s around " +
                            "${s.peakHour.toString().padStart(2, '0')}:00.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )

                    Spacer(Modifier.height(24.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            "SESSIONS" to s.totalSessions.toString(),
                            "XP EARNED" to s.totalXp.toString(),
                            "LONGEST SESSION" to "${s.longestSessionMinutes} min",
                            "BEST STREAK" to "${s.bestStreak} days",
                            "UNBROKEN" to s.perfectSessions.toString(),
                            "FLOW STATES" to s.flowSessions.toString(),
                            "PEAK DAY" to s.topWeekday,
                            "TOP ALLY" to (s.topCollaborator?.name ?: "—"),
                            "PERCENTILE" to "Top ${maxOf(1, 100 - s.percentile)}%",
                        ).forEach { (label, value) -> StatTile(label, value) }
                    }

                    s.personality?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(20.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.accent,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                    SectionLabel("SHARE CARD")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Render your year as a 1080×1350 card and send it anywhere.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    EmberButton(
                        text = "Share Wrapped",
                        onClick = { WrappedCard.share(context, s) },
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
private fun StatTile(label: String, value: String) {
    val colors = Stackd.colors
    Column(
        modifier = Modifier
            .fillMaxWidth(0.48f)
            .background(colors.textPrimary.copy(alpha = 0.02f), Radius2Xl)
            .border(1.dp, colors.border, Radius2Xl)
            .padding(16.dp),
    ) {
        Text(label, style = MonoLabelSmall, color = colors.textMuted)
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}
