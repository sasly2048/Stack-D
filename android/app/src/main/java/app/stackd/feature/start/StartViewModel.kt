package app.stackd.feature.start

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stackd.core.settings.SettingsStore
import app.stackd.data.auth.AuthRepository
import app.stackd.data.profile.ProfileRepository
import app.stackd.data.room.RoomRepository
import app.stackd.data.room.RoomTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * State for the "new session" configurator: template picker, title, collective
 * goal, duration, enforcement mode.
 *
 * [tplKey] empty means the Custom (manual) path. Selecting a template pins the
 * duration to that template's length and disables the slider, matching the web —
 * a template's whole point is that its parameters are fixed.
 */
data class StartUiState(
    val loading: Boolean = true,
    val templates: List<RoomTemplate> = emptyList(),
    val tplKey: String = "",
    val title: String = "",
    val goalHours: Int = 0,
    val duration: Int = RECOMMENDED_MINUTES,
    val lastMinutes: Int? = null,
    val mode: String = SettingsStore.MODE_ABSOLUTE,
    val showIntro: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    /** Set to the new room's code once creation succeeds; the screen navigates on it. */
    val createdCode: String? = null,
) {
    val durationLocked: Boolean get() = tplKey.isNotEmpty()

    companion object {
        const val RECOMMENDED_MINUTES = 30
        val QUICK_DURATIONS = listOf(15, 25, 30, 45, 60, 90)
        const val MIN_MINUTES = 5
        const val MAX_MINUTES = 240
        const val STEP_MINUTES = 5
    }
}

class StartViewModel(
    private val auth: AuthRepository,
    private val profiles: ProfileRepository,
    private val rooms: RoomRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(StartUiState())
    val state: StateFlow<StartUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val savedMode = settings.enforcementMode.first()
            val last = settings.lastSessionMinutes.first()
            val completed = settings.hasCompletedSession.first()
            val introDismissed = settings.startIntroDismissed.first()
            _state.value = _state.value.copy(
                mode = savedMode,
                lastMinutes = last,
                // Restore the last-used length as the starting duration, exactly
                // as the web does — the number you keep picking is the default.
                duration = last ?: _state.value.duration,
                showIntro = !completed && !introDismissed,
            )
            // Templates are a shortcut, not a requirement: a failure degrades to
            // the manual form, so it's swallowed rather than surfaced.
            runCatching { rooms.listTemplates() }
                .onSuccess { _state.value = _state.value.copy(templates = it, loading = false) }
                .onFailure { _state.value = _state.value.copy(loading = false) }
        }
    }

    fun selectTemplate(key: String) {
        val tpl = _state.value.templates.firstOrNull { it.key == key }
        _state.value = _state.value.copy(
            tplKey = key,
            // Pin duration to the template; Custom (empty key) leaves it as-is.
            duration = tpl?.let { (it.targetDurationSeconds / 60).toInt() } ?: _state.value.duration,
        )
    }

    fun onTitleChange(v: String) {
        _state.value = _state.value.copy(title = v.take(80))
    }

    fun onGoalHoursChange(v: Int) {
        _state.value = _state.value.copy(goalHours = v.coerceIn(0, 720))
    }

    fun onDurationChange(minutes: Int) {
        if (_state.value.durationLocked) return
        _state.value = _state.value.copy(
            duration = minutes.coerceIn(StartUiState.MIN_MINUTES, StartUiState.MAX_MINUTES),
        )
    }

    fun setMode(mode: String) {
        _state.value = _state.value.copy(mode = mode)
        viewModelScope.launch { settings.setEnforcementMode(mode) }
    }

    fun dismissIntro() {
        _state.value = _state.value.copy(showIntro = false)
        viewModelScope.launch { settings.dismissStartIntro() }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun create() {
        val s = _state.value
        if (s.busy) return
        val userId = auth.currentUserId
        if (userId == null) {
            _state.value = s.copy(error = "You're not signed in.")
            return
        }
        _state.value = s.copy(busy = true, error = null)
        viewModelScope.launch {
            val hostName = profiles.displayNameFor(userId, auth.currentEmail)
            val goalSeconds = if (s.goalHours > 0) s.goalHours * 3600L else null
            val result = if (s.tplKey.isNotEmpty()) {
                rooms.createRoomFromTemplate(
                    templateKey = s.tplKey,
                    title = s.title.takeIf { it.isNotBlank() },
                    collectiveGoalSeconds = goalSeconds,
                    hostId = userId,
                    hostDisplayName = hostName,
                )
            } else {
                rooms.createRoom(
                    targetDurationSeconds = s.duration * 60L,
                    title = s.title.takeIf { it.isNotBlank() },
                    collectiveGoalSeconds = goalSeconds,
                    hostId = userId,
                    hostDisplayName = hostName,
                )
            }
            result.fold(
                onSuccess = { code ->
                    // Persist the length only now the room exists — a duration
                    // picked but never started is a draft, not a preference.
                    settings.setLastSessionMinutes(s.duration)
                    _state.value = _state.value.copy(busy = false, createdCode = code)
                },
                onFailure = { err ->
                    _state.value = _state.value.copy(
                        busy = false,
                        error = err.message ?: "Could not create room.",
                    )
                },
            )
        }
    }

    /** Cleared once the screen has consumed the navigation. */
    fun consumeCreated() {
        _state.value = _state.value.copy(createdCode = null)
    }
}
