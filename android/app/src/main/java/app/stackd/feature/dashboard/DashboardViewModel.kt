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
    /** The caller's recent rooms — a listed path back to a lobby/active room. */
    val myRooms: List<app.stackd.data.room.RoomListItem> = emptyList(),
    /** My-rooms paging (web MyRoomsPanel: 8 per page, Prev/Next). */
    val myRoomsPage: Int = 0,
    val myRoomsHasMore: Boolean = false,
    val myRoomsError: Boolean = false,
    /** For the HOST/GUEST tag on my-rooms rows. */
    val meId: String? = null,
    /** Daily login reward — null while loading or if the read failed. */
    val reward: RewardStatus? = null,
    val claiming: Boolean = false,
    /** One-shot claim feedback line, e.g. "+40 XP · Day 3". */
    val claimNotice: String? = null,
    /**
     * LLM-written next-session recommendation from the web AI route. Null until
     * it lands (or forever, if the AI backend is unreachable); the Atlas card
     * falls back to the local heuristic over [history] in that case.
     */
    val aiRecommendation: app.stackd.feature.insights.SessionRecommendation? = null,
    /**
     * LLM-written ledger insights from the web AI route. Pure LLM (no local
     * heuristic to fall back to), so the card only renders once this lands and
     * stays hidden if the AI backend is unreachable.
     */
    val aiInsights: app.stackd.data.ai.DashboardInsights? = null,
    val aiRecLoading: Boolean = true,
    val aiRecError: Boolean = false,
    val aiInsLoading: Boolean = true,
    val aiInsError: Boolean = false,
    /** Null until the entitlement read lands; the upgrade card needs a definite false. */
    val isPremium: Boolean? = null,
    val upgradeDismissed: Boolean = false,
    val atlasDismissed: Boolean = false,
    val greeting: GreetingExtras = GreetingExtras(),
    val prestige: app.stackd.data.progression.PrestigeStatus? = null,
    val ascending: Boolean = false,
    val prestigeNotice: String? = null,
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
    private val rooms: app.stackd.data.room.RoomRepository,
    private val ai: app.stackd.data.ai.AiRepository,
    private val cache: app.stackd.core.cache.MemoryCache,
    private val premium: app.stackd.data.premium.PremiumRepository,
    private val prestigeRepo: app.stackd.data.progression.PrestigeRepository,
    private val client: io.github.jan.supabase.SupabaseClient,
    private val prefs: android.content.SharedPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        _state.value = _state.value.copy(
            atlasDismissed = prefs.getLong(ATLAS_KEY, 0L) > System.currentTimeMillis(),
            upgradeDismissed = prefs.getBoolean(UPGRADE_KEY, false),
        )
        load()
        fetchAiRecommendation()
        fetchAiInsights()
        viewModelScope.launch {
            val ent = runCatching { premium.myEntitlement() }.getOrNull()
            _state.value = _state.value.copy(isPremium = ent?.isPremium)
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(greeting = loadGreetingExtras(client))
        }
        refreshPrestige()
    }

    fun refreshPrestige() {
        viewModelScope.launch {
            val st = runCatching { prestigeRepo.status() }.getOrNull() ?: return@launch
            _state.value = _state.value.copy(prestige = st)
        }
    }

    fun ascend() {
        if (_state.value.ascending) return
        _state.value = _state.value.copy(ascending = true, prestigeNotice = null)
        viewModelScope.launch {
            val level = prestigeRepo.prestigeUp()
            _state.value = _state.value.copy(
                ascending = false,
                prestigeNotice = if (level != null) "Prestige $level · ascended" else "Prestige failed. Retry.",
            )
            if (level != null) {
                app.stackd.core.feedback.Sfx.play(app.stackd.core.feedback.Sfx.Kind.ACHIEVEMENT)
                refreshPrestige(); load()
            }
        }
    }

    fun dismissAtlas() {
        prefs.edit().putLong(ATLAS_KEY, System.currentTimeMillis() + 6 * 3600_000L).apply()
        _state.value = _state.value.copy(atlasDismissed = true)
    }

    fun dismissUpgrade() {
        prefs.edit().putBoolean(UPGRADE_KEY, true).apply()
        _state.value = _state.value.copy(upgradeDismissed = true)
    }

    /** Pulls the LLM ledger insights; [fresh] bypasses the 30-min cache (Regenerate). */
    fun fetchAiInsights(fresh: Boolean = false) {
        _state.value = _state.value.copy(aiInsLoading = true, aiInsError = false)
        viewModelScope.launch {
            val insights = ai.dashboardInsights(fresh)
            _state.value = _state.value.copy(
                aiInsights = insights ?: _state.value.aiInsights,
                aiInsLoading = false,
                aiInsError = insights == null,
            )
        }
    }

    /**
     * Pulls the LLM recommendation for the Atlas card, best-effort. Fire-and-
     * forget: the card already shows the local heuristic; this upgrades it if
     * the AI backend answers, and silently no-ops if it doesn't.
     */
    fun fetchAiRecommendation(fresh: Boolean = false) {
        _state.value = _state.value.copy(aiRecLoading = true, aiRecError = false)
        viewModelScope.launch {
            val rec = ai.recommendNextSession(fresh)
            if (rec == null) {
                _state.value = _state.value.copy(aiRecLoading = false, aiRecError = true)
                return@launch
            }
            _state.value = _state.value.copy(
                aiRecLoading = false,
                aiRecommendation = app.stackd.feature.insights.SessionRecommendation(
                    durationMinutes = rec.durationMinutes,
                    topic = rec.topic,
                    rationale = rec.rationale,
                    confidence = rec.confidence,
                    basedOnSessions = rec.basedOnSessions,
                ),
            )
        }
    }

    private fun cacheKey(userId: String) = "dashboard:$userId"

    fun load() {
        val userId = auth.currentUserId
        if (userId == null) {
            // No session — nothing to show, and not an error the user can fix
            // here. The nav guard routes signed-out users away before this.
            _state.value = DashboardUiState(loading = false)
            return
        }
        // Stale-while-revalidate: seed from the last cached state so a re-entry
        // shows data instantly instead of a spinner, then revalidate below.
        val cached: DashboardUiState? = cache.get(cacheKey(userId))
        val cur = _state.value
        // Ledger from cache, but keep the live AI/extras fields the cache doesn't own.
        _state.value = (cached?.copy(
            aiRecommendation = cur.aiRecommendation, aiInsights = cur.aiInsights,
            aiRecLoading = cur.aiRecLoading, aiRecError = cur.aiRecError,
            aiInsLoading = cur.aiInsLoading, aiInsError = cur.aiInsError,
            isPremium = cur.isPremium, upgradeDismissed = cur.upgradeDismissed,
            atlasDismissed = cur.atlasDismissed, greeting = cur.greeting,
            prestige = cur.prestige, ascending = cur.ascending, prestigeNotice = cur.prestigeNotice,
        ) ?: cur).copy(loading = cached == null, error = false)
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
                    // My-rooms is best-effort: a failed read degrades to empty
                    // rather than sinking the whole dashboard load.
                    val roomsDef = async {
                        runCatching { rooms.listMyRooms(pageSize = MY_ROOMS_PAGE) }
                            .getOrDefault(emptyList<app.stackd.data.room.RoomListItem>() to false)
                    }
                    Quint(
                        profileDef.await(), historyDef.await(), liveDef.await(),
                        rewardDef.await(), roomsDef.await(),
                    )
                }
            }.fold(
                onSuccess = { (profile, history, live, reward, myRooms) ->
                    // Copy over current state (not a fresh instance) so the
                    // concurrently-fetched AI fields aren't wiped if they landed
                    // before this load's fan-out returned. Clear the transient
                    // claim/error flags a completed load implies.
                    val fresh = _state.value.copy(
                        loading = false,
                        error = false,
                        name = profile?.displayName?.takeIf { it.isNotBlank() }
                            ?: auth.currentEmail?.substringBefore("@") ?: "You",
                        lifetimeXp = profile?.lifetimeXp ?: 0,
                        streak = profile?.currentFocusStreak ?: 0,
                        history = history,
                        live = live,
                        reward = reward,
                        myRooms = myRooms.first,
                        myRoomsHasMore = myRooms.second,
                        myRoomsPage = 0,
                        myRoomsError = false,
                        meId = userId,
                    )
                    _state.value = fresh
                    // Cache the ledger only — AI cards are cheap to refetch and a
                    // stale recommendation/insight shouldn't persist across sessions.
                    cache.put(
                        cacheKey(userId),
                        fresh.copy(aiRecommendation = null, aiInsights = null),
                    )
                },
                onFailure = {
                    // Keep showing stale data if we have it; only surface the
                    // error card when there was nothing cached to fall back on.
                    _state.value = _state.value.copy(
                        loading = false,
                        error = cached == null,
                    )
                },
            )
        }
    }

    /** Prev/Next on the my-rooms panel. Failure keeps the current page and flags Retry. */
    fun myRoomsGoTo(page: Int) {
        if (page < 0) return
        viewModelScope.launch {
            runCatching { rooms.listMyRooms(page = page, pageSize = MY_ROOMS_PAGE) }.fold(
                onSuccess = { (items, more) ->
                    _state.value = _state.value.copy(
                        myRooms = items, myRoomsHasMore = more, myRoomsPage = page, myRoomsError = false,
                    )
                },
                onFailure = { _state.value = _state.value.copy(myRoomsError = true) },
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
            app.stackd.core.feedback.Sfx.play(
                if (result != null) app.stackd.core.feedback.Sfx.Kind.XP else app.stackd.core.feedback.Sfx.Kind.ERROR,
            )
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

private const val MY_ROOMS_PAGE = 8
private const val ATLAS_KEY = "atlas_dismissed_until"
private const val UPGRADE_KEY = "upgrade_card_dismissed"

/** Claim path + tiny tuple the fan-out load needs. */
private data class Quint<A, B, C, D, E>(
    val a: A, val b: B, val c: C, val d: D, val e: E,
)

private operator fun <A, B, C, D, E> Quint<A, B, C, D, E>.component1() = a
private operator fun <A, B, C, D, E> Quint<A, B, C, D, E>.component2() = b
private operator fun <A, B, C, D, E> Quint<A, B, C, D, E>.component3() = c
private operator fun <A, B, C, D, E> Quint<A, B, C, D, E>.component4() = d
private operator fun <A, B, C, D, E> Quint<A, B, C, D, E>.component5() = e
