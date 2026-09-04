package app.stackd.feature.trust

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
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
import app.stackd.data.trust.HostReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ModerationUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val reports: List<HostReport> = emptyList(),
    /** "open" | "all" */
    val filter: String = "open",
    val actingId: String? = null,
    val notice: String? = null,
) {
    val visible: List<HostReport>
        get() = if (filter == "open") reports.filter { it.status == "open" } else reports
}

/** Host moderation dashboard — web's `trust.moderation.tsx`. */
class ModerationViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ModerationUiState())
    val state: StateFlow<ModerationUiState> = _state

    init {
        load()
    }

    private fun cacheKey(userId: String) = "moderation:$userId"

    fun load() {
        val userId = container.auth.currentUserId ?: return
        // Stale-while-revalidate: seed from the last cached state so re-entry
        // shows data instantly instead of a spinner, then revalidate below.
        val cached: ModerationUiState? = container.cache.get(cacheKey(userId))
        _state.value = (cached ?: _state.value).copy(loading = cached == null, error = false)
        viewModelScope.launch {
            runCatching { container.trust.listRoomReports(userId) }.fold(
                onSuccess = {
                    val fresh = _state.value.copy(loading = false, error = false, reports = it)
                    _state.value = fresh
                    container.cache.put(cacheKey(userId), fresh)
                },
                onFailure = { _state.value = _state.value.copy(loading = false, error = cached == null) },
            )
        }
    }

    fun setFilter(f: String) {
        _state.value = _state.value.copy(filter = f)
    }

    fun act(id: String, status: String) {
        if (_state.value.actingId != null) return
        _state.value = _state.value.copy(actingId = id, notice = null)
        viewModelScope.launch {
            runCatching { container.trust.resolveReport(id, status) }.fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        actingId = null,
                        reports = _state.value.reports.map {
                            if (it.id == id) it.copy(status = status) else it
                        },
                        notice = if (status == "resolved") "Report resolved." else "Report dismissed.",
                    )
                },
                onFailure = {
                    // See TrustRepository: the UPDATE grant is missing, so this
                    // is expected to fail on both clients until it lands. Report
                    // it honestly rather than faking a resolve.
                    _state.value = _state.value.copy(
                        actingId = null,
                        notice = "Couldn't update the report. Moderation actions are not enabled yet.",
                    )
                },
            )
        }
    }
}

@Composable
fun ModerationRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ModerationViewModel = viewModel(factory = stackdViewModel { ModerationViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    ModerationScreen(
        state = state,
        onFilter = vm::setFilter,
        onAct = vm::act,
        onRetry = vm::load,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun ModerationScreen(
    state: ModerationUiState,
    onFilter: (String) -> Unit,
    onAct: (String, String) -> Unit,
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
            Text("STACK'D / MODERATION", style = MonoLabel, color = colors.textMuted)
            Spacer(Modifier.height(16.dp))
            SectionLabel("MODERATION")
            Spacer(Modifier.height(8.dp))
            Text(
                "Reports filed on rooms you host.",
                style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
            )
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("open", "all").forEach { k ->
                    val on = state.filter == k
                    Text(
                        k.uppercase(),
                        style = MonoLabelSmall,
                        color = if (on) colors.accent else colors.textMuted,
                        modifier = Modifier
                            .background(
                                if (on) colors.accent.copy(alpha = 0.1f) else colors.textPrimary.copy(alpha = 0.04f),
                                CircleShape,
                            )
                            .border(
                                1.dp,
                                if (on) colors.accent.copy(alpha = 0.6f) else colors.border,
                                CircleShape,
                            )
                            .clickable { onFilter(k) }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }

            state.notice?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MonoLabelSmall, color = colors.accent)
            }

            Spacer(Modifier.height(20.dp))
            when {
                state.loading -> Text(
                    "Loading reports…",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                )
                state.error -> {
                    Text(
                        "Couldn't load reports.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Retry", onClick = onRetry)
                }
                state.visible.isEmpty() -> Text(
                    "No reports here. Rooms you host are clean.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                )
                else -> state.visible.forEach { r ->
                    HostReportCard(r, acting = state.actingId == r.id, onAct = onAct)
                }
            }

            Spacer(Modifier.height(24.dp))
            GhostButton(text = "Back", onClick = onBack)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun HostReportCard(r: HostReport, acting: Boolean, onAct: (String, String) -> Unit) {
    val colors = Stackd.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(colors.textPrimary.copy(alpha = 0.02f), Radius2Xl)
            .border(1.dp, colors.border, Radius2Xl)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "${trustDateTime(r.createdAt)} · Room ${r.roomCode ?: "—"}",
                style = MonoLabelSmall, color = colors.textMuted,
            )
            Text(
                r.status.uppercase(),
                style = MonoLabelSmall,
                color = if (r.status == "open") colors.accent else colors.textMuted,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row {
            Text(r.kind, style = MaterialTheme.typography.bodyMedium, color = colors.accent)
            r.targetName?.let {
                Text(
                    " · target: $it",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary,
                )
            }
            r.reporterName?.let {
                Text(
                    " · from $it",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                )
            }
        }
        r.reason?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "“$it”",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                fontStyle = FontStyle.Italic,
            )
        }
        if (r.status == "open") {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(
                    text = if (acting) "…" else "Resolve",
                    onClick = { onAct(r.id, "resolved") },
                    enabled = !acting,
                    modifier = Modifier.weight(1f),
                )
                GhostButton(
                    text = if (acting) "…" else "Dismiss",
                    onClick = { onAct(r.id, "dismissed") },
                    enabled = !acting,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
