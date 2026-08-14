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
    /** A device signal that isn't guarding the stack — surfaced as a warning. */
    val sensorWarning: String? = null,
) {
    val me: ParticipantRow? get() = participants.firstOrNull { it.userId == meId }
    val isHost: Boolean get() = room != null && meId != null && room.hostId == meId
    val iBreached: Boolean get() = me?.breached == true
    val code: String get() = room?.code.orEmpty()
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
            val room = runCatching { rooms.claimRoomSeat(code) }.getOrNull()
            if (room == null) {
                _state.value = _state.value.copy(
                    phase = RoomPhase.ERROR,
                    error = "Room not found, or it has already ended.",
                )
                return@launch
            }

            val participants = runCatching { rooms.listParticipants(room.id) }.getOrDefault(emptyList())
            val breaks = runCatching { rooms.listBreaks(room.id) }.getOrDefault(emptyList())

            _state.value = _state.value.copy(
                phase = phaseFor(room),
                room = room,
                participants = participants,
                breaks = breaks,
                meId = userId,
                error = null,
            )
            subscribe(room.id)
            startTicker()
            reconcile() // one immediate compute so the timer isn't blank for a second
        }
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

        viewModelScope.launch { runCatching { ch.channel.subscribe() } }
    }

    private fun applyRoom(next: RoomRow) {
        val prev = _state.value.room
        // Stale-row rejection: a replayed older row on reconnect would flip an
        // active room back to lobby and reset the timer. `updated_at` is ISO-8601
        // and sorts lexically, so a string compare orders events correctly —
        // exactly the web's guard.
        if (prev?.updatedAt != null && next.updatedAt != null && next.updatedAt < prev.updatedAt) return

        _state.value = _state.value.copy(room = next, phase = phaseFor(next, _state.value))
        if (next.statusEnum == RoomStatus.ACTIVE) armIfNeeded()
        if (next.statusEnum == RoomStatus.COMPLETE || next.statusEnum == RoomStatus.ABORTED) {
            _state.value = _state.value.copy(armed = false)
            maybeFinalize()
        }
        tick()
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
            runCatching { rooms.abortSession(room.id, nowIso()) }
        }
    }

    private fun completeSession() {
        val room = _state.value.room ?: return
        if (completionLock) return
        completionLock = true
        viewModelScope.launch {
            // ended_at is the host client's clock, matching the web — the finalize
            // split depends on this being the same source on both platforms.
            runCatching { rooms.completeSession(room.id, nowIso()) }
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
            )

            val submitted = runCatching {
                rooms.finalizeSession(
                    roomId = room.id,
                    score = result.score,
                    xp = result.xp,
                    durationSeconds = result.focusSecondsInt,
                    breachesCount = myBreachCount,
                    tier = result.tier.key,
                    scoringVersion = result.scoringVersion,
                ) != null
            }.getOrDefault(false)

            if (submitted) {
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

    override fun onCleared() {
        super.onCleared()
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

    private companion object {
        const val BREAK_FEED_CAP = 30
    }
}
