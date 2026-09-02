package app.stackd.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stackd.data.auth.AuthRepository
import app.stackd.data.profile.ProfileRepository
import app.stackd.data.profile.RewardStatus
import app.stackd.data.room.FocusHistoryRow
import app.stackd.data.room.RoomRow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    /** Daily login reward — null while loading or if the read failed. */
    val reward: RewardStatus? = null,
    val claiming: Boolean = false,
    /** One-shot claim feedback line, e.g. "+40 XP · Day 3". */
    val claimNotice: String? = null,
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
                //
                // coroutineScope is load-bearing: an `async` launched straight
                // into viewModelScope propagates its failure to the PARENT scope,
                // not to the await() call site, so a throwing read (e.g. an RLS
                // denial on profiles) would escape this runCatching and crash the
                // app. Nesting the asyncs in coroutineScope makes them its
                // children, so the failure surfaces here where runCatching sees it.
                coroutineScope {
                    val profileDef = async { profiles.getProfile(userId) }
                    val historyDef = async { profiles.recentSessions(userId) }
                    val liveDef = async { profiles.activeSessions() }
                    val rewardDef = async { runCatching { profiles.rewardStatus(userId) }.getOrNull() }
                    Quad(profileDef.await(), historyDef.await(), liveDef.await(), rewardDef.await())
                }
            }.fold(
                onSuccess = { (profile, history, live, reward) ->
                    _state.value = DashboardUiState(
                        loading = false,
                        name = profile?.displayName?.takeIf { it.isNotBlank() }
                            ?: auth.currentEmail?.substringBefore("@") ?: "You",
                        lifetimeXp = profile?.lifetimeXp ?: 0,
                        streak = profile?.currentFocusStreak ?: 0,
                        history = history,
                        live = live,
                        reward = reward,
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(loading = false, error = true)
                },
            )
        }
    }

    /**
     * Builds the focus-history CSV off the main thread. Returns null if there's
     * no session or the read fails; the caller (which owns a Context) shares it.
     */
    suspend fun buildCsv(): app.stackd.data.profile.CsvExport? {
        val userId = auth.currentUserId ?: return null
        return runCatching { profiles.exportFocusHistoryCsv(userId) }.getOrNull()
    }

    /** Claims today's login reward; the RPC owns streak math and the XP grant. */
    fun claimReward() {
        val current = _state.value
        if (current.claiming || current.reward?.claimedToday != false) return
        _state.value = current.copy(claiming = true, claimNotice = null)
        viewModelScope.launch {
            val result = runCatching { profiles.claimDailyReward() }.getOrNull()
            if (result != null) {
                _state.value = _state.value.copy(
                    claiming = false,
                    claimNotice = "+${result.rewardXp} XP · Day ${result.dayOfStreak}",
                )
                load()
            } else {
                _state.value = _state.value.copy(claiming = false, claimNotice = "Claim failed. Retry.")
            }
        }
    }
}

/** Claim path + tiny tuple the fan-out load needs. */
private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

private operator fun <A, B, C, D> Quad<A, B, C, D>.component1() = a
private operator fun <A, B, C, D> Quad<A, B, C, D>.component2() = b
private operator fun <A, B, C, D> Quad<A, B, C, D>.component3() = c
private operator fun <A, B, C, D> Quad<A, B, C, D>.component4() = d
