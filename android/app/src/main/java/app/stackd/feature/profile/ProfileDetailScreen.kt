package app.stackd.feature.profile

import app.stackd.core.ui.glassSurface

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import app.stackd.core.formatHours
import app.stackd.core.stackdViewModel
import app.stackd.core.theme.MonoLabel
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.Radius2Xl
import app.stackd.core.theme.RadiusMd
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.SkeletonBlock
import app.stackd.data.profile.PublicProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ProfileDetailUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val profile: PublicProfile? = null,
    val busy: Boolean = false,
)

/** Another witness's profile — web's `profile.$id.tsx`. */
class ProfileDetailViewModel(
    private val container: AppContainer,
    private val targetId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileDetailUiState())
    val state: StateFlow<ProfileDetailUiState> = _state

    init {
        load()
    }

    // Keyed by the profile being viewed, not the viewer.
    // Getter: init { load() } runs before stored properties declared below it.
    private val cacheKey: String get() = "profileDetail:$targetId"

    fun load() {
        val viewerId = container.auth.currentUserId ?: return
        // Stale-while-revalidate: seed from the last cached state so re-entry
        // shows data instantly instead of a spinner, then revalidate below.
        val cached: ProfileDetailUiState? = container.cache.get(cacheKey)
        _state.value = (cached ?: _state.value).copy(loading = cached == null, error = false)
        viewModelScope.launch {
            runCatching { container.profiles.publicProfile(targetId, viewerId) }.fold(
                onSuccess = {
                    val fresh = ProfileDetailUiState(loading = false, profile = it)
                    _state.value = fresh
                    container.cache.put(cacheKey, fresh)
                },
                onFailure = { _state.value = _state.value.copy(loading = false, error = cached == null) },
            )
        }
    }

    /**
     * The single tie button cycles by current state: no edge → send request;
     * incoming → accept; friend → sever. Outgoing is disabled (awaiting them).
     */
    fun tieAction() {
        val viewerId = container.auth.currentUserId ?: return
        val p = _state.value.profile ?: return
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true)
        viewModelScope.launch {
            val f = p.friendship
            runCatching {
                when {
                    f == null -> container.friends.sendRequest(viewerId, p.profile.id)
                    f.direction == "incoming" -> container.friends.respond(f.id, viewerId, accept = true)
                    f.direction == "friend" -> container.friends.remove(f.id)
                    else -> Unit // outgoing: nothing to do
                }
            }
            _state.value = _state.value.copy(busy = false)
            load()
        }
    }
}

@Composable
fun ProfileDetailRoute(
    userId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ProfileDetailViewModel = viewModel(
        factory = stackdViewModel { ProfileDetailViewModel(it, userId) },
    ),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    ProfileDetailScreen(
        state = state,
        onTie = vm::tieAction,
        onRetry = vm::load,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileDetailScreen(
    state: ProfileDetailUiState,
    onTie: () -> Unit,
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
            app.stackd.core.ui.ScreenHeader("STACK'D / WITNESS", onBack)
            Spacer(Modifier.height(16.dp))

            when {
                state.loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkeletonBlock(Modifier.size(72.dp), CircleShape)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(24.dp))
                            Spacer(Modifier.height(8.dp))
                            SkeletonBlock(Modifier.fillMaxWidth(0.35f).height(12.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    SkeletonBlock(Modifier.fillMaxWidth().height(54.dp))
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(4) { SkeletonBlock(Modifier.weight(1f).height(56.dp), Radius2Xl) }
                    }
                }
                state.error || state.profile == null -> {
                    Text(
                        "Profile not found.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Retry", onClick = onRetry)
                }
                else -> {
                    val p = state.profile.profile
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        app.stackd.core.ui.Avatar(
                            url = p.avatarUrl,
                            name = p.displayName,
                            size = 72.dp,
                            sharedKey = "avatar-${p.id}",
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                p.displayName?.takeIf { it.isNotBlank() } ?: "Anonymous",
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            p.username?.takeIf { it.isNotBlank() }?.let {
                                Text("@$it", style = MonoLabelSmall, color = colors.textMuted)
                            }
                        }
                    }
                    p.bio?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                    }

                    Spacer(Modifier.height(16.dp))
                    TieButton(state.profile, busy = state.busy, onTie = onTie)

                    Spacer(Modifier.height(24.dp))
                    // Bento: lifetime XP at display scale beside three compact tiles.
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(
                            modifier = Modifier
                                .weight(1.15f)
                                .fillMaxHeight()
                                .glassSurface(Radius2Xl)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("LIFETIME", style = MonoLabelSmall, color = colors.accent)
                            Column {
                                Text(
                                    "${p.lifetimeXp}",
                                    // Long values step down a size so they never clip.
                                    style = if (p.lifetimeXp >= 100_000) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayMedium,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                )
                                Text("XP", style = MonoLabelSmall, color = colors.textMuted)
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "FOCUSED" to formatHours(p.totalFocusSeconds.toInt()),
                                "SESSIONS" to "${state.profile.sessionCount}",
                                "BEST" to "${p.bestStreak}d",
                            ).forEach { (label, value) ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .glassSurface(Radius2Xl)
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                ) {
                                    Text(label, style = MonoLabelSmall, color = colors.textMuted)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        value,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Text(
                        "ACHIEVEMENTS · ${state.profile.achievements.size}",
                        style = MonoLabelSmall, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (state.profile.achievements.isEmpty()) {
                        FeatureEmptyState(
                            icon = app.stackd.core.ui.StackdIcons.EmojiEvents,
                            title = "No unlocks yet",
                            body = "Achievements show up here as they're earned.",
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.profile.achievements.forEach { a ->
                                Column(
                                    modifier = Modifier
                                        .glassSurface(RadiusMd)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(a.tier.uppercase(), style = MonoLabelSmall, color = colors.accent)
                                    Text(a.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(56.dp))
        }
    }
}

@Composable
private fun TieButton(p: PublicProfile, busy: Boolean, onTie: () -> Unit) {
    val f = p.friendship
    val label = when {
        busy -> "…"
        f == null -> "Send tie"
        f.direction == "friend" -> "Sever tie"
        f.direction == "incoming" -> "Accept tie"
        else -> "Awaiting…"
    }
    // Outgoing requests have nothing to act on until they respond.
    val enabled = !busy && f?.direction != "outgoing"
    // Severing is destructive, so it must not wear the filled primary style.
    if (f?.direction == "friend") {
        GhostButton(text = label, onClick = onTie, enabled = enabled)
    } else {
        EmberButton(text = label, onClick = onTie, enabled = enabled)
    }
}
