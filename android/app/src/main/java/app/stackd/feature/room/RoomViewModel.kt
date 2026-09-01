package app.stackd.feature.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stackd.core.AppContainer
import app.stackd.core.workmanager.FinalizePayload
import app.stackd.data.room.BreakRow
import app.stackd.data.room.ParticipantRow
import app.stackd.data.room.RoomRow
import app.stackd.data.room.RoomStatus
import app.stackd.feature.room.session.BreachReason
import app.stackd.feature.room.session.BreachSeverity
import app.stackd.feature.room.session.EnforcementMode
import app.stackd.feature.room.session.FinalizeInputs
import app.stackd.feature.room.session.FocusScore
import app.stackd.feature.room.session.FocusSessionService
import app.stackd.feature.room.session.SessionClock
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/** Where the room screen is in the session lifecycle. */
enum class RoomPhase { LOADING, LOBBY, COUNTDOWN, ACTIVE, ENDED, ERROR }

data class RoomUiState(
    val phase: RoomPhase = RoomPhase.LOADING,
    val error: String? = null,
    val room: RoomRow? = null,
    val participants: List<ParticipantRow> = emptyList(),
    val breaks: List<BreakRow> = emptyList(),
    val meId: String? = null,
    val countdown: Int? = null,
    /** Recomputed each tick from the server start — never a local accumulator. */
    val remainingSeconds: Long = 0,
    val elapsedSeconds: Long = 0,
    val armed: Boolean = false,
    val calibrating: Boolean = false,
    /** Null until this user's result is computed at session end. */
    val result: FocusScore.Result? = null,
    val resultQueuedOffline: Boolean = false,
    /** History row id from finalize — gates the post-session notes/tags form. */
    val historyId: String? = null,
    val savingSessionMeta: Boolean = false,
    val sessionMetaSaved: Boolean = false,
    /** A device signal that isn't guarding the stack — surfaced as a warning. */
    val sensorWarning: String? = null,

    // Phase 2 panels.
    val events: List<app.stackd.data.room.RoomEvent> = emptyList(),
    val milestones: List<app.stackd.data.room.Milestone> = emptyList(),
    val joinRequests: List<app.stackd.data.room.JoinRequest> = emptyList(),
    val workspace: List<app.stackd.data.room.WorkspaceItem> = emptyList(),
    /** user_ids that have marked themselves ready in the lobby. */
    val readyIds: Set<String> = emptySet(),
    val isModerator: Boolean = false,
    /** Banked focus seconds across all finished sessions in this room. */
    val bankedFocusSeconds: Long = 0,
    val moderatorIds: List<String> = emptyList(),
    val schedule: List<app.stackd.data.room.ScheduledEvent> = emptyList(),
    /** True while the header's edit form is saving. */
    val savingMeta: Boolean = false,
) {
    val me: ParticipantRow? get() = participants.firstOrNull { it.userId == meId }
    val isHost: Boolean get() = room != null && meId != null && room.hostId == meId
    val iBreached: Boolean get() = me?.breached == true
    val code: String get() = room?.code.orEmpty()
    val iAmReady: Boolean get() = meId != null && meId in readyIds

    /** Participants still in the room (haven't left). */
    val present: List<ParticipantRow> get() = participants.filter { it.leftAt == null }

    /** Collective focus across everyone, in seconds — the shared-goal numerator. */
    val collectiveFocusSeconds: Long get() {
        val started = app.stackd.core.parseIsoMillis(room?.startedAt) ?: return 0
        if (room?.statusEnum != RoomStatus.ACTIVE) return 0
        val each = ((System.currentTimeMillis() - started) / 1000).coerceAtLeast(0)
        return each * present.count { !it.breached }
    }

    val goalHours: Int get() = ((room?.collectiveGoalSeconds ?: 0) / 3600).toInt()
}

/**
 * The core session loop, ported from the web's `room.$code.tsx`.
 *
 * Realtime is ViewModel-scoped: the channel is subscribed in [enter] and torn
 * down in [onCleared], so it lives exactly as long as the screen — the web
 * scopes it to the component the same way.
 *
 * Two things must match the web exactly or scores diverge:
 *  - a severe breach records + disarms this user but does NOT end the session;
 *    only the host or the timer ends it,
 *  - `started_at` is the server's, `ended_at` is the host client's — the finalize
 *    split reads both, see [FinalizeInputs].
 */
class RoomViewModel(
    private val container: AppContainer,
    private val code: String,
) : ViewModel() {

    private val rooms = container.rooms
    private val profiles = container.profiles
    private val auth = container.auth
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(RoomUiState())
    val state: StateFlow<RoomUiState> = _state.asStateFlow()

    private var channel: RealtimeChannel? = null
    private var mode: EnforcementMode = EnforcementMode.ABSOLUTE

    // Optim 02: one-write guards, mirroring the web's completion/finalize refs.
    private var completionLock = false
    private var finalizeLock = false

    /** Whether the countdown foreground service is currently running. */
    private var foregroundTimerRunning = false

    init {
        enter()
    }

    private fun enter() {
        viewModelScope.launch {
            mode = if (container.settings.enforcementMode.first() == EnforcementMode.GENTLE.wire) {
                EnforcementMode.GENTLE
            } else {
                EnforcementMode.ABSOLUTE
            }
            val userId = auth.currentUserId
            if (userId == null) {
                _state.value = _state.value.copy(phase = RoomPhase.ERROR, error = "You're not signed in.")
                return@launch
            }

            // claim_room_seat looks up the room by code AND seats the caller in
            // one definer transaction, so the participant/break reads below pass
            // RLS. A plain lookup would fail for a non-member.
            val claim = runCatching { rooms.claimRoomSeat(code) }
            val room = claim.getOrNull()
            if (room == null) {
                // claim_room_seat raises named errors; map the ones a user can
                // act on to real copy instead of a generic "not found".
                val raw = claim.exceptionOrNull()?.message ?: ""
                val message = when {
                    "needs_approval" in raw ->
                        "This room requires the host's approval to join. Ask the host to approve your request."
                    "blocked" in raw ->
                        "You can't join this room."
                    else -> "Room not found, or it has already ended."
                }
                _state.value = _state.value.copy(phase = RoomPhase.ERROR, error = message)
                return@launch
            }

            val participants = runCatching { rooms.listParticipants(room.id) }.getOrDefault(emptyList())
            val breaks = runCatching { rooms.listBreaks(room.id) }.getOrDefault(emptyList())
            val events = runCatching { rooms.listRoomEvents(room.id) }.getOrDefault(emptyList())
            val milestones = runCatching { rooms.listMilestones(room.id) }.getOrDefault(emptyList())
            // Join requests only return rows to a moderator (RLS); a non-empty
            // list is itself the signal that this user can moderate.
            val joinRequests = runCatching { rooms.listJoinRequests(room.id) }.getOrDefault(emptyList())
            val workspace = runCatching { rooms.listWorkspace(room.id) }.getOrDefault(emptyList())
            val banked = runCatching { rooms.sumRoomFocusSeconds(room.id) }.getOrDefault(0L)
            val moderatorIds = runCatching { rooms.listModeratorIds(room.id) }.getOrDefault(emptyList())
            val schedule = runCatching { rooms.listSchedule(room.id) }.getOrDefault(emptyList())

            _state.value = _state.value.copy(
                phase = phaseFor(room),
                room = room,
                participants = participants,
                breaks = breaks,
                events = events,
                milestones = milestones,
                joinRequests = joinRequests,
                workspace = workspace,
                bankedFocusSeconds = banked,
                moderatorIds = moderatorIds,
                schedule = schedule,
                readyIds = readyFromEvents(events),
                isModerator = room.hostId == userId || joinRequests.isNotEmpty(),
                meId = userId,
                error = null,
            )
            subscribe(room.id)
            startTicker()
            startHeartbeat()
            reconcile() // one immediate compute so the timer isn't blank for a second
        }
    }

    /**
     * Reconstructs the ready set from the event log: the latest ready/unready/
     * left event per user wins. The web keeps this in a live Set fed by realtime;
     * seeding it from history means a late joiner sees who's already ready.
     */
    private fun readyFromEvents(events: List<app.stackd.data.room.RoomEvent>): Set<String> {
        val ready = mutableSetOf<String>()
        // Oldest first so later events override earlier ones.
        events.sortedBy { it.createdAt }.forEach { e ->
            val uid = e.actorId ?: return@forEach
            when (e.kind) {
                "ready" -> ready.add(uid)
                "unready", "left" -> ready.remove(uid)
            }
        }
        return ready
    }

    /** Re-reads the room row from the server to correct drift after a background freeze. */
    fun reconcile() {
        val roomId = _state.value.room?.id ?: return
        viewModelScope.launch {
            runCatching { rooms.getRoom(roomId) }.getOrNull()?.let { applyRoom(it) }
            tick()
        }
    }

    /* ---------------------------------------------------------------------- */
    /*  Realtime                                                               */
    /* ---------------------------------------------------------------------- */

    private fun subscribe(roomId: String) {
        val ch = rooms.roomChannel(roomId)
        channel = ch.channel

        ch.rooms.onEach { action ->
            decode<RoomRow>(recordOf(action))?.let { applyRoom(it) }
        }.launchIn(viewModelScope)

        ch.participants.onEach { action ->
            when (action) {
                is PostgresAction.Delete ->
                    decode<ParticipantRow>(action.oldRecord)?.let { removeParticipant(it.id) }
                else -> decode<ParticipantRow>(recordOf(action))?.let { upsertParticipant(it) }
            }
        }.launchIn(viewModelScope)

        ch.breaks.onEach { action ->
            decode<BreakRow>(action.record)?.let { addBreak(it) }
        }.launchIn(viewModelScope)

        ch.events.onEach { action ->
            decode<app.stackd.data.room.RoomEvent>(action.record)?.let { addEvent(it) }
        }.launchIn(viewModelScope)

        ch.milestones.onEach { action ->
            decode<app.stackd.data.room.Milestone>(action.record)?.let { addMilestone(it) }
        }.launchIn(viewModelScope)

        ch.joinRequests.onEach { _ ->
            // Any change to the request table → re-pull the pending list; the set
            // is small and a moderator's RLS scopes it. Simpler than diffing.
            runCatching { rooms.listJoinRequests(roomId) }.getOrNull()?.let {
                _state.value = _state.value.copy(joinRequests = it, isModerator = _state.value.isModerator || it.isNotEmpty())
            }
        }.launchIn(viewModelScope)

        viewModelScope.launch { runCatching { ch.channel.subscribe() } }
    }

    private fun addEvent(event: app.stackd.data.room.RoomEvent) {
        val list = _state.value.events
        if (list.any { it.id == event.id }) return
        val next = (listOf(event) + list).sortedByDescending { it.createdAt }.take(EVENT_CAP)
        // A ready/unready/left event also moves the ready set live.
        val ready = _state.value.readyIds.toMutableSet()
        event.actorId?.let { uid ->
            when (event.kind) {
                "ready" -> ready.add(uid)
                "unready", "left" -> ready.remove(uid)
            }
        }
        _state.value = _state.value.copy(events = next, readyIds = ready)
    }

    private fun addMilestone(m: app.stackd.data.room.Milestone) {
        val list = _state.value.milestones
        if (list.any { it.id == m.id }) return
        _state.value = _state.value.copy(
            milestones = (listOf(m) + list).sortedByDescending { it.reachedAt }.take(EVENT_CAP),
        )
    }

    /**
     * Writes this participant's heartbeat every 15s while the session is active,
     * so the roster's 45s disconnect threshold reflects real presence. Only runs
     * during ACTIVE — a lobby or ended room has nothing to keep alive.
     */
    private fun startHeartbeat() {
        viewModelScope.launch {
            while (isActive) {
                val s = _state.value
                if (s.room?.statusEnum == RoomStatus.ACTIVE && s.meId != null) {
                    runCatching { rooms.heartbeat(s.room.id, s.meId) }
                }
                kotlinx.coroutines.delay(HEARTBEAT_MS)
            }
        }
    }

    private fun applyRoom(next: RoomRow) {
        val prev = _state.value.room
        // Stale-row rejection: a replayed older row on reconnect would flip an
        // active room back to lobby and reset the timer. `updated_at` is ISO-8601
        // and sorts lexically, so a string compare orders events correctly —
        // exactly the web's guard.
        if (prev?.updatedAt != null && next.updatedAt != null && next.updatedAt < prev.updatedAt) return

        _state.value = _state.value.copy(room = next, phase = phaseFor(next, _state.value))
        if (next.statusEnum == RoomStatus.ACTIVE) {
            armIfNeeded()
            startForegroundTimer(next)
        }
        if (next.statusEnum == RoomStatus.COMPLETE || next.statusEnum == RoomStatus.ABORTED) {
            _state.value = _state.value.copy(armed = false)
            stopForegroundTimer()
            maybeFinalize()
        }
        tick()
    }

    /**
     * The Android counterpart to the web's `use-lock-screen-timer`: a foreground
     * service whose ongoing notification counts down to the session's end,
     * visible on the lock screen and while the app is backgrounded. Anchored to
     * the server's end time so the OS renders the countdown without us waking
     * every second. Started once the session is genuinely active (a real
     * `started_at`), idempotent on repeated ACTIVE rows.
     */
    private fun startForegroundTimer(room: RoomRow) {
        val started = app.stackd.core.parseIsoMillis(room.startedAt) ?: return
        if (foregroundTimerRunning) return
        foregroundTimerRunning = true
        FocusSessionService.start(
            context = container.appContextForWork,
            roomCode = room.code,
            endsAtMillis = SessionClock.endsAtMillis(started, room.targetDurationSeconds),
        )
    }

    private fun stopForegroundTimer() {
        if (!foregroundTimerRunning) return
        foregroundTimerRunning = false
        FocusSessionService.stop(container.appContextForWork)
    }

    private fun upsertParticipant(row: ParticipantRow) {
        val list = _state.value.participants
        val next = if (list.any { it.id == row.id }) {
            list.map { if (it.id == row.id) row else it }
        } else {
            (list + row).sortedBy { it.joinedAt }
        }
        _state.value = _state.value.copy(participants = next)
        // If the realtime update is my own breach landing, disarm locally.
        if (row.userId == _state.value.meId && row.breached) {
            _state.value = _state.value.copy(armed = false)
        }
    }

    private fun removeParticipant(id: String) {
        _state.value = _state.value.copy(participants = _state.value.participants.filterNot { it.id == id })
    }

    private fun addBreak(row: BreakRow) {
        val list = _state.value.breaks
        if (list.any { it.id == row.id }) return
        _state.value = _state.value.copy(
            breaks = (listOf(row) + list).sortedByDescending { it.at }.take(BREAK_FEED_CAP),
        )
    }

    /* ---------------------------------------------------------------------- */
    /*  Timer                                                                  */
    /* ---------------------------------------------------------------------- */

    private fun startTicker() {
        viewModelScope.launch {
            while (isActive) {
                tick()
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    private fun tick() {
        val room = _state.value.room ?: return
        val started = app.stackd.core.parseIsoMillis(room.startedAt) ?: 0L
        val now = System.currentTimeMillis()
        val remaining = SessionClock.remainingSeconds(started, room.targetDurationSeconds, now)
        val elapsed = SessionClock.elapsedSeconds(started, now)
        _state.value = _state.value.copy(remainingSeconds = remaining, elapsedSeconds = elapsed)

        // Host auto-completes an expired session — compare-and-set on the server
        // keeps a double-fire idempotent.
        if (_state.value.isHost && room.statusEnum == RoomStatus.ACTIVE &&
            SessionClock.isExpired(started, room.targetDurationSeconds, now)
        ) {
            completeSession()
        }
    }

    /* ---------------------------------------------------------------------- */
    /*  Host controls                                                          */
    /* ---------------------------------------------------------------------- */

    /** Host: begin the 3-2-1 countdown, then start the server session. */
    fun startRitual() {
        if (!_state.value.isHost || _state.value.room?.statusEnum != RoomStatus.LOBBY) return
        viewModelScope.launch {
            _state.value = _state.value.copy(phase = RoomPhase.COUNTDOWN)
            for (c in 3 downTo 1) {
                _state.value = _state.value.copy(countdown = c)
                kotlinx.coroutines.delay(1000)
            }
            _state.value = _state.value.copy(countdown = null)
            // The server sets started_at from its own clock — every score derives
            // from it, so it must never be a device timestamp.
            runCatching { rooms.startSession(_state.value.room!!.id) }
                .onFailure { err ->
                    // A swallowed failure here looks like "the start button does
                    // nothing" — surface it and fall back to the lobby. Raw
                    // Postgres text leaks schema internals (web's db-error.ts
                    // does the same scrubbing); full detail goes to logcat only.
                    android.util.Log.e("StackdRoom", "start_focus_session failed", err)
                    _state.value = _state.value.copy(
                        phase = RoomPhase.LOBBY,
                        error = "Couldn't start the session. Check your connection and retry.",
                    )
                    return@launch
                }
            armIfNeeded()
        }
    }

    fun endSession() {
        if (!_state.value.isHost) return
        completeSession()
    }

    fun abortSession() {
        if (!_state.value.isHost) return
        val room = _state.value.room ?: return
        viewModelScope.launch {
            runCatching { rooms.abortSession(room.id) }
        }
    }

    private fun completeSession() {
        val room = _state.value.room ?: return
        if (completionLock) return
        completionLock = true
        viewModelScope.launch {
            // ended_at is stamped by the finish_focus_room RPC from the server
            // clock — the same clock that wrote started_at, so the finalize
            // split can never be skewed by a device clock.
            runCatching { rooms.completeSession(room.id) }
                .onFailure { completionLock = false }
        }
    }

    /* ---------------------------------------------------------------------- */
    /*  Sensors / breach                                                       */
    /* ---------------------------------------------------------------------- */

    private fun armIfNeeded() {
        val s = _state.value
        if (s.room?.statusEnum == RoomStatus.ACTIVE && !s.iBreached && !s.armed) {
            _state.value = _state.value.copy(armed = true, calibrating = true, phase = RoomPhase.ACTIVE)
        }
    }

    /** The detector reports it has calibrated and tilt detection is live. */
    fun onCalibrated() {
        _state.value = _state.value.copy(calibrating = false)
    }

    fun onSensorWarning(message: String?) {
        _state.value = _state.value.copy(sensorWarning = message)
    }

    val enforcementMode: EnforcementMode get() = mode

    /**
     * A detected breach. Records it server-side and disarms this user locally —
     * but does NOT end the session. Only the host or the expired timer transitions
     * the room to complete; a breach just marks this participant and stops their
     * own detection. Exactly the web's `handleBreach`.
     */
    fun onBreach(reason: BreachReason, severity: BreachSeverity) {
        val s = _state.value
        val room = s.room ?: return
        val me = s.me ?: return
        if (severity == BreachSeverity.SEVERE && me.breached) return

        val integrity = if (room.targetDurationSeconds > 0) {
            ((s.elapsedSeconds.toDouble() / room.targetDurationSeconds) * 100).toInt().coerceIn(0, 100)
        } else 0

        viewModelScope.launch {
            runCatching {
                rooms.recordBreach(
                    roomId = room.id,
                    participantId = me.id,
                    reason = reason.wire,
                    severity = severity.wire,
                    integrity = integrity,
                )
            }
            if (severity == BreachSeverity.SEVERE) {
                _state.value = _state.value.copy(armed = false)
            }
        }
    }

    /* ---------------------------------------------------------------------- */
    /*  Finalize                                                               */
    /* ---------------------------------------------------------------------- */

    private fun maybeFinalize() {
        val s = _state.value
        val room = s.room ?: return
        val me = s.me ?: return
        val userId = s.meId ?: return
        if (room.statusEnum != RoomStatus.COMPLETE && room.statusEnum != RoomStatus.ABORTED) return
        if (finalizeLock) return
        finalizeLock = true

        viewModelScope.launch {
            val started = app.stackd.core.parseIsoMillis(room.startedAt) ?: 0L
            val ended = app.stackd.core.parseIsoMillis(room.endedAt) ?: System.currentTimeMillis()
            val breachAt = app.stackd.core.parseIsoMillis(me.breachAt)
            val split = FinalizeInputs.compute(started, ended, me.breached, breachAt)

            val myBreachCount = s.breaks.count { it.userId == userId }
            val mySevere = s.breaks.count { it.userId == userId && it.isSevere }
            val myMinor = myBreachCount - mySevere

            val result = FocusScore.compute(
                targetSeconds = room.targetDurationSeconds.toDouble(),
                focusSeconds = split.focusMillis / 1000.0,
                severeBreaches = mySevere,
                minorBreaches = myMinor,
                abandonmentSeconds = split.abandonmentMillis / 1000.0,
            )
            _state.value = _state.value.copy(result = result, phase = RoomPhase.ENDED)

            val abandonmentSeconds = (split.abandonmentMillis / 1000L).toInt().coerceAtLeast(0)
            val payload = FinalizePayload(
                roomId = room.id,
                score = result.score,
                xp = result.xp,
                durationSeconds = result.focusSecondsInt,
                breachesCount = myBreachCount,
                tier = result.tier.key,
                scoringVersion = result.scoringVersion,
                owner = userId,
                queuedAt = System.currentTimeMillis(),
                abandonmentSeconds = abandonmentSeconds,
            )

            val historyId = runCatching {
                rooms.finalizeSession(
                    roomId = room.id,
                    score = result.score,
                    xp = result.xp,
                    durationSeconds = result.focusSecondsInt,
                    breachesCount = myBreachCount,
                    tier = result.tier.key,
                    scoringVersion = result.scoringVersion,
                    abandonmentSeconds = abandonmentSeconds,
                )
            }.getOrNull()
            val submitted = historyId != null

            if (submitted) {
                // Keep the id so the Ended screen's notes/tags form can attach
                // metadata to this exact row.
                _state.value = _state.value.copy(historyId = historyId)
                // First finished session gates the Start-screen intro tip.
                container.settings.markCompletedSession()
                container.finalizeQueue // ensure init; drains on next flush
            } else {
                // Offline or refused — park it and release the lock so a retry can
                // run. WorkManager drains it when connectivity returns.
                container.finalizeQueue.enqueue(payload)
                app.stackd.core.workmanager.FinalizeQueueWorker.flush(
                    container.appContextForWork, userId,
                )
                container.settings.markCompletedSession()
                finalizeLock = false
                _state.value = _state.value.copy(resultQueuedOffline = true)
            }
        }
    }

    /** Attaches notes + tags to the just-finished session (Ended phase). */
    fun saveSessionMeta(notes: String, tagsRaw: String) {
        val historyId = _state.value.historyId ?: return
        if (_state.value.savingSessionMeta) return
        _state.value = _state.value.copy(savingSessionMeta = true)
        val tags = tagsRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        viewModelScope.launch {
            val ok = runCatching { rooms.updateSessionMeta(historyId, notes, tags) }.isSuccess
            _state.value = _state.value.copy(savingSessionMeta = false, sessionMetaSaved = ok)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Leaving the screen must not leave a countdown notification stranded.
        stopForegroundTimer()
        channel?.let { ch -> viewModelScope.launch { runCatching { ch.unsubscribe() } } }
    }

    /* ---------------------------------------------------------------------- */
    /*  Helpers                                                                */
    /* ---------------------------------------------------------------------- */

    private fun phaseFor(room: RoomRow, current: RoomUiState = _state.value): RoomPhase =
        when (room.statusEnum) {
            RoomStatus.LOBBY -> if (current.countdown != null) RoomPhase.COUNTDOWN else RoomPhase.LOBBY
            RoomStatus.ACTIVE -> RoomPhase.ACTIVE
            RoomStatus.COMPLETE, RoomStatus.ABORTED -> RoomPhase.ENDED
        }

    private fun recordOf(action: PostgresAction) = when (action) {
        is PostgresAction.Insert -> action.record
        is PostgresAction.Update -> action.record
        is PostgresAction.Select -> action.record
        is PostgresAction.Delete -> action.oldRecord
    }

    private inline fun <reified T> decode(record: JsonObject): T? =
        runCatching { json.decodeFromJsonElement<T>(record) }.getOrNull()

    private fun nowIso(): String =
        java.time.Instant.ofEpochMilli(System.currentTimeMillis()).toString()

    /* ---------------------------------------------------------------------- */
    /*  Phase 2 panel actions                                                  */
    /* ---------------------------------------------------------------------- */

    /** Lobby ready toggle — optimistic, then records the event server-side. */
    fun toggleReady() {
        val s = _state.value
        val room = s.room ?: return
        val uid = s.meId ?: return
        if (room.statusEnum != RoomStatus.LOBBY) return
        val nowReady = uid !in s.readyIds
        _state.value = s.copy(
            readyIds = if (nowReady) s.readyIds + uid else s.readyIds - uid,
        )
        viewModelScope.launch {
            // KNOWN GAP: migration 20260825010000 rejects `ready`/`unready`
            // kinds for plain members (the web hits the same wall), so this
            // event write fails until the allowlist migration lands. The local
            // flip is kept — NOT rolled back — so the lobby UX still works on
            // this device; cross-device ready sync resumes when the DB allows
            // the kinds again.
            runCatching { rooms.recordRoomEvent(room.id, if (nowReady) "ready" else "unready") }
                .onFailure {
                    android.util.Log.w("StackdRoom", "ready event rejected (known DB allowlist gap)", it)
                }
        }
    }

    /** Host: save the header's edit form. Cosmetic columns only. */
    fun saveRoomMeta(
        title: String,
        description: String,
        pinnedMessage: String,
        goalHours: Int,
        visibility: String,
    ) {
        val room = _state.value.room ?: return
        if (!_state.value.isHost) return
        _state.value = _state.value.copy(savingMeta = true)
        viewModelScope.launch {
            runCatching {
                rooms.updateRoomMeta(
                    roomId = room.id,
                    title = title,
                    description = description,
                    pinnedMessage = pinnedMessage,
                    collectiveGoalSeconds = if (goalHours > 0) goalHours * 3600L else null,
                    visibility = visibility,
                )
            }.onSuccess {
                // Optimistic local copy; the rooms realtime UPDATE reconciles.
                _state.value = _state.value.copy(
                    savingMeta = false,
                    room = _state.value.room?.copy(
                        title = title.ifBlank { null },
                        description = description.ifBlank { null },
                        pinnedMessage = pinnedMessage.ifBlank { null },
                        collectiveGoalSeconds = if (goalHours > 0) goalHours * 3600L else null,
                        visibility = visibility,
                    ),
                )
            }.onFailure {
                _state.value = _state.value.copy(savingMeta = false)
            }
        }
    }

    /** Host: add a scheduled event, then reload the list. */
    fun addScheduledEvent(title: String, startsAtIso: String, durationMinutes: Int) {
        val room = _state.value.room ?: return
        val uid = _state.value.meId ?: return
        if (title.isBlank()) return
        viewModelScope.launch {
            runCatching {
                rooms.createScheduledEvent(room.id, uid, title.trim(), startsAtIso, durationMinutes)
            }.onSuccess {
                val rows = runCatching { rooms.listSchedule(room.id) }.getOrDefault(emptyList())
                _state.value = _state.value.copy(schedule = rows)
            }
        }
    }

    fun respondToJoinRequest(requestId: String, approve: Boolean) {
        // Optimistically drop the row; realtime will reconcile the truth.
        _state.value = _state.value.copy(
            joinRequests = _state.value.joinRequests.filterNot { it.id == requestId },
        )
        viewModelScope.launch {
            runCatching { rooms.respondToJoinRequest(requestId, approve) }
        }
    }

    fun addWorkspaceItem(kind: String, content: String, url: String? = null) {
        val room = _state.value.room ?: return
        val text = content.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            runCatching { rooms.addWorkspaceItem(room.id, kind, text, url) }.getOrNull()?.let { item ->
                _state.value = _state.value.copy(workspace = listOf(item) + _state.value.workspace)
            }
        }
    }

    fun toggleWorkspaceDone(id: String) {
        val current = _state.value.workspace.firstOrNull { it.id == id } ?: return
        val next = !current.done
        _state.value = _state.value.copy(
            workspace = _state.value.workspace.map { if (it.id == id) it.copy(done = next) else it },
        )
        viewModelScope.launch {
            runCatching { rooms.updateWorkspaceItem(id, next) }.onFailure {
                _state.value = _state.value.copy(
                    workspace = _state.value.workspace.map { if (it.id == id) it.copy(done = current.done) else it },
                )
            }
        }
    }

    fun deleteWorkspaceItem(id: String) {
        val removed = _state.value.workspace.firstOrNull { it.id == id }
        _state.value = _state.value.copy(workspace = _state.value.workspace.filterNot { it.id == id })
        viewModelScope.launch {
            runCatching { rooms.deleteWorkspaceItem(id) }.onFailure {
                // Restore on failure so a lost delete doesn't silently vanish.
                removed?.let { _state.value = _state.value.copy(workspace = (_state.value.workspace + it).sortedBy { w -> w.position }) }
            }
        }
    }

    private companion object {
        const val BREAK_FEED_CAP = 30
        const val EVENT_CAP = 30
        const val HEARTBEAT_MS = 15_000L
    }
}
