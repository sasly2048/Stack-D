package app.stackd.feature.groups

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
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.SectionLabel
import app.stackd.data.social.CircleDetail
import app.stackd.data.social.CircleRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CirclesUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val circles: List<CircleRef> = emptyList(),
    val activeId: String? = null,
    val detailLoading: Boolean = false,
    val detail: CircleDetail? = null,
)

/**
 * Weekly circle standings — web's `circles.tsx` over `circles.functions.ts`.
 * Read-only; the create/join/leave surface lives on [GroupsScreen] ("Manage →").
 */
class CirclesViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(CirclesUiState())
    val state: StateFlow<CirclesUiState> = _state

    init {
        load()
    }

    fun load() {
        val userId = container.auth.currentUserId ?: return
        _state.value = _state.value.copy(loading = true, error = false)
        viewModelScope.launch {
            runCatching { container.groups.listMyCircles(userId) }.fold(
                onSuccess = { circles ->
                    _state.value = _state.value.copy(loading = false, circles = circles)
                    // Default to the first circle so there's never a loaded-but-
                    // empty frame, matching the web's `picked ?? circles[0]`.
                    circles.firstOrNull()?.let { select(it.id) }
                },
                onFailure = { _state.value = _state.value.copy(loading = false, error = true) },
            )
        }
    }

    fun select(id: String) {
        if (_state.value.activeId == id && _state.value.detail != null) return
        _state.value = _state.value.copy(activeId = id, detailLoading = true, detail = null)
        viewModelScope.launch {
            val detail = runCatching {
                container.groups.circleDetail(id, System.currentTimeMillis())
            }.getOrNull()
            // Guard against a stale response if the user tapped another circle
            // while this one was loading.
            if (_state.value.activeId == id) {
                _state.value = _state.value.copy(detailLoading = false, detail = detail)
            }
        }
    }
}

@Composable
fun CirclesRoute(
    onBack: () -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
    vm: CirclesViewModel = viewModel(factory = stackdViewModel { CirclesViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    CirclesScreen(
        state = state,
        onSelect = vm::select,
        onRetry = vm::load,
        onBack = onBack,
        onManage = onManage,
        modifier = modifier,
    )
}

@Composable
fun CirclesScreen(
    state: CirclesUiState,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onManage: () -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("STUDY CIRCLES", style = MonoLabel, color = colors.textMuted)
                Text(
                    "MANAGE →",
                    style = MonoLabelSmall,
                    color = colors.textMuted,
                    modifier = Modifier.clickable { onManage() },
                )
            }
            Spacer(Modifier.height(16.dp))
            SectionLabel("YOUR CIRCLES")
            Spacer(Modifier.height(16.dp))

            when {
                state.loading -> Text(
                    "Loading your circles…",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                )
                state.error -> {
                    Text(
                        "Couldn't load your circles.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Retry", onClick = onRetry)
                }
                state.circles.isEmpty() -> {
                    Text(
                        "You haven't joined any circles yet.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Create or join", onClick = onManage)
                }
                else -> {
                    state.circles.forEach { c ->
                        val selected = state.activeId == c.id
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .background(
                                    if (selected) colors.accent.copy(alpha = 0.1f)
                                    else colors.textPrimary.copy(alpha = 0.02f),
                                    Radius2Xl,
                                )
                                .border(
                                    1.dp,
                                    if (selected) colors.accent.copy(alpha = 0.5f) else colors.border,
                                    Radius2Xl,
                                )
                                .clickable { onSelect(c.id) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Text(
                                c.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected) colors.accent else colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text("${c.totalXp} XP", style = MonoLabelSmall, color = colors.textMuted)
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    when {
                        state.detailLoading -> Text(
                            "Loading circle…",
                            style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                        )
                        state.detail == null -> Text(
                            "This circle is gone.",
                            style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                        )
                        else -> CircleBoard(state.detail)
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
private fun CircleBoard(detail: CircleDetail) {
    val colors = Stackd.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            detail.name,
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            "${detail.memberCount} members · ${detail.totalXp} XP",
            style = MonoLabelSmall, color = colors.textMuted,
        )
    }
    Spacer(Modifier.height(12.dp))
    detail.members.forEachIndexed { i, m ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .background(colors.textPrimary.copy(alpha = 0.02f), Radius2Xl)
                .border(1.dp, colors.border, Radius2Xl)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "${i + 1}",
                style = MonoLabelSmall,
                color = if (i < 3) colors.accent else colors.textMuted,
                modifier = Modifier.size(20.dp),
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(colors.textPrimary.copy(alpha = 0.05f), CircleShape)
                    .border(1.dp, colors.border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (m.displayName ?: "?").take(2).uppercase(),
                    style = MonoLabelSmall, color = colors.accent,
                )
                if (m.isOnline) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(9.dp)
                            .background(colors.accent, CircleShape)
                            .border(2.dp, colors.background, CircleShape),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    m.displayName?.takeIf { it.isNotBlank() } ?: "Anon",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${m.currentStreak}🔥 streak · ${m.weeklyMinutes}m this week",
                    style = MonoLabelSmall, color = colors.textMuted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${m.weeklyXp}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.accent,
                    fontWeight = FontWeight.Bold,
                )
                Text("WEEKLY XP", style = MonoLabelSmall, color = colors.textMuted)
            }
        }
    }
}
