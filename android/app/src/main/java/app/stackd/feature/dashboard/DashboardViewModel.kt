package app.stackd.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stackd.data.auth.AuthRepository
import app.stackd.data.profile.ProfileRepository
import app.stackd.data.room.FocusHistoryRow
import app.stackd.data.room.RoomRow
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State for the analytics dashboard: lifetime stats, session history, and any
 * rooms running right now.
 *
 * The web dashboard also renders two AI cards (next-session recommendation,
 * ledger insights) driven by server functions that call an LLM. Those are a
 * backend surface outside Phase 1 — the honest core here is the ledger the
 * device can read directly under RLS. The cards get wired when the AI functions
 * are ported; the screen leaves room for them.
 */
data class DashboardUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val name: String = "You",
    val lifetimeXp: Long = 0,
    val streak: Int = 0,
    val history: List<FocusHistoryRow> = emptyList(),
    val live: List<RoomRow> = emptyList(),
) {
    /** Lifetime focus, summed off the same rows the history table shows. */
    val totalSeconds: Int get() = history.sumOf { it.durationSeconds }

    /** Mean score across completed sessions; 0 with no history rather than NaN. */
    val avgScore: Int get() =
        if (history.isEmpty()) 0 else history.sumOf { it.score } / history.size

    val isEmpty: Boolean get() = history.isEmpty()
}

class DashboardViewModel(
    private val auth: AuthRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        val userId = auth.currentUserId
        if (userId == null) {
            // No session — nothing to show, and not an error the user can fix
            // here. The nav guard routes signed-out users away before this.
            _state.value = DashboardUiState(loading = false)
            return
        }
        _state.value = _state.value.copy(loading = true, error = false)
        viewModelScope.launch {
            runCatching {
                // Three independent reads — fan them out, mirroring the web's
                // Promise.all, so the slowest one bounds the wait, not the sum.
                val profileDef = async { profiles.getProfile(userId) }
                val historyDef = async { profiles.recentSessions(userId) }
                val liveDef = async { profiles.activeSessions() }
                Triple(profileDef.await(), historyDef.await(), liveDef.await())
            }.fold(
                onSuccess = { (profile, history, live) ->
                    _state.value = DashboardUiState(
                        loading = false,
                        name = profile?.displayName?.takeIf { it.isNotBlank() }
                            ?: auth.currentEmail?.substringBefore("@") ?: "You",
                        lifetimeXp = profile?.lifetimeXp ?: 0,
                        streak = profile?.currentFocusStreak ?: 0,
                        history = history,
                        live = live,
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(loading = false, error = true)
                },
            )
        }
    }
}
