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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.heightIn
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
import app.stackd.core.ui.SkeletonBlock
import app.stackd.core.ui.pressFeedback
import app.stackd.feature.profile.FeatureEmptyState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.Role
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
    onOpenProfile: (String) -> Unit = {},
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
        onOpenProfile = onOpenProfile,
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
    onOpenProfile: (String) -> Unit = {},
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
                app.stackd.core.ui.ScreenHeader(
                    "STUDY CIRCLES",
                    onBack,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        "MANAGE →",
                        style = MonoLabelSmall,
                        color = colors.textMuted,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clickable(role = androidx.compose.ui.semantics.Role.Button) { onManage() }
                            .wrapContentHeight(Alignment.CenterVertically)
                            .padding(horizontal = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            SectionLabel("YOUR CIRCLES")
            Spacer(Modifier.height(16.dp))

            when {
                state.loading -> {
                    repeat(2) {
                        SkeletonBlock(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(60.dp), Radius2Xl)
                    }
                    Spacer(Modifier.height(24.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(24.dp))
                    Spacer(Modifier.height(16.dp))
                    repeat(4) {
                        SkeletonBlock(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(60.dp), Radius2Xl)
                    }
                }
                state.error -> {
                    Text(
                        "Couldn't load your circles.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Retry", onClick = onRetry)
                }
                state.circles.isEmpty() -> FeatureEmptyState(
                    icon = Icons.Outlined.Groups,
                    title = "No circles yet",
                    body = "Study with a small crew and climb a shared weekly board.",
                    actionText = "Create or join",
                    onAction = onManage,
                )
                else -> {
                    state.circles.forEach { c ->
                        val selected = state.activeId == c.id
                        val source = remember { MutableInteractionSource() }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .pressFeedback(source)
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
                                .selectable(
                                    selected = selected,
                                    interactionSource = source,
                                    indication = null,
                                    role = Role.Tab,
                                ) { onSelect(c.id) }
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

                    Spacer(Modifier.height(24.dp))
                    when {
                        state.detailLoading -> repeat(4) {
                            SkeletonBlock(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(60.dp), Radius2Xl)
                        }
                        state.detail == null -> Text(
                            "This circle is gone.",
                            style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                        )
                        else -> CircleBoard(state.detail, onOpenProfile)
                    }
                }
            }

            Spacer(Modifier.height(56.dp))
        }
    }
}

@Composable
private fun CircleBoard(detail: CircleDetail, onOpenProfile: (String) -> Unit) {
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
            "${detail.memberCount} ${if (detail.memberCount == 1) "member" else "members"} · ${detail.totalXp} XP",
            style = MonoLabelSmall, color = colors.textMuted,
        )
    }
    Spacer(Modifier.height(12.dp))
    detail.members.forEachIndexed { i, m ->
        val source = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .pressFeedback(source)
                .background(colors.textPrimary.copy(alpha = 0.02f), Radius2Xl)
                .border(1.dp, colors.border, Radius2Xl)
                .clickable(interactionSource = source, indication = null, role = Role.Button) { onOpenProfile(m.userId) }
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
            Box(contentAlignment = Alignment.BottomEnd) {
                app.stackd.core.ui.Avatar(url = m.avatarUrl, name = m.displayName, size = 34.dp)
                if (m.isOnline) {
                    Box(
                        modifier = Modifier
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
