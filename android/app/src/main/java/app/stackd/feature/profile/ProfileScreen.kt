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
    val usernameSaving: Boolean = false,
    val usernameNotice: String? = null,
    /** Live availability hint while typing a username. */
    val usernameStatus: UsernameStatus = UsernameStatus.Idle,
    /** Lifetime milestones shelf — null until loaded. */
    val shelf: app.stackd.data.profile.MilestoneShelf? = null,
)

/** Live username-availability hint states. */
sealed interface UsernameStatus {
    data object Idle : UsernameStatus
    data object Checking : UsernameStatus
    data object Available : UsernameStatus
    data class Unavailable(val message: String) : UsernameStatus
}

/** Own profile — web's `profile.tsx`: identity card, stats, edit, sign out. */
class ProfileViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state

    init {
        load()
    }

    private fun cacheKey(userId: String) = "profile:$userId"

    fun load() {
        val userId = container.auth.currentUserId ?: return
        // Stale-while-revalidate: seed from the last cached state so re-entry
        // shows data instantly instead of a spinner, then revalidate below.
        val cached: ProfileUiState? = container.cache.get(cacheKey(userId))
        _state.value = (cached ?: _state.value).copy(loading = cached == null, error = false)
        viewModelScope.launch {
            runCatching {
                val profile = container.profiles.getProfile(userId)
                val tier = runCatching { container.premium.myEntitlement().tier }.getOrDefault("free")
                val shelf = runCatching {
                    container.profiles.getMilestones(userId, userId)
                }.getOrNull()
                Triple(profile, tier, shelf)
            }.fold(
                onSuccess = { (profile, tier, shelf) ->
                    val fresh = ProfileUiState(
                        loading = false, profile = profile, tier = tier, shelf = shelf,
                    )
                    _state.value = fresh
                    container.cache.put(cacheKey(userId), fresh)
                },
                onFailure = { _state.value = _state.value.copy(loading = false, error = cached == null) },
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

    private var usernameProbe: kotlinx.coroutines.Job? = null

    /**
     * Debounced live availability probe as the user types (web's Checking… /
     * Available hint). The current handle is never flagged "taken" against
     * itself. Purely advisory — [saveUsername] is the real gate.
     */
    fun onUsernameInput(input: String) {
        usernameProbe?.cancel()
        val trimmed = input.trim()
        val current = _state.value.profile?.username
        if (trimmed.isEmpty() || trimmed == current) {
            _state.value = _state.value.copy(usernameStatus = UsernameStatus.Idle)
            return
        }
        val userId = container.auth.currentUserId ?: return
        _state.value = _state.value.copy(usernameStatus = UsernameStatus.Checking)
        usernameProbe = viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            val result = runCatching { container.profiles.checkUsername(userId, trimmed) }.getOrNull()
            _state.value = _state.value.copy(
                usernameStatus = when (result) {
                    is app.stackd.data.profile.UsernameResult.Ok -> UsernameStatus.Available
                    is app.stackd.data.profile.UsernameResult.Rejected ->
                        UsernameStatus.Unavailable(result.message)
                    null -> UsernameStatus.Idle
                },
            )
        }
    }

    fun saveUsername(username: String) {
        val userId = container.auth.currentUserId ?: return
        if (username.isBlank() || _state.value.usernameSaving) return
        _state.value = _state.value.copy(usernameSaving = true, usernameNotice = null)
        viewModelScope.launch {
            val result = container.profiles.setMyUsername(userId, username)
            val notice = when (result) {
                is app.stackd.data.profile.UsernameResult.Ok -> "Username set to @${result.username}."
                is app.stackd.data.profile.UsernameResult.Rejected -> result.message
            }
            _state.value = _state.value.copy(usernameSaving = false, usernameNotice = notice)
            if (result is app.stackd.data.profile.UsernameResult.Ok) load()
        }
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            container.auth.signOut()
            // Drop cached screen state so the next user never sees this one's data.
            container.cache.clear()
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
        onSaveUsername = vm::saveUsername,
        onUsernameInput = vm::onUsernameInput,
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
    onSaveUsername: (String) -> Unit,
    onUsernameInput: (String) -> Unit = {},
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

                    state.shelf?.let { shelf ->
                        Spacer(Modifier.height(24.dp))
                        MilestoneShelfSection(shelf)
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
                    SectionLabel("USERNAME")
                    Spacer(Modifier.height(8.dp))
                    var username by remember(p) { mutableStateOf(p.username.orEmpty()) }
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            if (it.length <= 20) {
                                username = it
                                onUsernameInput(it)
                            }
                        },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    // Live availability hint, else the rules + change-cooldown copy.
                    val (hint, hintColor) = when (val s = state.usernameStatus) {
                        UsernameStatus.Checking -> "Checking…" to colors.textMuted
                        UsernameStatus.Available -> "Available" to colors.accent
                        is UsernameStatus.Unavailable -> s.message to colors.breach
                        UsernameStatus.Idle ->
                            "3–20 characters, starts with a letter, letters/numbers/_/- only. " +
                                "You can change it once every 24h." to colors.textMuted
                    }
                    Text(hint, style = MonoLabelSmall, color = hintColor)
                    state.usernameNotice?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = MonoLabelSmall, color = colors.textMuted)
                    }
                    Spacer(Modifier.height(8.dp))
                    EmberButton(
                        text = if (state.usernameSaving) "Setting…" else "Set username",
                        onClick = { onSaveUsername(username) },
                        enabled = username.isNotBlank() && username != p.username,
                        busy = state.usernameSaving,
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

/**
 * Lifetime milestones shelf — the Android counterpart to web's MilestoneShelf.
 * Engraved plates for earned markers, the total hours held, empty copy before
 * the first, and a progress bar toward the next unearned milestone.
 */
@Composable
private fun MilestoneShelfSection(shelf: app.stackd.data.profile.MilestoneShelf) {
    val colors = Stackd.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SectionLabel("LIFETIME MILESTONES")
        Text("${shelf.totalHours}H HELD", style = MonoLabelSmall, color = colors.textMuted)
    }
    Spacer(Modifier.height(12.dp))

    if (shelf.earned.isEmpty()) {
        Text(
            "No milestones yet. The first plate is engraved at 100 hours held.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
        )
    } else {
        shelf.earned.forEach { m ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(colors.accent.copy(alpha = 0.06f), Radius2Xl)
                    .border(1.dp, colors.accent.copy(alpha = 0.3f), Radius2Xl)
                    .padding(16.dp),
            ) {
                Text(m.metric.uppercase(), style = MonoLabelSmall, color = colors.accent)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${m.threshold}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                )
                if (m.description.isNotBlank()) {
                    Text(m.description, style = MonoLabelSmall, color = colors.textMuted)
                }
                m.unlockedAt?.let {
                    Text(it.take(10), style = MonoLabelSmall, color = colors.textMuted)
                }
            }
        }
    }

    shelf.next?.let { next ->
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("NEXT · ${next.card.name.uppercase()}", style = MonoLabelSmall, color = colors.textMuted)
            Text("${next.current} / ${next.card.threshold}", style = MonoLabelSmall, color = colors.textMuted)
        }
        Spacer(Modifier.height(6.dp))
        val frac = (next.current.toFloat() / next.card.threshold.coerceAtLeast(1)).coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(colors.textPrimary.copy(alpha = 0.05f), Radius2Xl),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(frac)
                    .height(5.dp)
                    .background(colors.accent.copy(alpha = 0.7f), Radius2Xl),
            )
        }
    }
}
