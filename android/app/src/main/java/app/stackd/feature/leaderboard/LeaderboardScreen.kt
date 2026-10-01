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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
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
import app.stackd.core.theme.SerifFamily
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.SectionLabel
import app.stackd.core.ui.SkeletonBlock
import app.stackd.core.ui.pressFeedback
import app.stackd.feature.profile.FeatureEmptyState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.ui.graphics.Color
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

    // A getter, not a stored val: init { load() } above runs before stored
    // properties below it are initialized, so a stored key read null and crashed.
    private val cacheKey: String get() = "leaderboard"

    fun load() {
        // Rankings tolerate seconds of staleness — show the last set instantly,
        // then revalidate. No spinner on re-entry.
        val cached: LeaderboardUiState? = container.cache.get(cacheKey)
        _state.value = (cached ?: _state.value).copy(loading = cached == null, error = false)
        viewModelScope.launch {
            runCatching {
                container.leaderboard.topIndividuals() to container.leaderboard.topGroups()
            }.fold(
                onSuccess = { (people, groups) ->
                    val fresh = LeaderboardUiState(
                        loading = false,
                        individuals = people,
                        groups = groups,
                        meId = container.auth.currentUserId,
                    )
                    _state.value = fresh
                    container.cache.put(cacheKey, fresh)
                },
                onFailure = {
                    _state.value = _state.value.copy(loading = false, error = cached == null)
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
    onOpenProfile: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    vm: LeaderboardViewModel = viewModel(factory = stackdViewModel { LeaderboardViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LeaderboardScreen(state = state, onRetry = vm::load, onBack = onBack, onOpenProfile = onOpenProfile, modifier = modifier)
}

@Composable
fun LeaderboardScreen(
    state: LeaderboardUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenProfile: (String) -> Unit = {},
) {
    val colors = Stackd.colors
    var tab by remember { mutableStateOf("individual") }
    app.stackd.core.ui.ResponsiveLazyColumn(modifier = modifier.background(colors.background)) {
        item(key = "header") {
            Column {
                app.stackd.core.ui.ScreenHeader("STACK'D / LEADERBOARD", onBack)
                Spacer(Modifier.height(16.dp))
                SectionLabel("THE STANDINGS")
                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("individual" to "INDIVIDUAL", "groups" to "GROUPS").forEach { (key, label) ->
                        val selected = tab == key
                        val source = remember { MutableInteractionSource() }
                        Text(
                            label,
                            style = MonoLabelSmall,
                            color = if (selected) colors.accent else colors.textMuted,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .pressFeedback(source)
                                .background(if (selected) colors.accent.copy(alpha = 0.08f) else Color.Transparent, RadiusMd)
                                .border(1.dp, if (selected) colors.accent else colors.border, RadiusMd)
                                .selectable(
                                    selected = selected,
                                    interactionSource = source,
                                    indication = null,
                                    role = androidx.compose.ui.semantics.Role.Tab,
                                ) { tab = key }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                val myRank = state.individuals.indexOfFirst { it.id == state.meId }
                if (tab == "individual" && !state.loading && myRank >= 0) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "#${myRank + 1}",
                            style = MaterialTheme.typography.displayMedium,
                            fontFamily = SerifFamily,
                            fontWeight = FontWeight.Normal,
                            color = colors.textPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "YOUR RANK · OF ${state.individuals.size}",
                            style = MonoLabelSmall,
                            color = colors.textMuted,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        when {
            state.loading -> items(8, key = { "sk:$it" }) {
                SkeletonBlock(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(60.dp), Radius2Xl)
            }
            state.error -> item(key = "error") {
                Column {
                    Text(
                        "Couldn't load the board.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Retry", onClick = onRetry)
                }
            }
            tab == "individual" && state.individuals.isEmpty() -> item(key = "empty-p") {
                FeatureEmptyState(
                    icon = Icons.Outlined.Leaderboard,
                    title = "The board is empty",
                    body = "Hold a session to earn XP and claim the first spot.",
                )
            }
            tab == "groups" && state.groups.isEmpty() -> item(key = "empty-g") {
                FeatureEmptyState(
                    icon = Icons.Outlined.Groups,
                    title = "No groups ranked yet",
                    body = "Groups appear here once their members start earning XP.",
                )
            }
            tab == "individual" -> itemsIndexed(state.individuals, key = { _, p -> "p:${p.id}" }) { i, p ->
                BoardRow(
                    rank = i + 1,
                    title = p.displayName?.takeIf { it.isNotBlank() } ?: "Anon",
                    subtitle = "${p.currentFocusStreak}d streak",
                    xp = p.lifetimeXp,
                    isMe = p.id == state.meId,
                    onClick = { onOpenProfile(p.id) },
                )
            }
            else -> itemsIndexed(state.groups, key = { _, g -> "g:${g.id}" }) { i, g ->
                BoardRow(
                    rank = i + 1,
                    title = g.name,
                    subtitle = if (g.memberCount == 1) "1 member" else "${g.memberCount} members",
                    xp = g.totalGroupXp,
                    isMe = false,
                )
            }
        }

        item(key = "footer") {
            Column {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun BoardRow(rank: Int, title: String, subtitle: String, xp: Long, isMe: Boolean, onClick: (() -> Unit)? = null) {
    val colors = Stackd.colors
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(if (onClick != null) Modifier.pressFeedback(source) else Modifier)
            .background(
                if (isMe) {
                    Brush.verticalGradient(listOf(colors.accent.copy(alpha = 0.09f), colors.surface))
                } else {
                    SolidColor(colors.textPrimary.copy(alpha = 0.04f))
                },
                Radius2Xl,
            )
            .border(1.dp, if (isMe) colors.accent.copy(alpha = 0.18f) else colors.border, Radius2Xl)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = source,
                        indication = null,
                        role = androidx.compose.ui.semantics.Role.Button,
                    ) { onClick() }
                } else {
                    Modifier
                },
            )
            .heightIn(min = 48.dp)
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
        if (rank == 1) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$xp",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.Normal,
                    color = colors.textPrimary,
                )
                Text(" XP", style = MonoLabelSmall, color = colors.textMuted, modifier = Modifier.padding(bottom = 4.dp))
            }
        } else {
            Text(
                "$xp XP",
                style = MonoLabelSmall,
                color = colors.textPrimary,
            )
        }
    }
}
