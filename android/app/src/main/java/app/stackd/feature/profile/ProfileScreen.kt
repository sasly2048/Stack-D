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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.SectionLabel
import app.stackd.data.room.ProfileRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val profile: ProfileRow? = null,
    val tier: String = "free",
    val saving: Boolean = false,
)

/** Own profile — web's `profile.tsx`: identity card, stats, edit, sign out. */
class ProfileViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state

    init {
        load()
    }

    fun load() {
        val userId = container.auth.currentUserId ?: return
        _state.value = _state.value.copy(loading = true, error = false)
        viewModelScope.launch {
            runCatching {
                val profile = container.profiles.getProfile(userId)
                val tier = runCatching { container.premium.myEntitlement().tier }.getOrDefault("free")
                profile to tier
            }.fold(
                onSuccess = { (profile, tier) ->
                    _state.value = ProfileUiState(loading = false, profile = profile, tier = tier)
                },
                onFailure = { _state.value = _state.value.copy(loading = false, error = true) },
            )
        }
    }

    fun save(displayName: String, bio: String) {
        val userId = container.auth.currentUserId ?: return
        _state.value = _state.value.copy(saving = true)
        viewModelScope.launch {
            runCatching { container.profiles.updateProfile(userId, displayName, bio) }
            _state.value = _state.value.copy(saving = false)
            load()
        }
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            container.auth.signOut()
            onDone()
        }
    }
}

@Composable
fun ProfileRoute(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    onOpenPremium: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ProfileViewModel = viewModel(factory = stackdViewModel { ProfileViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    ProfileScreen(
        state = state,
        onSave = vm::save,
        onSignOut = { vm.signOut(onSignedOut) },
        onRetry = vm::load,
        onBack = onBack,
        onOpenPremium = onOpenPremium,
        modifier = modifier,
    )
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onSave: (String, String) -> Unit,
    onSignOut: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpenPremium: () -> Unit,
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
            Text("STACK'D / PROFILE", style = MonoLabel, color = colors.textMuted)
            Spacer(Modifier.height(16.dp))

            when {
                state.loading -> Text(
                    "Loading…",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                )
                state.error || state.profile == null -> {
                    Text(
                        "Couldn't load your profile.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Retry", onClick = onRetry)
                }
                else -> {
                    val p = state.profile
                    Text(
                        p.displayName?.takeIf { it.isNotBlank() } ?: "Anon",
                        style = MaterialTheme.typography.displaySmall,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(state.tier.uppercase(), style = MonoLabelSmall, color = colors.accent)
                        p.title?.takeIf { it.isNotBlank() }?.let {
                            Text(it.uppercase(), style = MonoLabelSmall, color = colors.textMuted)
                        }
                        p.username?.takeIf { it.isNotBlank() }?.let {
                            Text("@$it", style = MonoLabelSmall, color = colors.textMuted)
                        }
                    }
                    p.bio?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                    }

                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "LIFETIME XP" to "${p.lifetimeXp}",
                            "STREAK" to "${p.currentFocusStreak}d",
                            "BEST" to "${p.bestStreak}d",
                            "FOCUSED" to formatHours(p.totalFocusSeconds.toInt()),
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
                    SectionLabel("EDIT")
                    Spacer(Modifier.height(8.dp))
                    var name by remember(p) { mutableStateOf(p.displayName.orEmpty()) }
                    var bio by remember(p) { mutableStateOf(p.bio.orEmpty()) }
                    OutlinedTextField(
                        value = name, onValueChange = { if (it.length <= 60) name = it },
                        label = { Text("Display name") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = bio, onValueChange = { if (it.length <= 300) bio = it },
                        label = { Text("Bio") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    EmberButton(
                        text = if (state.saving) "Saving…" else "Save",
                        onClick = { onSave(name, bio) },
                        enabled = name.isNotBlank(),
                        busy = state.saving,
                    )

                    Spacer(Modifier.height(20.dp))
                    GhostButton(text = "Manage plan", onClick = onOpenPremium)
                    Spacer(Modifier.height(8.dp))
                    GhostButton(text = "Sign out", onClick = onSignOut)
                }
            }

            Spacer(Modifier.height(24.dp))
            GhostButton(text = "Back", onClick = onBack)
            Spacer(Modifier.height(32.dp))
        }
    }
}
