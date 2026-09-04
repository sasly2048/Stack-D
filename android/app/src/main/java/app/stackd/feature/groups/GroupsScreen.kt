package app.stackd.feature.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stackd.core.AppContainer
import app.stackd.core.stackdViewModel
import app.stackd.core.theme.MonoLabel
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.Radius2Xl
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.SectionLabel
import app.stackd.data.social.BoardEntry
import app.stackd.data.social.GroupSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class GroupsUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val meId: String? = null,
    val groups: List<GroupSummary> = emptyList(),
    val circleBoard: List<BoardEntry> = emptyList(),
    val personalBoard: List<BoardEntry> = emptyList(),
    val creating: Boolean = false,
    /** groupId currently mid join/leave, so only that row's buttons disable. */
    val rowBusy: String? = null,
    /** groupId whose sprint is dispatching. */
    val sprintBusy: String? = null,
    /** Transient banner: sprint dispatched, rate-limited, or failed. */
    val notice: String? = null,
    val expandedGroup: String? = null,
)

private const val SPRINT_MINUTES = 30

/**
 * Focus circles — web's `groups.tsx`.
 *
 * The web's per-group dispatch cooldown UI (a shrinking progress bar) is not
 * ported: the server is the authority on the rate limit and simply reports it
 * as a notice. Rebuilding the visual countdown would duplicate state the RPC
 * already owns for no behavioural gain.
 */
class GroupsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(GroupsUiState())
    val state: StateFlow<GroupsUiState> = _state

    init {
        load()
    }

    private fun cacheKey(userId: String) = "groups:$userId"

    fun load() {
        val userId = container.auth.currentUserId ?: return
        // Stale-while-revalidate: seed from the last cached state so re-entry
        // shows data instantly instead of a spinner, then revalidate below.
        val cached: GroupsUiState? = container.cache.get(cacheKey(userId))
        _state.value = (cached ?: _state.value).copy(loading = cached == null, error = false)
        viewModelScope.launch {
            runCatching { container.groups.snapshot(userId) }.fold(
                onSuccess = { snap ->
                    val fresh = _state.value.copy(
                        loading = false,
                        error = false,
                        meId = snap.meId,
                        groups = snap.groups,
                        circleBoard = snap.circleBoard,
                        personalBoard = snap.personalBoard,
                    )
                    _state.value = fresh
                    container.cache.put(cacheKey(userId), fresh)
                },
                onFailure = { _state.value = _state.value.copy(loading = false, error = cached == null) },
            )
        }
    }

    fun create(name: String) {
        val userId = container.auth.currentUserId ?: return
        if (name.isBlank() || _state.value.creating) return
        _state.value = _state.value.copy(creating = true)
        viewModelScope.launch {
            runCatching { container.groups.createGroup(name, userId) }
                .onFailure { _state.value = _state.value.copy(notice = "Could not create circle.") }
            _state.value = _state.value.copy(creating = false)
            load()
        }
    }

    fun join(groupId: String) = mutate(groupId) { u -> container.groups.joinGroup(groupId, u) }
    fun leave(groupId: String) = mutate(groupId) { u -> container.groups.leaveGroup(groupId, u) }

    private fun mutate(groupId: String, block: suspend (String) -> Unit) {
        val userId = container.auth.currentUserId ?: return
        if (_state.value.rowBusy != null) return
        _state.value = _state.value.copy(rowBusy = groupId)
        viewModelScope.launch {
            runCatching { block(userId) }
                .onFailure { _state.value = _state.value.copy(notice = "That didn't work. Try again.") }
            _state.value = _state.value.copy(rowBusy = null)
            load()
        }
    }

    fun toggleExpanded(groupId: String) {
        _state.value = _state.value.copy(
            expandedGroup = if (_state.value.expandedGroup == groupId) null else groupId,
        )
    }

    fun clearNotice() {
        _state.value = _state.value.copy(notice = null)
    }

    /**
     * Creates a 30-minute lobby, then fans it out to the circle. If dispatch is
     * rate-limited the just-created room is rolled back so no orphan lobby is
     * left behind — the web does the same delete on the rate-limit path.
     */
    fun startSprint(group: GroupSummary, onEnterRoom: (String) -> Unit) {
        val userId = container.auth.currentUserId ?: return
        if (_state.value.sprintBusy != null) return
        _state.value = _state.value.copy(sprintBusy = group.id, notice = null)
        viewModelScope.launch {
            val name = container.profiles.displayNameFor(userId, container.auth.currentEmail)
            val created = container.rooms.createRoom(
                targetDurationSeconds = SPRINT_MINUTES * 60L,
                hostId = userId,
                hostDisplayName = name,
            )
            val code = created.getOrNull()
            if (code == null) {
                _state.value = _state.value.copy(sprintBusy = null, notice = "Sprint failed.")
                return@launch
            }
            val roomId = container.rooms.roomIdForCode(code)
            val expiresAt = System.currentTimeMillis() + 5 * 60_000
            val dispatch = runCatching {
                container.groups.dispatchSprint(group.id, roomId ?: "", code, expiresAt)
            }
            _state.value = _state.value.copy(sprintBusy = null)
            dispatch.fold(
                onSuccess = {
                    val others = (group.memberCount - 1).coerceAtLeast(0)
                    _state.value = _state.value.copy(
                        notice = "Sprint dispatched to $others member${if (others == 1) "" else "s"}.",
                    )
                    onEnterRoom(code)
                },
                onFailure = { err ->
                    if (roomId != null &&
                        err.message?.contains("rate_limited", ignoreCase = true) == true
                    ) {
                        // Roll back so a throttled dispatch doesn't leak a lobby.
                        runCatching { container.rooms.deleteRoom(roomId) }
                        _state.value = _state.value.copy(
                            notice = "Too many sprint invites just now. Try again in ~60s.",
                        )
                    } else {
                        // Dispatch failed for another reason, but the room is
                        // real; drop the host into it rather than stranding it.
                        _state.value = _state.value.copy(notice = "Couldn't notify the circle.")
                        onEnterRoom(code)
                    }
                },
            )
        }
    }
}

@Composable
fun GroupsRoute(
    onBack: () -> Unit,
    onOpenRoom: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: GroupsViewModel = viewModel(factory = stackdViewModel { GroupsViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    GroupsScreen(
        state = state,
        onCreate = vm::create,
        onJoin = vm::join,
        onLeave = vm::leave,
        onToggle = vm::toggleExpanded,
        onSprint = { g -> vm.startSprint(g, onOpenRoom) },
        onRetry = vm::load,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun GroupsScreen(
    state: GroupsUiState,
    onCreate: (String) -> Unit,
    onJoin: (String) -> Unit,
    onLeave: (String) -> Unit,
    onToggle: (String) -> Unit,
    onSprint: (GroupSummary) -> Unit,
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
            Text("CIRCLES / LEADERBOARDS", style = MonoLabel, color = colors.textMuted)
            Spacer(Modifier.height(16.dp))
            SectionLabel("FOCUS CIRCLES")
            Spacer(Modifier.height(16.dp))

            var name by remember { mutableStateOf("") }
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 80) name = it },
                label = { Text("Circle name (e.g. Dev Team)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            EmberButton(
                text = if (state.creating) "Forging…" else "Forge circle",
                onClick = { onCreate(name); name = "" },
                enabled = name.isNotBlank() && !state.creating,
                busy = state.creating,
            )

            state.notice?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MonoLabelSmall, color = colors.accent)
            }

            Spacer(Modifier.height(24.dp))
            when {
                state.loading -> Text(
                    "Loading circles…",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                )
                state.error -> {
                    Text(
                        "Couldn't load circles.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Retry", onClick = onRetry)
                }
                else -> {
                    Text(
                        "ALL CIRCLES · ${state.groups.size}",
                        style = MonoLabelSmall, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (state.groups.isEmpty()) {
                        Text(
                            "None yet. Forge the first circle above.",
                            style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                        )
                    }
                    state.groups.forEach { g ->
                        GroupCard(
                            g = g,
                            expanded = state.expandedGroup == g.id,
                            rowBusy = state.rowBusy == g.id,
                            sprintBusy = state.sprintBusy == g.id,
                            onToggle = { onToggle(g.id) },
                            onJoin = { onJoin(g.id) },
                            onLeave = { onLeave(g.id) },
                            onSprint = { onSprint(g) },
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                    Board("CIRCLE LEADERBOARD · AVG XP", state.circleBoard)
                    Spacer(Modifier.height(20.dp))
                    Board("PERSONAL LEADERBOARD · LIFETIME XP", state.personalBoard)
                }
            }

            Spacer(Modifier.height(24.dp))
            GhostButton(text = "Back", onClick = onBack)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun GroupCard(
    g: GroupSummary,
    expanded: Boolean,
    rowBusy: Boolean,
    sprintBusy: Boolean,
    onToggle: () -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onSprint: () -> Unit,
) {
    val colors = Stackd.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(colors.textPrimary.copy(alpha = 0.02f), Radius2Xl)
            .border(1.dp, colors.border, Radius2Xl)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onToggle() },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        g.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (g.isOwner) Tag("OWNER", colors.accent)
                    else if (g.isMember) Tag("JOINED", colors.live)
                }
                Text(
                    "${g.memberCount} member${if (g.memberCount == 1) "" else "s"} · ${g.totalGroupXp} XP",
                    style = MonoLabelSmall, color = colors.textMuted,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (g.isMember) {
                EmberButton(
                    text = if (sprintBusy) "Dispatching…" else "Start Sprint",
                    onClick = onSprint,
                    enabled = !sprintBusy,
                    busy = sprintBusy,
                    modifier = Modifier.weight(1f),
                )
                if (!g.isOwner) {
                    GhostButton(
                        text = "Leave",
                        onClick = onLeave,
                        enabled = !rowBusy,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                GhostButton(
                    text = "Join",
                    onClick = onJoin,
                    enabled = !rowBusy,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (expanded && g.members.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            g.members.forEach { m ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        m.displayName?.takeIf { it.isNotBlank() } ?: "—",
                        style = MonoLabelSmall, color = colors.textPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${m.lifetimeXp} XP", style = MonoLabelSmall, color = colors.textMuted)
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        style = MonoLabelSmall,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), app.stackd.core.theme.RadiusMd)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun Board(heading: String, rows: List<BoardEntry>) {
    val colors = Stackd.colors
    Text(heading, style = MonoLabelSmall, color = colors.textMuted)
    Spacer(Modifier.height(6.dp))
    if (rows.isEmpty()) {
        Text(
            "Nothing here yet.",
            style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
        )
        return
    }
    rows.forEachIndexed { i, r ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                Text(
                    (i + 1).toString().padStart(2, '0'),
                    style = MonoLabelSmall, color = colors.textMuted,
                )
                Text(
                    r.name,
                    style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text("${r.value}", style = MonoLabelSmall, color = colors.textPrimary)
        }
    }
}
