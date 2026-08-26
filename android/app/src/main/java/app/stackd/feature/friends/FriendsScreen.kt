package app.stackd.feature.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.SectionLabel
import app.stackd.data.social.Friend
import app.stackd.data.social.PersonRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class FriendsUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val rows: List<Friend> = emptyList(),
    val searchResults: List<PersonRef> = emptyList(),
    val searching: Boolean = false,
    /** user_ids a request was just sent to, for instant button feedback. */
    val requested: Set<String> = emptySet(),
) {
    val friends: List<Friend> get() = rows.filter { it.direction == "friend" }
    val incoming: List<Friend> get() = rows.filter { it.direction == "incoming" }
    val outgoing: List<Friend> get() = rows.filter { it.direction == "outgoing" }
}

/** Friends — web's `friends.tsx`: list, requests both ways, people search. */
class FriendsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(FriendsUiState())
    val state: StateFlow<FriendsUiState> = _state

    init {
        load()
    }

    fun load() {
        val userId = container.auth.currentUserId ?: return
        _state.value = _state.value.copy(loading = true, error = false)
        viewModelScope.launch {
            runCatching { container.friends.listFriends(userId) }.fold(
                onSuccess = { _state.value = _state.value.copy(loading = false, rows = it) },
                onFailure = { _state.value = _state.value.copy(loading = false, error = true) },
            )
        }
    }

    fun search(q: String) {
        val userId = container.auth.currentUserId ?: return
        if (q.isBlank()) {
            _state.value = _state.value.copy(searchResults = emptyList())
            return
        }
        _state.value = _state.value.copy(searching = true)
        viewModelScope.launch {
            val rows = runCatching { container.friends.searchPeople(userId, q) }
                .getOrDefault(emptyList())
            _state.value = _state.value.copy(searching = false, searchResults = rows)
        }
    }

    fun sendRequest(addresseeId: String) {
        val userId = container.auth.currentUserId ?: return
        _state.value = _state.value.copy(requested = _state.value.requested + addresseeId)
        viewModelScope.launch {
            runCatching { container.friends.sendRequest(userId, addresseeId) }
            load()
        }
    }

    fun respond(id: String, accept: Boolean) {
        val userId = container.auth.currentUserId ?: return
        _state.value = _state.value.copy(rows = _state.value.rows.filterNot { it.id == id && !accept })
        viewModelScope.launch {
            runCatching { container.friends.respond(id, userId, accept) }
            load()
        }
    }

    fun remove(id: String) {
        _state.value = _state.value.copy(rows = _state.value.rows.filterNot { it.id == id })
        viewModelScope.launch { runCatching { container.friends.remove(id) } }
    }
}

@Composable
fun FriendsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: FriendsViewModel = viewModel(factory = stackdViewModel { FriendsViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    FriendsScreen(
        state = state,
        onSearch = vm::search,
        onSendRequest = vm::sendRequest,
        onRespond = vm::respond,
        onRemove = vm::remove,
        onRetry = vm::load,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun FriendsScreen(
    state: FriendsUiState,
    onSearch: (String) -> Unit,
    onSendRequest: (String) -> Unit,
    onRespond: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
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
            Text("STACK'D / FRIENDS", style = MonoLabel, color = colors.textMuted)
            Spacer(Modifier.height(16.dp))
            SectionLabel("YOUR PEOPLE")
            Spacer(Modifier.height(16.dp))

            var query by remember { mutableStateOf("") }
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it.take(60)
                    onSearch(query)
                },
                label = { Text("Find people by name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.searchResults.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                state.searchResults.forEach { p ->
                    PersonRow(
                        name = p.displayName?.takeIf { it.isNotBlank() } ?: "Anon",
                        sub = null,
                        actionA = if (p.id in state.requested) "SENT" else "ADD",
                        onA = if (p.id in state.requested) null else ({ onSendRequest(p.id) }),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            when {
                state.loading -> Text(
                    "Loading…",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                )
                state.error -> {
                    Text(
                        "Couldn't load friends.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Retry", onClick = onRetry)
                }
                else -> {
                    if (state.incoming.isNotEmpty()) {
                        Text("REQUESTS", style = MonoLabelSmall, color = colors.accent)
                        Spacer(Modifier.height(6.dp))
                        state.incoming.forEach { f ->
                            PersonRow(
                                name = f.displayName ?: "Anon",
                                sub = "wants to connect",
                                actionA = "ACCEPT", onA = { onRespond(f.id, true) },
                                actionB = "DECLINE", onB = { onRespond(f.id, false) },
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    if (state.outgoing.isNotEmpty()) {
                        Text("SENT", style = MonoLabelSmall, color = colors.textMuted)
                        Spacer(Modifier.height(6.dp))
                        state.outgoing.forEach { f ->
                            PersonRow(
                                name = f.displayName ?: "Anon",
                                sub = "pending",
                                actionA = "CANCEL", onA = { onRemove(f.id) },
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    Text("FRIENDS · ${state.friends.size}", style = MonoLabelSmall, color = colors.textMuted)
                    Spacer(Modifier.height(6.dp))
                    if (state.friends.isEmpty()) {
                        Text(
                            "No friends yet — search above to send a request.",
                            style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                        )
                    }
                    state.friends.forEach { f ->
                        PersonRow(
                            name = f.displayName ?: "Anon",
                            sub = "since ${f.since.take(10)}",
                            actionA = "REMOVE", onA = { onRemove(f.id) },
                        )
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
private fun PersonRow(
    name: String,
    sub: String?,
    actionA: String? = null,
    onA: (() -> Unit)? = null,
    actionB: String? = null,
    onB: (() -> Unit)? = null,
) {
    val colors = Stackd.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(colors.textPrimary.copy(alpha = 0.02f), Radius2Xl)
            .border(1.dp, colors.border, Radius2Xl)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            sub?.let { Text(it, style = MonoLabelSmall, color = colors.textMuted) }
        }
        actionA?.let { label ->
            Text(
                label,
                style = MonoLabelSmall,
                color = if (onA != null) colors.accent else colors.textMuted,
                modifier = (if (onA != null) Modifier.clickable { onA() } else Modifier)
                    .padding(start = 10.dp),
            )
        }
        actionB?.let { label ->
            Text(
                label,
                style = MonoLabelSmall,
                color = colors.textMuted,
                modifier = (if (onB != null) Modifier.clickable { onB() } else Modifier)
                    .padding(start = 14.dp),
            )
        }
    }
}
