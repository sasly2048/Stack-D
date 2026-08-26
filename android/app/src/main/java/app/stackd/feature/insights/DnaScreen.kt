package app.stackd.feature.insights

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.SectionLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant

data class DnaUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    /** Null until entitlement resolves; false shows the Pro gate. */
    val hasAccess: Boolean? = null,
    val dna: AnalyticsEngine.Dna? = null,
)

/**
 * Focus DNA — web's `dna.tsx`. Pro-gated exactly like the web: the server
 * function calls `requireFeature("focus_dna")`, we resolve the same
 * entitlement RPC and show the upgrade gate to free-tier users.
 */
class DnaViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(DnaUiState())
    val state: StateFlow<DnaUiState> = _state

    init {
        load()
    }

    fun load() {
        val userId = container.auth.currentUserId ?: return
        _state.value = _state.value.copy(loading = true, error = false)
        viewModelScope.launch {
            val ent = runCatching { container.premium.myEntitlement() }.getOrNull()
            if (ent == null) {
                _state.value = _state.value.copy(loading = false, error = true)
                return@launch
            }
            if (!ent.isPro && !ent.isAdmin) {
                _state.value = DnaUiState(loading = false, hasAccess = false)
                return@launch
            }
            runCatching {
                val since = Instant.now().minusSeconds(60L * 24 * 3600).toString()
                AnalyticsEngine.dna(container.profiles.historySince(userId, since, limit = 500))
            }.fold(
                onSuccess = { _state.value = DnaUiState(loading = false, hasAccess = true, dna = it) },
                onFailure = { _state.value = _state.value.copy(loading = false, error = true) },
            )
        }
    }
}

@Composable
fun DnaRoute(
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
    vm: DnaViewModel = viewModel(factory = stackdViewModel { DnaViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    DnaScreen(state = state, onRetry = vm::load, onBack = onBack, onUpgrade = onUpgrade, modifier = modifier)
}

@Composable
fun DnaScreen(
    state: DnaUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
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
            Text("STACK'D / DNA", style = MonoLabel, color = colors.textMuted)
            Spacer(Modifier.height(16.dp))
            SectionLabel("FOCUS DNA")
            Spacer(Modifier.height(16.dp))

            when {
                state.loading -> Text(
                    "Sequencing…",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                )
                state.error -> {
                    Text(
                        "Couldn't read your DNA.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Retry", onClick = onRetry)
                }
                state.hasAccess == false -> {
                    // The Pro gate — web's <PremiumGate feature="focus_dna">.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.accent.copy(alpha = 0.04f), Radius2Xl)
                            .border(1.dp, colors.accent.copy(alpha = 0.3f), Radius2Xl)
                            .padding(20.dp),
                    ) {
                        Text("PRO FEATURE", style = MonoLabelSmall, color = colors.accent)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Your focus signature, mapped from every session into traits you can act on.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
                        Spacer(Modifier.height(16.dp))
                        EmberButton(text = "See plans", onClick = onUpgrade)
                    }
                }
                state.dna != null -> {
                    val dna = state.dna
                    Text(
                        dna.archetype,
                        style = MaterialTheme.typography.displaySmall,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "SIGNATURE ${dna.signature} · ${dna.totalSessions} SESSIONS · PEAK ${dna.peakHour}:00",
                        style = MonoLabelSmall,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(20.dp))
                    FocusRadar(dna.traits)
                    Spacer(Modifier.height(20.dp))
                    dna.traits.forEach { t ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Text(
                                t.label.uppercase(),
                                style = MonoLabelSmall,
                                color = colors.textMuted,
                                modifier = Modifier.width(110.dp),
                            )
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .background(colors.textPrimary.copy(alpha = 0.05f), CircleShape),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(t.value / 100f)
                                        .height(6.dp)
                                        .background(colors.accent, CircleShape),
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("${t.value}", style = MonoLabelSmall, color = colors.textPrimary)
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
