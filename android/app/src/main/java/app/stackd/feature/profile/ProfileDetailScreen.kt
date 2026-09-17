package app.stackd.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    private val cacheKey = "profileDetail:$targetId"

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
            Text("STACK'D / WITNESS", style = MonoLabel, color = colors.textMuted)
            Spacer(Modifier.height(16.dp))

            when {
                state.loading -> Text(
                    "Loading…",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                )
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
                        Spacer(Modifier.height(10.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                    }

                    Spacer(Modifier.height(14.dp))
                    TieButton(state.profile, busy = state.busy, onTie = onTie)

                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "LIFETIME XP" to "${p.lifetimeXp}",
                            "FOCUSED" to formatHours(p.totalFocusSeconds.toInt()),
                            "SESSIONS" to "${state.profile.sessionCount}",
                            "BEST" to "${p.bestStreak}d",
                        ).forEach { (label, value) ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(colors.textPrimary.copy(alpha = 0.03f), Radius2Xl)
                                    .border(1.dp, colors.border, Radius2Xl)
                                    .padding(10.dp),
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

                    Spacer(Modifier.height(24.dp))
                    Text(
                        "ACHIEVEMENTS · ${state.profile.achievements.size}",
                        style = MonoLabelSmall, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (state.profile.achievements.isEmpty()) {
                        Text(
                            "No unlocks yet.",
                            style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.profile.achievements.forEach { a ->
                                Column(
                                    modifier = Modifier
                                        .background(colors.textPrimary.copy(alpha = 0.03f), RadiusMd)
                                        .border(1.dp, colors.border, RadiusMd)
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

            Spacer(Modifier.height(24.dp))
            GhostButton(text = "Back", onClick = onBack)
            Spacer(Modifier.height(32.dp))
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
    EmberButton(
        text = label,
        onClick = onTie,
        // Outgoing requests have nothing to act on until they respond.
        enabled = !busy && f?.direction != "outgoing",
    )
}
